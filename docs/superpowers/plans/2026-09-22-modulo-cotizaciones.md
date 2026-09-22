# Módulo de Cotizaciones — Plan de trabajo

## Contexto

`docs/modulo-cotizacion.md` especifica un módulo de Cotizaciones completo: es el
primer eslabón de la futura trazabilidad comercial del ERP
(Cotización → Nota de Venta → Guía → Factura → Pago). Hoy **no existe nada** de
cotizaciones en el repositorio (verificado: el único match de "cotizac" es el
propio documento).

El módulo más parecido que ya existe es **Ventas** (`cl.slimerp.ventas`), del que
se reutilizan tres piezas clave en vez de reinventarlas:

- **Folio correlativo atómico**: `FolioVentaContadorRepository` usa un UPSERT
  nativo (`INSERT … ON CONFLICT … DO UPDATE SET ultimo_folio = ultimo_folio + 1
  RETURNING ultimo_folio`) — sin `max()+1` ni race conditions. Se replica para
  el número de cotización.
- **Cálculo de IVA**: `cl.slimerp.ventas.CalculadoraMontosVenta` (TASA_IVA 0.19,
  garantiza `neto + iva == total`).
- **PDF**: `cl.slimerp.ventas.VentaPdfService` genera el PDF con OpenPDF 1.3.30
  (`com.lowagie.text.*`), y el frontend lo muestra en
  `features/ventas/venta-pdf-dialog.component.ts` dentro de un `MatDialog`.

**No existe** ningún patrón de historial de eventos por entidad dentro del tenant
(no hay tablas `*_historial`/`*_evento`; `admin.audit_log` vive en el esquema
`admin` y pertenece al `admin-backend`, fuera de alcance). El historial de la
cotización es tabla nueva, modelada sobre `movimiento_inventario_header`
(tenant + usuario + fecha + tipo enum) más `estado_anterior`/`estado_nuevo`.

### Decisiones tomadas con el usuario

1. **Plan en 2 fases.** Fase 1 = núcleo funcional; Fase 2 = dashboard, Libro de
   Cotizaciones y sección de trazabilidad. Se puede revisar la Fase 1 antes de
   entrar a la 2.
2. **Aceptar solo cambia el estado.** No se genera ninguna Venta ni se toca
   stock/tesorería. "Documentos relacionados" se implementa en Fase 2 como el
   punto de extensión que exigen las secciones 7 y 12 del documento, y arranca
   vacío a propósito.
3. **El vendedor es el usuario autenticado** que crea la cotización (resuelto en
   el backend desde el token). No editable.
4. **Montos**: precios de línea netos, descuento por línea + descuento global,
   IVA 19% sobre el neto, más un flag `exenta` (IVA 0). Todo CLP, sin campo
   moneda.

## Global Constraints

- `id` autoincremental generado por la base de datos; el frontend nunca lo envía
  (regla del proyecto, `CLAUDE.md`).
- Multi-tenant: toda tabla lleva `tenant_id BIGINT NOT NULL REFERENCES tenant(id)`
  y toda consulta filtra por `TenantContext.getTenantId()`.
- Entidades al estilo del repo: Lombok (`@Getter @Setter @NoArgsConstructor
  @AllArgsConstructor @Builder`), **sin asociaciones JPA** salvo la cabecera↔detalle
  (el resto son `Long xxxId` denormalizados), enums como `@Enumerated(EnumType.STRING)`
  con `@Column(length = 20)`, montos `BigDecimal(precision = 14, scale = 2)`.
- **Errores de negocio (incluidas transiciones de estado inválidas) se lanzan como
  `IllegalArgumentException`** → 400 vía `GlobalExceptionHandler`. Nunca
  `IllegalStateException`: el handler la mapea a **401**.
