# Búsqueda ágil de productos en Movimientos — Design

## Contexto

En **Inventario > Movimientos** el usuario arma un movimiento (ENTRADA / SALIDA /
TRASLADO / AJUSTE) buscando productos uno por uno y ajustando cantidades antes de
confirmar. Hoy la búsqueda:

- Carga **todos** los productos del tenant al entrar a la pantalla y filtra en
  memoria por nombre/SKU (sin debounce, sin llamada al backend).
- No busca por código de barra porque el modelo `Producto` no tiene ese campo.
- Es un flujo de dos pasos: buscar → seleccionar → ajustar cantidad → click en
  "+" para recién agregar la línea.
- No hay forma de cargar muchos movimientos a la vez ni de crear un producto
  al vuelo si el código escaneado no existe.

Este documento cubre las cuatro piezas acordadas con el usuario:

1. Campo **código de barra** en `Producto` (backend + frontend), único por
   tenant cuando está presente.
2. Unificar la búsqueda de Movimientos con el patrón que ya usa Ventas
   (debounce + backend paginado), agregando código de barra a los campos
   buscables — así un lector de código de barras "simplemente funciona".
3. Si no hay match, ofrecer **crear el producto al vuelo** desde un diálogo
   rápido sin salir de Movimientos.
4. **Carga masiva por Excel**: filas con Código, Cantidad, Tipo, Bodega,
   Observación; las filas que comparten Tipo+Bodega+Observación se agrupan en
   un solo movimiento con varios ítems, reutilizando exactamente las mismas
   validaciones que la carga manual.

## Global Constraints

- El `id` de toda tabla es autoincremental y lo genera la base de datos — el
  frontend nunca lo envía (regla del proyecto, `CLAUDE.md`).
- Formularios nuevos/editados siguen el sistema de diseño existente: HTML
  nativo (`.form-group`, `input`/`select`/`textarea`) estilizado con los
  tokens de diseño; Material solo para botones, tarjetas, tablas e íconos
  (`frontend/src/styles/_components.scss`).
- No modificar el comportamiento existente de Movimientos/Productos solo por
  prolijidad; no eliminar campos sin pedido explícito.
- Todo endpoint nuevo sigue el patrón multi-tenant existente
  (`TenantContext.getTenantId()`, `@PreAuthorize("hasAuthority(...)")`) y los
  errores de negocio se exponen como `IllegalArgumentException` /
  `ResponseStatusException`-friendly, capturados por
  `GlobalExceptionHandler` (backend/src/main/java/cl/slimerp/config/GlobalExceptionHandler.java).

---

## 1. Código de barra en `Producto`

### Migración

Nuevo archivo `backend/src/main/resources/db/migration/V19__producto_codigo_barra.sql`
(V18 es la última existente):

```sql
ALTER TABLE producto ADD COLUMN codigo_barra VARCHAR(64);

-- Único por tenant solo cuando está presente (Postgres soporta índices
-- únicos parciales; así muchos productos pueden seguir sin código de barra).
CREATE UNIQUE INDEX uq_producto_tenant_codigo_barra
    ON producto (tenant_id, codigo_barra)
    WHERE codigo_barra IS NOT NULL;
```

### Backend

- `Producto.java`: agregar `@Column(name = "codigo_barra", length = 64) private String codigoBarra;`.
- `ProductoRequest.java`: agregar `String codigoBarra` al record.
- `ProductoController`: `crear`/`actualizar` setean `codigoBarra` igual que `sku`.
- Antes de guardar (crear y actualizar), si `codigoBarra` no es null/blank,
  validar unicidad explícita con una nueva query de repositorio
  (`existsByTenantIdAndCodigoBarraAndIdNot` / `existsByTenantIdAndCodigoBarra`)
  y lanzar una excepción de dominio nueva `ProductoConflictException` (mismo
  patrón que `UsuarioConflictException`/`EmpresaConflictException`) con
  mensaje `"Ya existe un producto con el código de barra <valor>"`, mapeada a
  409 en `GlobalExceptionHandler`. Esto evita depender de que la violación de
  índice único de Postgres burbujee como un 500 genérico.