- Permisos nuevos `COTIZACIONES_VER` / `COTIZACIONES_EDITAR`: **no requieren
  migración** (`usuario_permiso.permiso` es `VARCHAR(40)` libre). Sí requieren
  tocar `Permiso.java`, `RolPermisos.java`, el union type `Permiso` de
  `frontend/src/app/core/models/models.ts` y `GRUPOS_PERMISOS` en
  `features/usuarios/roles-permisos.component.ts`. **`RolPermisosTest` fallará a
  propósito** y hay que actualizarlo.
- Formularios/pantallas con HTML nativo (`.form-group`, `input`/`select`);
  Material solo para botones, cards, tablas, iconos, diálogos y paginador. Nunca
  `<mat-form-field>`.
- Reutilizar clases globales de `frontend/src/styles/_components.scss`
  (`.page-header`, `.form-panel`, `.form-grid`, `.tag--success/--warning/--error/--info`,
  `.empty-state`, `.page-error`) y **duplicar en el `.scss` del componente** las
  clases component-scoped del repo (`.kpi-grid`, `.kpi-card`, `.historial-table`,
  `.filtros-grid`, `.items-table`, `.totals-group`, `.two-col`) — el repo no usa
  partials SCSS compartidos para eso.
- Comandos de test verificados en este proyecto:
  - Backend (requiere Docker, no hay `mvn` local):
    ```bash
    cd backend
    MSYS_NO_PATHCONV=1 docker run --rm -v "//c/Users/ivana/Documents/slime-erp/backend://app" -v slime-erp-maven-repo:/root/.m2 -w //app maven:3.9-eclipse-temurin-21 mvn -q -B -Dtest=<Clase> test
    ```
  - Frontend:
    ```bash
    cd frontend
    CHROME_BIN="C:\Program Files\Google\Chrome\Application\chrome.exe" npx ng test --watch=false --include='**/<archivo>.spec.ts'
    ```
- No ejecutar comandos de git durante la implementación; el usuario revisa y
  comitea cuando lo decida.

---

## Tarea 0 — Guardar el plan en el repo

Copiar este plan a `docs/superpowers/plans/2026-09-22-modulo-cotizaciones.md`,
siguiendo la convención del repositorio (`prompt_modulo_inventario.md` →
`docs/superpowers/plans/2026-09-09-modulo-inventario.md`).

---

# FASE 1 — Núcleo funcional

## 1. Migración y modelo de datos

**Archivos:** `backend/src/main/resources/db/migration/V34__cotizaciones.sql`
(la última migración existente es `V33`), y el paquete nuevo
`backend/src/main/java/cl/slimerp/cotizaciones/`.

**`cotizacion`** — `id`, `tenant_id`, `folio INTEGER NOT NULL`,
`cliente_id → cliente(id)`, `vendedor_id → usuario(id)`,
`forma_pago_id → forma_pago(id)` (nullable),
`estado VARCHAR(20) NOT NULL DEFAULT 'BORRADOR'`,
`exenta BOOLEAN NOT NULL DEFAULT false`,
`fecha_emision DATE NOT NULL`, `fecha_vencimiento DATE NOT NULL`,
`descuento NUMERIC(14,2) NOT NULL DEFAULT 0` (global),
`monto_subtotal`, `monto_descuento`, `monto_neto`, `monto_iva`, `monto_total`
(todos `NUMERIC(14,2) NOT NULL DEFAULT 0` — el documento pide el bloque de
totales de 5 líneas, así que se persisten los cinco),
`condiciones_comerciales VARCHAR(500)`, `observaciones VARCHAR(500)`,
`motivo VARCHAR(500)` (motivo de rechazo/cancelación),
`fecha_creacion TIMESTAMP NOT NULL DEFAULT now()`, `fecha_actualizacion TIMESTAMP`.
Constraints: `CONSTRAINT uq_cotizacion_tenant_folio UNIQUE (tenant_id, folio)`;
índices `idx_cotizacion_tenant_estado(tenant_id, estado)`,
`idx_cotizacion_tenant_fecha(tenant_id, fecha_emision)`,
`idx_cotizacion_cliente(cliente_id)`.

**`cotizacion_detalle`** — `id`, `cotizacion_id → cotizacion(id) ON DELETE CASCADE`,
`producto_id → producto(id)`, `codigo VARCHAR(50)`, `descripcion VARCHAR(255)`
(código y descripción son **snapshot** del producto al guardar: la cotización es
un documento histórico y el catálogo cambia), `cantidad`, `precio_unitario`,
`descuento`, `subtotal` (todos `NUMERIC(14,2)`).

**`cotizacion_evento`** (historial append-only) — `id`, `tenant_id`,
`cotizacion_id → cotizacion(id) ON DELETE CASCADE`, `usuario_id → usuario(id)`
(nullable: el job de vencimiento no tiene usuario), `accion VARCHAR(30) NOT NULL`,
`estado_anterior VARCHAR(20)`, `estado_nuevo VARCHAR(20)`, `detalle VARCHAR(500)`,
`fecha TIMESTAMP NOT NULL DEFAULT now()`; índice
`idx_cotizacion_evento(cotizacion_id, fecha)`.

**`cotizacion_folio_contador`** — `id`, `tenant_id`, `ultimo_folio INTEGER NOT NULL
DEFAULT 0`, `CONSTRAINT uq_cotizacion_folio_tenant UNIQUE (tenant_id)`.

Entidades JPA espejo: `Cotizacion`, `CotizacionDetalle`, `CotizacionEvento`,
`CotizacionFolioContador`, más los enums `EstadoCotizacion {BORRADOR, ENVIADA,
ACEPTADA, RECHAZADA, VENCIDA, CANCELADA}` y `AccionCotizacion {CREADA, EDITADA,
ENVIADA, ACEPTADA, RECHAZADA, CANCELADA, VENCIDA, DUPLICADA}`.
`CotizacionDetalle.cotizacion` lleva `@JsonIgnore` (igual que `VentaDetalle`).

## 2. Folio correlativo

**Archivos:** `CotizacionFolioContadorRepository.java`, `CotizacionFolioService.java`,
`NumeroCotizacion.java`.

Copiar literalmente el UPSERT nativo de
`backend/src/main/java/cl/slimerp/ventas/FolioVentaContadorRepository.java`
(adaptado: sin columna `clave`, una sola serie por tenant) y su wrapper
`FolioVentaService`. `NumeroCotizacion.formatear(int folio)` → `"COT-%06d"`; el
número formateado lo calcula el backend y viaja en el DTO para que pantalla, PDF
y Excel muestren exactamente lo mismo.

El folio se asigna **al crear** (incluso en BORRADOR) y nunca se reutiliza:
borrar un borrador deja un hueco, que es el comportamiento correcto según la
sección 12 del documento.

## 3. Servicio, estados y validaciones

**Archivos:** `CotizacionService.java`, `CotizacionRepository.java`
(extiende `JpaRepository` + `JpaSpecificationExecutor`), `CotizacionEventoRepository.java`,
`CotizacionRequest.java`, `MotivoRequest.java`,
`backend/src/main/java/cl/slimerp/common/UsuarioActualService.java` (nuevo).

`UsuarioActualService.idUsuarioActual(Long tenantId)` extrae el email del
`SecurityContextHolder` y lo resuelve con `usuarioRepository.findByEmailAndTenantId`.
Hoy ese bloque está duplicado en `CuentaPorCobrarController` y
`MovimientoInventarioController`; **no** se tocan esos controllers (fuera de
alcance), pero el helper queda disponible para migrarlos después.

Métodos de `CotizacionService` (todos `@Transactional` los de escritura), cada
uno registrando su evento en `cotizacion_evento`:

| método | transición | notas |
|---|---|---|
| `crear(req)` | → BORRADOR | asigna folio, vendedor = usuario actual, calcula montos |
| `actualizar(id, req)` | BORRADOR → BORRADOR | reemplaza las líneas completas; evento EDITADA |
| `eliminar(id)` | BORRADOR → (borrado físico) | único estado borrable |
| `enviar(id)` | BORRADOR → ENVIADA | |
| `aceptar(id)` | ENVIADA → ACEPTADA | |
| `rechazar(id, motivo)` | ENVIADA → RECHAZADA | motivo opcional |
| `cancelar(id, motivo)` | BORRADOR\|ENVIADA → CANCELADA | estado final |
| `duplicar(id)` | cualquiera → nueva en BORRADOR | folio nuevo; evento DUPLICADA en ambas |
| `obtener(id)` / `listar(filtros, orden, página)` | — | lectura |