- `ProductoRepository`:
  - `Optional<Producto> findFirstByTenantIdAndCodigoBarra(Long tenantId, String codigoBarra)`
    (lookup exacto, usado por el importador de Excel).
  - `boolean existsByTenantIdAndCodigoBarraAndIdNot(Long tenantId, String codigoBarra, Long id)`
    y `boolean existsByTenantIdAndCodigoBarra(Long tenantId, String codigoBarra)`
    (para la validación de unicidad).
  - `buscar(...)`: agregar `OR LOWER(COALESCE(p.codigoBarra, '')) LIKE :busqueda`
    a la cláusula `WHERE` existente, así el mismo endpoint
    `GET /api/productos/pagina?q=` ya sirve para escanear (el código exacto
    hace match único contra `LIKE '%codigo%'`).

### Frontend

- `frontend/src/app/core/models/models.ts` → `Producto`: agregar
  `codigoBarra: string | null;`.
- `frontend/src/app/core/services/producto.service.ts` → `ProductoRequest`:
  agregar `codigoBarra?: string | null;`.
- `frontend/src/app/features/productos/productos.component.ts`: agregar
  propiedad `codigoBarra = '';`, incluirla en `editar()`, `guardar()` y
  `limpiarFormulario()`.
- `frontend/src/app/features/productos/productos.component.html`: agregar un
  `form-group` "Código de barra (opcional)" junto al de SKU (misma fila del
  `form-grid`), y una columna `codigoBarra` en la tabla `mat-table` (opcional,
  junto a SKU) para poder verificarlo de un vistazo.
- Mostrar el error 409 del backend igual que ya se hace con otros errores
  (`err?.error?.error`).

---

## 2. Búsqueda unificada en Movimientos (debounce + backend)

Alinear `MovimientosComponent` con el patrón ya usado en
`VentasComponent` (`frontend/src/app/features/ventas/ventas.component.ts`):

- Reemplazar la carga completa `productoService.listar()` +
  `productosFiltrados` (getter en memoria) por:
  - `private readonly busquedaProducto$ = new Subject<string>();`
  - `productosResultados: Producto[] = []; productosTotal = 0;`
  - `private readonly productosConocidos = new Map<number, Producto>();`
    (para poder mostrar nombre/SKU de las líneas ya agregadas incluso
    después de limpiar `productosResultados`, ya que Movimientos deja de
    tener el arreglo completo en memoria).
  - `ngOnInit`: `this.busquedaProducto$.pipe(debounceTime(300), distinctUntilChanged()).subscribe(q => this.buscarProductos(q))`.
  - `buscarProductos(q)`: si `q` vacío, limpia resultados; si no,
    `productoService.listarPagina(q, 0, 8)` (mismo límite que Ventas) y
    guarda cada resultado en `productosConocidos`.
  - `onFiltroProductoChange()` llamado desde `(ngModelChange)` del input,
    hace `busquedaProducto$.next(this.filtroProducto)`.
  - `ngOnDestroy()`: `busquedaProducto$.complete()` (el componente pasa a
    implementar `OnDestroy`).
- `nombreProducto(id)`/`skuProducto(id)`: ahora resuelven contra
  `productosConocidos.get(id)` en vez del arreglo completo `productos`.

### Selección agrega la línea al instante

Cambio de comportamiento acordado con el usuario: hoy seleccionar un
resultado solo llena un "producto seleccionado" a la espera de que el
usuario ajuste cantidad y presione "+". Pasa a comportarse como un lector de
código de barras real:

- `seleccionarProducto(producto)`:
  - Si `producto.id` ya está en `items`, **incrementa** su `cantidad` en 1
    (en vez de mostrar el error "ya está en la lista" — así escanear el mismo
    código dos veces suma unidades, que es lo esperado en un flujo de
    escaneo).
  - Si no está, agrega `{ productoId: producto.id, cantidad: 1 }` al final de
    `items`.
  - Limpia `filtroProducto` y `productosResultados` para el siguiente scan.
- `seleccionarPrimero()` (atado a `(keydown.enter)`) sigue funcionando igual:
  con un único resultado (el caso típico de un código de barra exacto),
  Enter agrega la línea sin tocar el mouse.
- Se elimina el bloque "Producto seleccionado" + input de cantidad + botón
  "+" del `item-entry`; la cantidad ahora se edita **inline en la tabla**:
  la columna "Cantidad" pasa de texto plano a
  `<input type="number" min="1" [ngModel]="it.cantidad" (ngModelChange)="actualizarCantidad(i, $event)" />`.
  `actualizarCantidad(i, valor)` valida `valor > 0` antes de asignar.
- `agregarItem()` y el estado `itemProductoId`/`itemError` (el mensaje "ya
  está en la lista") se eliminan; `puedeConfirmar` no cambia.

### Sin match → ofrecer crear producto

- Nuevo estado derivado:
  `get sinResultados(): boolean { return this.filtroProducto.trim().length > 0 && this.busquedaCompleta && this.productosResultados.length === 0; }`
  (`busquedaCompleta` es un flag que se pone en `true` al recibir la
  respuesta de `buscarProductos` y en `false` justo antes de llamar, para no
  mostrar el mensaje mientras el debounce todavía no disparó la consulta).
- En la plantilla, cuando `sinResultados`, mostrar en el panel de resultados:
  `No se encontró ningún producto para "<query>".` +
  `<button mat-stroked-button (click)="abrirCreacionRapida()">Crear producto nuevo</button>`.
- `abrirCreacionRapida()` abre el diálogo (sección 3) pasando `filtroProducto`
  como dato inicial.

---

## 3. Diálogo de creación rápida de producto

Nuevo componente standalone
`frontend/src/app/features/movimientos/producto-rapido-dialog.component.ts`
(mismo patrón que `venta-pdf-dialog.component.ts`: `MAT_DIALOG_DATA` +
`MatDialogRef`, template inline).

**Datos de entrada** (`ProductoRapidoDialogData`): `{ textoBusqueda: string }`.

**Campos del formulario** (subconjunto del formulario completo de Productos,
para mantenerlo rápido): Nombre* (`required`), SKU (opcional), Código de
barra (opcional — precargado con `textoBusqueda` **solo si es puramente
numérico y tiene 6 o más dígitos**, patrón típico de EAN-8/EAN-13/UPC; si no,
queda vacío y editable), Categoría (select opcional, se carga vía
`CategoriaService.listar()` igual que en `ProductosComponent`), Precio venta*
(`required`, `min=0`), Precio compra (opcional), Stock mínimo (opcional). Se
omite Descripción y Subcategoría para no alargar el formulario — no aportan
al flujo de "agregar rápido durante un movimiento".

**Guardar**: `productoService.crear(request)` → al resolver, `dialogRef.close(productoCreado)`.
Errores (ej. código de barra duplicado, 409) se muestran inline con
`.field-error`, sin cerrar el diálogo.

**En `MovimientosComponent`**:
```ts
abrirCreacionRapida(): void {
  const ref = this.dialog.open(ProductoRapidoDialogComponent, {
    data: { textoBusqueda: this.filtroProducto },
  });
  ref.afterClosed().subscribe((producto?: Producto) => {
    if (!producto) return;
    this.productosConocidos.set(producto.id, producto);
    this.seleccionarProducto(producto); // agrega la línea al instante, igual que un resultado de búsqueda
  });
}
```
Requiere agregar `MatDialogModule`/`MatDialog` a los imports/inject de
`MovimientosComponent`.

---

## 4. Carga masiva por Excel

### Alcance y agrupación (confirmado con el usuario)

- El Excel trae **solo movimientos** de productos ya existentes — no crea
  productos nuevos (si una fila no matchea ningún producto, esa fila queda
  como error y no se importa; el resto del archivo se sigue procesando).
- Columnas esperadas (fila de encabezado + datos desde la fila 2):
  `Codigo` (SKU o código de barra — se prueba SKU exacto primero, luego
  código de barra exacto), `Cantidad`, `Tipo` (`ENTRADA`/`SALIDA`/`TRASLADO`/`AJUSTE`,
  case-insensitive), `Bodega` (nombre exacto; para `TRASLADO`, ver más abajo),
  `Observacion` (opcional).
- Para `TRASLADO` la columna `Bodega` no alcanza a expresar origen y destino
  con una sola celda: se usan dos columnas adicionales opcionales
  `Bodega Origen` / `Bodega Destino`, que si están presentes tienen
  prioridad sobre `Bodega` para esa fila. Si el tipo es `TRASLADO` y faltan
  ambas, la fila es un error ("Traslado requiere Bodega Origen y Bodega
  Destino").
- **Agrupación**: las filas se recorren en orden y se agrupan por la clave
  `(Tipo, BodegaOrigenId resuelto, BodegaDestinoId resuelto, Observación)`
  usando un `LinkedHashMap` (preserva el primer orden de aparición). Cada
  grupo se convierte en **un** `MovimientoRequest` con tantos
  `MovimientoItemRequest` como filas válidas tenga ese grupo. Si dos filas
  del mismo grupo referencian el mismo producto, se sigue el mismo criterio
  que la carga manual: se suman en un solo ítem del grupo (evita que
  `MovimientoInventarioService.crear` reciba dos líneas para el mismo
  producto, que no está contemplado hoy).
- Cada grupo se crea llamando **exactamente** a
  `MovimientoInventarioService.crear(tenantId, usuarioId, request)` (la misma
  ruta que usa la carga manual), así todas las reglas de negocio (bodega
  requerida según tipo, stock disponible, producto activo) quedan en un solo
  lugar. Un grupo que falla (`IllegalArgumentException`) no aborta el
  archivo completo: se registra como error para todas las filas de ese
  grupo y se sigue con el siguiente grupo. Como `MovimientoInventarioService.crear`
  ya es `@Transactional` por llamada, cada grupo queda naturalmente aislado
  en su propia transacción sin anotaciones adicionales en el importador.

### Backend

- **Dependencia nueva** en `backend/pom.xml`:
  ```xml
  <dependency>
      <groupId>org.apache.poi</groupId>
      <artifactId>poi-ooxml</artifactId>
      <version>5.2.5</version>
  </dependency>
  ```
  (`poi-ooxml` trae `poi` transitivamente; cubre `.xlsx`).
- Nueva clase `MovimientoImportService`
  (`backend/src/main/java/cl/slimerp/inventario/MovimientoImportService.java`):
  - `parsear(InputStream xlsx)`: usa `XSSFWorkbook`/`DataFormatter` para leer
    la primera hoja, valida el encabezado esperado, y devuelve una lista de
    "filas crudas" (número de fila, valores de texto de cada columna) — sin
    tocar la base de datos todavía.
  - `resolverYAgrupar(tenantId, filas)`: para cada fila resuelve
    `productoId` (por SKU exacto vía `findFirstByTenantIdAndSku`, si no por
    código de barra exacto vía `findFirstByTenantIdAndCodigoBarra`), `tipo`
    (`TipoMovimiento.valueOf(...)` case-insensitive), `bodegaOrigenId`/`bodegaDestinoId`
    (por nombre exacto vía un nuevo
    `BodegaRepository.findFirstByTenantIdAndNombreIgnoreCaseAndActivoTrue`),
    y `cantidad` (`BigDecimal`, debe ser > 0). Filas con cualquier error de
    resolución quedan marcadas con su motivo y no entran a ningún grupo.
    Agrupa las filas válidas como se describe arriba.
  - `ejecutar(tenantId, usuarioId, grupos)`: por cada grupo, intenta
    `movimientoService.crear(...)`; captura `IllegalArgumentException` y la
    adjunta como error a todas las filas de ese grupo.
  - Devuelve un `ImportResultado` con: `totalFilas`, `movimientosCreados`
    (ids), y `errores: List<FilaError>` (`numeroFila`, `mensaje`).
- Nuevo endpoint en `MovimientoInventarioController`:
  ```java
  @PostMapping(value = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAuthority('MOVIMIENTOS_EDITAR')")
  public ImportResultado importar(@RequestParam("archivo") MultipartFile archivo) { ... }
  ```
- **Plantilla descargable**: archivo estático
  `frontend/public/plantillas/movimientos-carga-masiva.xlsx` (encabezados +
  una fila de ejemplo + una nota de instrucciones en una segunda hoja),
  generado una vez y commiteado — no requiere endpoint backend. Se enlaza
  desde el frontend con un `<a href="/plantillas/movimientos-carga-masiva.xlsx" download>`.

### Frontend

- En `movimientos.component.html`, dentro de la tarjeta "Detalle de
  productos" (o una tarjeta nueva "Carga masiva" inmediatamente después),
  agregar:
  - Link "Descargar plantilla Excel" (al archivo estático).
  - Botón "Cargar Excel" que dispara un `<input type="file" hidden accept=".xlsx" (change)="onArchivoSeleccionado($event)" #fileInput>`
    (mismo patrón *hidden input* que ya usa `flujo_caja` para el respaldo
    JSON).
  - Tras subir, mostrar un resumen: "`N` movimiento(s) creado(s), `M` fila(s)
    con error" + una tabla expandible de errores (`numeroFila`, `mensaje`)
    cuando `M > 0`. Si hubo movimientos creados, ofrecer refrescar/ver en el
    historial (link a `/movimientos/historial`), ya que la importación no
    llena el formulario de la pantalla actual (son movimientos ya
    confirmados, no un borrador).
- `MovimientoService` (`frontend/src/app/core/services/movimiento.service.ts`):
  nuevo método
  ```ts
  importarExcel(archivo: File): Observable<ImportResultado> {
    const formData = new FormData();
    formData.append('archivo', archivo);
    return this.http.post<ImportResultado>(`${this.base}/importar`, formData);
  }
  ```
  y la interfaz `ImportResultado` espejando el DTO backend.

---

## Testing

- Backend: test unitario para `ProductoRepository.buscar` cubriendo match por
  código de barra (puede sumarse al test existente de búsqueda de
  productos, si existe, o crearse uno nuevo); test de
  `MovimientoImportService` con un workbook de prueba armado en memoria
  (`XSSFWorkbook` creado en el test, no un archivo en disco) cubriendo:
  agrupación correcta, fila con producto inexistente, fila con stock
  insuficiente, traslado sin bodegas.
- Frontend: no hay suite de tests para `MovimientosComponent` hoy (verificar
  al implementar); si existiera un patrón de test para `VentasComponent` se
  replica para el nuevo flujo de búsqueda, si no, se valida manualmente en
  navegador (build + docker, como en el resto de la sesión).

## Fuera de alcance

- No se agrega lectura de cámara/hardware de escáner: el "escaneo" depende
  de que el lector de código de barras conectado actúe como teclado (USB
  HID), que es el caso general y no requiere código adicional — el input de
  texto + Enter ya lo soporta.
- El Excel no crea ni actualiza productos (confirmado con el usuario).
- No se pagina el resultado de la importación en el frontend; se asume un
  volumen razonable (cientos de filas, no decenas de miles) dado el uso
  esperado (carga manual de inventario, no un ETL).