Cualquier otra transición lanza `IllegalArgumentException` con mensaje explícito
(p. ej. `"No se puede enviar una cotización en estado ACEPTADA"`), siguiendo los
if-guards imperativos de `CuentaPorPagarService.anular` /
`TransaccionPagoService.registrarPago`.

**Cálculo de montos:** reutilizar
`CalculadoraMontosVenta.calcular(TipoDocumentoVenta.FACTURA, exenta, sumaDetalle)`
— la rama `FACTURA` afecta es exactamente "la suma de detalle es neta, súmale el
IVA", y la exenta devuelve IVA 0. Se pasa `FACTURA` como modo de cálculo (dejar
el comentario explicándolo) para no duplicar la lógica de IVA ni arriesgar que
`neto + iva == total` deje de cumplirse. `monto_subtotal` = Σ(precio × cantidad)
y `monto_descuento` = Σ(descuentos de línea) + descuento global se calculan en el
servicio.

**Validaciones** (sección 12 del documento): cliente obligatorio y existente,
al menos una línea, cantidad > 0, precio ≥ 0, descuento de línea entre 0 y el
subtotal bruto de la línea, descuento global entre 0 y la suma de líneas,
`fecha_vencimiento >= fecha_emision`.

**Listado con filtros** vía `Specification` armada condicionalmente (nunca
`(:param IS NULL OR …)` en JPQL — ver el comentario en
`TransaccionPagoService:134`): `estado`, `clienteId`, `vendedorId`, `desde`,
`hasta`, y `q` (número, nombre o RUT del cliente). Orden por `fecha_emision`,
`folio` o `monto_total`, asc/desc. Respuesta `PaginaResponse<T>`
(`cl.slimerp.common.PaginaResponse`).

## 4. Controller y PDF

**Archivos:** `CotizacionController.java`, `CotizacionPdfService.java`.

Base `/api/cotizaciones`, `@PreAuthorize("hasAuthority('COTIZACIONES_VER')")` en
lectura y `COTIZACIONES_EDITAR` en escritura:

```
GET    /api/cotizaciones?estado&clienteId&vendedorId&desde&hasta&q&sort&dir&pagina&tamano
GET    /api/cotizaciones/{id}          → cabecera + cliente + líneas + eventos
POST   /api/cotizaciones               → crear borrador
PUT    /api/cotizaciones/{id}          → editar borrador
DELETE /api/cotizaciones/{id}          → eliminar borrador
POST   /api/cotizaciones/{id}/enviar|aceptar|rechazar|cancelar|duplicar
GET    /api/cotizaciones/{id}/pdf      → application/pdf inline
```

DTOs como `record` dentro del servicio (convención del repo): `CotizacionResumen`
para el listado (número, fecha, cliente, vendedor, total, vencimiento, estado) y
`CotizacionDetalleResponse` para el detalle (incluye datos del cliente —razón
social, RUT, dirección, correo, teléfono—, líneas, bloque de totales y eventos).

`CotizacionPdfService` clona la anatomía de `VentaPdfService` (OpenPDF: `Document`,
`PdfWriter`, `PdfPTable`, helpers `agregarCelda`/`totalAlineado`, montos con
`NumberFormat.getIntegerInstance(new Locale("es","CL"))`), con encabezado
`COT-000123`, datos de cliente y vendedor, vigencia, condiciones comerciales y el
bloque Subtotal / Descuento / Neto / IVA / Total.

## 5. Job de vencimiento

**Archivo:** `CotizacionVencidaJob.java`.

Réplica de `cl.slimerp.gastos.GastoRecurrenteGeneratorJob`: `@Component`,
`@Scheduled(cron = "0 15 3 * * *")` (`@EnableScheduling` ya está activo en
`SlimErpApplication`), itera `tenantRepository.findByActivoTrue()` con
`TenantContext.setTenantId(...)` dentro de try/catch/finally por tenant, y delega
en `int marcarVencidasParaTenant(Long tenantId, LocalDate hoy)` **package-private
y con la fecha por parámetro** para poder testearlo sin cron. Marca VENCIDA toda
cotización ENVIADA con `fecha_vencimiento < hoy` y registra el evento con
`usuario_id = null`.

## 6. Permisos

**Archivos:** `permisos/Permiso.java`, `permisos/RolPermisos.java`,
`backend/src/test/java/cl/slimerp/permisos/RolPermisosTest.java`,
`frontend/src/app/core/models/models.ts`,
`frontend/src/app/features/usuarios/roles-permisos.component.ts`.

Agregar `COTIZACIONES_VER` / `COTIZACIONES_EDITAR`: ver+editar para ADMIN y
VENDEDOR, solo ver para COMPRADOR y VISUALIZADOR (SUPER_ADMIN los hereda por
`EnumSet.allOf`). Actualizar `RolPermisosTest` y agregar el grupo "Cotizaciones"
a `GRUPOS_PERMISOS`.

## 7. Frontend — modelos, servicio y listado

**Archivos:** `frontend/src/app/core/models/models.ts`,
`frontend/src/app/core/services/cotizacion.service.ts`,
`frontend/src/app/features/cotizaciones/cotizaciones.component.{ts,html,scss}`.

Interfaces espejo (`EstadoCotizacion`, `CotizacionLinea`, `CotizacionResumen`,
`CotizacionDetalle`, `CotizacionEvento`, `CotizacionRequest`) y un servicio
`providedIn: 'root'` con un método por endpoint (`HttpParams` para los filtros,
`responseType: 'blob'` para el PDF).

El listado clona la anatomía de
`features/reportes/libro-ventas.component.*` (filtros en `mat-card.form-panel`,
buscador con `Subject` + `debounceTime(300)` + `distinctUntilChanged`,
`mat-paginator` server-side, `.table-scroll`) más el ordenamiento por columna de
`features/inventario/inventario.component.*` (`MatSortModule`, `matSort`,
`onSortChange` reseteando a página 0). Columnas: Número, Fecha, Cliente,
Vendedor, Total, Vencimiento, Estado (badge), Acciones. Filas `.clickable-row`
navegando al detalle.

**Badges**: dos `Record<EstadoCotizacion, string>` a nivel de módulo, igual que
`cuentas-por-pagar.component.ts:16-28` — BORRADOR `''` (neutro), ENVIADA
`tag--info`, ACEPTADA `tag--success`, RECHAZADA `tag--error`, VENCIDA
`tag--warning`, CANCELADA `''`.

## 8. Frontend — formulario crear/editar

**Archivos:** `features/cotizaciones/cotizacion-form.component.{ts,html,scss}`.

Clon adaptado de `features/ventas/ventas.component.*`: buscador de cliente
debounced (`clienteService.listarPagina`), buscador de productos con caché
`Map<number, Producto>`, staging de línea (`ItemStaged`, `confirmarStaged`,
`editarItem`, `quitarItem`) y validación centralizada que devuelve mensaje o
`null`. **Sin validación de stock** (una cotización no compromete inventario).

Secciones según `CLAUDE.md`: `.two-col` con "Cliente" y "Datos de la cotización"
(fecha de emisión, vencimiento, forma de pago, exenta, condiciones comerciales),
tabla `.items-table` de líneas, y panel de Totales con `.totals-group` mostrando
Subtotal / Descuento / Neto / IVA / **Total** y el botón principal. Los totales
son getters en el componente (espejo cliente del cálculo del backend, como en
ventas).

## 9. Frontend — vista detalle, acciones e historial

**Archivos:** `features/cotizaciones/cotizacion-detalle.component.{ts,html,scss}`,
`features/ventas/venta-pdf-dialog.component.ts` (cambio mínimo),
`frontend/src/app/app.routes.ts`, `frontend/src/app/layout/layout.component.ts`.

Estructura tomada de `cuenta-por-pagar-detalle.component.*`: `.page-header` con
`COT-000123 — Cliente` + botón Volver y badge de estado; `.two-col` con datos del
cliente (inputs readonly) y bloque de totales (`.totals-row--total`);
`.panel-header-row` con `.panel-actions` **contextuales según estado y permiso**
(Editar/Enviar/Eliminar en BORRADOR; Aceptar/Rechazar/Cancelar/Duplicar en
ENVIADA; Duplicar en ACEPTADA/RECHAZADA/VENCIDA/CANCELADA; Descargar PDF e
Imprimir siempre); tabla `.items-table` de líneas; y tabla `.historial-table` con
el historial de eventos (fecha, usuario, acción, estado anterior → nuevo).
Rechazar y cancelar piden motivo con un `.inline-form` embebido (patrón de
anulación de cuentas por pagar), no con diálogo.

**PDF**: reutilizar `venta-pdf-dialog.component.ts` agregando un `titulo`
opcional en `MAT_DIALOG_DATA` (hoy el título está hardcodeado como
"Comprobante — Venta #id"); mantiene sus dos llamadores actuales intactos y
evita duplicar el componente. "Imprimir" = el botón de impresión del visor PDF
del iframe.

**Rutas** (literales antes del `:id`, como `cuentas-por-pagar`):
`cotizaciones`, `cotizaciones/nueva`, `cotizaciones/:id`, `cotizaciones/:id/editar`.
**Menú**: ítem "Cotizaciones" (icono `description`, permiso `COTIZACIONES_VER`)
en el grupo `operacion` de `GRUPOS`, antes de Ventas.

## 10. Tests de Fase 1

- Backend (Mockito puro, sin contexto Spring, nombres en español):
  `CotizacionServiceTest` (cálculo de montos afecta/exenta con descuentos, folio
  correlativo, cada transición válida y **cada transición inválida lanzando
  `IllegalArgumentException`**, duplicar genera folio nuevo y eventos, borrar
  solo en BORRADOR, validaciones de fechas/cantidades/descuentos),
  `CotizacionVencidaJobTest` (ENVIADA vencida → VENCIDA; BORRADOR y ACEPTADA sin
  cambios; un tenant que falla no aborta el resto), `CotizacionPdfServiceTest`
  (el PDF se genera y no viene vacío), y `CotizacionControllerPermissionTest`
  (`@WebMvcTest` + `@TestConfiguration @EnableMethodSecurity`, siguiendo
  `reporteria/LibroVentasControllerPermissionTest`): permiso correcto → 200,
  permiso ajeno → 403.
- Frontend (Karma/Jasmine, instanciación directa con stubs): `cotizacion.service.spec.ts`
  (HttpTestingController), spec del listado (debounce con `fakeAsync`/`tick`,
  `onSortChange` resetea a página 0, `onPageChange` no) y spec del detalle
  (acciones disponibles por estado).

---

# FASE 2 — Dashboard, Libro y trazabilidad

## 11. Dashboard de cotizaciones (sobre el listado)

**Archivos:** `CotizacionDashboardService.java` (o método en `CotizacionService`),
endpoint `GET /api/cotizaciones/dashboard?desde=&hasta=` en `CotizacionController`
(protegido con `COTIZACIONES_VER`), y ampliación de
`features/cotizaciones/cotizaciones.component.*`.

Según la sección 9 del documento el dashboard va **encima del listado**, en la
misma pantalla (no una ruta aparte). Devuelve: cotizaciones del período,
pendientes (ENVIADA), aceptadas, rechazadas, monto cotizado, monto aceptado,
conteo por estado para el gráfico y tasa de conversión
(`aceptadas / enviadas × 100`, null si no hay enviadas).

UI: `.kpi-grid`/`.kpi-card` copiados de `dashboard.component.scss`, selector de
período con `.rango-btn`/`.rango-btn--active` (Este mes / Mes anterior / Últimos
3 meses / Este año / Personalizado → muestra los `input type="date"`), y gráfico
Highcharts `column` "Cotizaciones por estado" siguiendo el patrón de
`dashboard.component.ts` (colores desde CSS vars con `getComputedStyle`,
`credits/legend` deshabilitados, `[(update)]`, ocultar con `[style.display]` en
vez de `@if` para no destruir el chart).

## 12. Libro de Cotizaciones

**Archivos:** `backend/src/main/java/cl/slimerp/reporteria/LibroCotizacionesService.java`,
`LibroCotizacionesExcelService.java`, `LibroCotizacionesController.java`
(`GET /api/reportes/libro-cotizaciones` y `/excel`, permiso `COTIZACIONES_VER`),
`frontend/src/app/features/reportes/libro-cotizaciones.component.{ts,html,scss}`,
ruta `reportes/libro-cotizaciones` e ítem en el grupo `reportes` del menú.

Clona `LibroVentasService`/`LibroComprasService` + Apache POI para el Excel.
Columnas: Número, Fecha, Cliente, Estado, Neto, IVA, Total, Usuario, Documentos
relacionados. Incluye **todos** los estados (rechazadas, vencidas y canceladas
también) y nunca borra registros históricos.

## 13. Documentos relacionados (trazabilidad preparada)

**Archivos:** `backend/src/main/resources/db/migration/V35__cotizacion_documento.sql`,
`CotizacionDocumento.java` + repositorio, ampliación del DTO de detalle, y la
sección correspondiente en `cotizacion-detalle.component.*`.

Tabla `cotizacion_documento` (`id`, `tenant_id`, `cotizacion_id`,
`tipo_documento VARCHAR(30)`, `documento_id BIGINT`, `fecha TIMESTAMP`) como el
punto de extensión que exigen las secciones 7 y 12 del documento. En esta fase
**nace vacía a propósito**: el detalle muestra la cadena
`Cotización → Nota de Venta` con el estado "No existen documentos relacionados",
y queda lista para Nota de Venta / Guía / Factura / Pago sin migrar datos.

---

## Verificación end-to-end

1. **Backend**: suite completa en verde (comando Docker de los Global Constraints,
   sin `-Dtest`); confirmar que la migración `V34` aplica limpio revisando los
   logs de Flyway al levantar el backend.
2. **Frontend**: `npx ng test --watch=false` para los specs nuevos. Nota: hoy hay
   **7 fallos preexistentes** ajenos a este módulo (DashboardComponent y los specs
   de interceptores/AdminAuthService); el criterio es no sumar fallos nuevos.
3. **Manual**, con la app levantada (`docker compose up -d --build backend frontend`,
   login demo `admin@demo.cl` / `admin123` en `http://localhost:4200`):
   - Crear una cotización con dos líneas, una con descuento, y verificar que
     Subtotal/Descuento/Neto/IVA/Total cuadran y que el número es `COT-000001`.
   - Editarla (solo debe permitirlo en BORRADOR), enviarla, y comprobar que
     Editar y Eliminar desaparecen y aparecen Aceptar/Rechazar.
   - Aceptar una, rechazar otra con motivo, cancelar una tercera; verificar los
     badges y que el historial registre cada evento con usuario y estados.
   - Duplicar una rechazada: debe nacer en BORRADOR con folio nuevo y con el
     evento DUPLICADA en ambas.
   - Descargar el PDF y revisar encabezado, cliente, líneas y totales.
   - Marcar manualmente una cotización ENVIADA con vencimiento pasado en la BD y
     ejecutar el job (o su método) para confirmar que pasa a VENCIDA.
   - Fase 2: comprobar KPIs, gráfico por estado y tasa de conversión contra los
     datos del listado, y que el Libro incluye las canceladas/rechazadas.
