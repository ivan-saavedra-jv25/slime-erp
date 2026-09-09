# Flujo de Caja conectado a backend (con Ventas y Compras reales) — Spec

## Objetivo

Hoy `frontend/src/app/features/flujo-caja` es una herramienta de proyección financiera (presupuesto mensual: ingresos/gastos recurrentes y puntuales, por categoría) que vive **enteramente en el navegador** (`localStorage`, ids generados en el cliente). No tiene backend, no es multi-tenant, y no sabe nada de las Ventas ni Compras reales que ya se registran en el sistema.

Este trabajo:

1. Crea un módulo backend nuevo (`cl.slimerp.flujocaja`) que persiste en PostgreSQL, por tenant, todo lo que hoy vive en `localStorage`: `Settings`, `Categoria`, `Recurrente`, `OneOff`, `Override`.
2. Agrega un endpoint que agrega los totales reales de Ventas y Compras por mes (reutilizando las tablas `venta`/`compra` ya existentes, sin duplicarlas).
3. Extiende el motor de proyección del frontend (`projection.ts`) para que cada mes sume automáticamente esos totales reales de Ventas (ingreso) y Compras (gasto) junto a lo planeado manualmente (recurrentes/puntuales) — el usuario deja de tener que ingresar sus ventas/compras a mano en el flujo de caja.
4. Migra el store del frontend (`cashflow.store.ts`) de síncrono/localStorage a asíncrono/HTTP, con ids asignados por la base de datos (nunca por el frontend, según regla de `CLAUDE.md`).

## No-goals (fuera de alcance)

- El módulo **Caja** (apertura/cierre de caja diaria, arqueo, `frontend/src/app/features/caja`) queda explícitamente fuera de este trabajo — es un módulo distinto (caja diaria vs. proyección mensual) con su propio backlog.
- No se migran datos existentes de `localStorage` al backend — cada tenant parte con el módulo vacío (decisión confirmada con el usuario).
- No se mueve el motor de cálculo (`projection.ts`) al backend — se mantiene en el frontend y solo se le agrega la entrada de datos reales.
- Se elimina la funcionalidad de "importar respaldo" (restaurar un JSON completo) porque los ids del backend no calzan con los ids de cliente de un respaldo viejo. Se mantiene "exportar/descargar respaldo" como conveniencia de solo lectura (snapshot del estado actual ya cargado).

## Arquitectura

```
Angular (flujo-caja)
   ↓ HttpClient (flujo-caja-api.service.ts, nuevo)
Backend: cl.slimerp.flujocaja (nuevo módulo)
   ↓ Spring Data JPA
PostgreSQL: flujo_caja_settings, flujo_caja_categoria,
            flujo_caja_recurrente, flujo_caja_one_off,
            flujo_caja_override   (nuevas, migración V22)
   +
Backend: cl.slimerp.flujocaja.RealesController (agregación)
   ↓ reutiliza VentaRepository / CompraRepository existentes
PostgreSQL: venta, compra   (ya existentes, sin cambios de esquema)
```

El motor `projectMonth`/`projectMonths`/`projectYear` sigue corriendo en el frontend; recibe el `CashflowState` (ahora poblado desde la API en vez de `localStorage`) más un mapa de totales reales por mes (`Map<MonthKey, { totalVentas: number; totalCompras: number }>`) que también viene de la API.

## Modelo de datos (backend)

Nueva migración `backend/src/main/resources/db/migration/V22__flujo_caja.sql`. Todas las tablas siguen el patrón ya usado en `venta`/`compra`/`categoria`: `id BIGSERIAL PRIMARY KEY`, `tenant_id BIGINT NOT NULL REFERENCES tenant(id)`, `activo BOOLEAN NOT NULL DEFAULT true` para soft-delete donde aplica, `fecha_creacion TIMESTAMP NOT NULL DEFAULT now()`.

### `flujo_caja_settings`

Una fila por tenant (constraint `UNIQUE(tenant_id)`).

| Columna | Tipo | Notas |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `tenant_id` | BIGINT NOT NULL UNIQUE | FK a `tenant` |
| `base_year` | INT NOT NULL | default: año actual al crear |
| `opening_balance` | NUMERIC(14,2) NOT NULL DEFAULT 0 | |
| `tax_rate_percent` | NUMERIC(5,2) NOT NULL DEFAULT 0 | 0–100 |
| `contingency_months` | INT NOT NULL DEFAULT 0 | 0–24 |
| `fecha_creacion` | TIMESTAMP NOT NULL DEFAULT now() | |

Sin fila para un tenant = valores por defecto. Se crea perezosamente (lazy) la primera vez que se pide `GET /api/flujo-caja/settings` para ese tenant y no existe fila — no se toca el flujo de creación de empresas existente (`EmpresaAdminController`), evitando cambiar arquitectura sin necesidad.

### `flujo_caja_categoria`

| Columna | Tipo | Notas |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `tenant_id` | BIGINT NOT NULL | FK a `tenant` |
| `nombre` | VARCHAR(100) NOT NULL | |
| `kind` | VARCHAR(10) NOT NULL | `income` \| `expense` |
| `nature` | VARCHAR(20) NOT NULL | `operational` \| `non_operational` \| `financing` |
| `variability` | VARCHAR(10) NOT NULL | `fixed` \| `variable` (se ignora si `kind = income`, igual que hoy en frontend) |
| `activo` | BOOLEAN NOT NULL DEFAULT true | soft delete |
| `fecha_creacion` | TIMESTAMP NOT NULL DEFAULT now() | |

### `flujo_caja_recurrente`

| Columna | Tipo | Notas |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `tenant_id` | BIGINT NOT NULL | |
| `kind` | VARCHAR(10) NOT NULL | |
| `categoria_id` | BIGINT NOT NULL | FK a `flujo_caja_categoria` |
| `descripcion` | VARCHAR(200) NOT NULL | |
| `monto` | NUMERIC(14,2) NOT NULL | |
| `from_month` | CHAR(7) NOT NULL | formato `YYYY-MM` |
| `to_month` | CHAR(7) | `NULL` = sin fecha de término |
| `interest_amount` | NUMERIC(14,2) | `NULL` = no es servicio de deuda |
| `activo` | BOOLEAN NOT NULL DEFAULT true | |
| `fecha_creacion` | TIMESTAMP NOT NULL DEFAULT now() | |

### `flujo_caja_one_off`

Igual a `flujo_caja_recurrente` pero con `month CHAR(7) NOT NULL` en vez de `from_month`/`to_month`.

### `flujo_caja_override`

| Columna | Tipo | Notas |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `tenant_id` | BIGINT NOT NULL | |
| `recurrente_id` | BIGINT NOT NULL | FK a `flujo_caja_recurrente`, `ON DELETE CASCADE` |
| `month` | CHAR(7) NOT NULL | |
| `monto` | NUMERIC(14,2) | `NULL` = el recurrente se omite ese mes |

`UNIQUE(recurrente_id, month)` — un override por recurrente y mes (mismo invariante que hoy mantiene `cashflow.store.ts` en memoria).

`ON DELETE CASCADE` en `recurrente_id` reemplaza la limpieza manual que hoy hace `removeRecurring()` en el store (borra los overrides huérfanos a mano).

## API backend

Todos los endpoints bajo `/api/flujo-caja`, protegidos con `@PreAuthorize("hasAuthority('FLUJO_CAJA_VER')")` para lecturas y `hasAuthority('FLUJO_CAJA_EDITAR')` para escrituras, mismo patrón que `CategoriaController`. Todos usan `TenantContext.getTenantId()`, nunca un `tenantId` recibido del cliente.

| Método | Ruta | Body | Respuesta |
|---|---|---|---|
| GET | `/api/flujo-caja/settings` | — | `Settings` (crea fila con defaults si no existe) |
| PUT | `/api/flujo-caja/settings` | `SettingsRequest` | `Settings` actualizado |
| GET | `/api/flujo-caja/categorias` | — | `Categoria[]` (solo `activo=true`) |
| POST | `/api/flujo-caja/categorias` | `CategoriaRequest` | `Categoria` creada |
| PUT | `/api/flujo-caja/categorias/{id}` | `CategoriaRequest` | `Categoria` actualizada |
| DELETE | `/api/flujo-caja/categorias/{id}` | — | 204, o 409 si `categoria_id` está en uso por algún recurrente/one-off activo (`FlujoCajaConflictException`, mismo patrón que `ProductoConflictException`) |
| GET | `/api/flujo-caja/recurrentes` | — | `Recurrente[]` |
| POST | `/api/flujo-caja/recurrentes` | `RecurrenteRequest` | `Recurrente` creado |
| PUT | `/api/flujo-caja/recurrentes/{id}` | `RecurrenteRequest` | `Recurrente` actualizado |
| DELETE | `/api/flujo-caja/recurrentes/{id}` | — | 204 (borra también sus overrides por `ON DELETE CASCADE`) |
| GET | `/api/flujo-caja/one-off` | — | `OneOff[]` |
| POST | `/api/flujo-caja/one-off` | `OneOffRequest` | `OneOff` creado |
| PUT | `/api/flujo-caja/one-off/{id}` | `OneOffRequest` | `OneOff` actualizado |
| DELETE | `/api/flujo-caja/one-off/{id}` | — | 204 |
| GET | `/api/flujo-caja/overrides` | — | `Override[]` |
| PUT | `/api/flujo-caja/overrides` | `OverrideRequest` (`recurrenteId`, `month`, `monto`) | upsert por `(recurrenteId, month)`, `Override` resultante |
| DELETE | `/api/flujo-caja/overrides/{recurrenteId}/{month}` | — | 204 |
| GET | `/api/flujo-caja/reales?desde=YYYY-MM&hasta=YYYY-MM` | — | `[{ month: string, totalVentas: number, totalCompras: number }]` |

Validación (`jakarta.validation` en los `*Request`, igual que `CategoriaRequest`):
- `nombre`/`descripcion`: `@NotBlank`.
- `monto`: `@Positive` — el frontend ya valida `amount > 0` en sus diálogos (`item-dialog.ts`) y el signo (ingreso/gasto) lo determina `kind`, nunca el signo del monto.
- `fromMonth`/`toMonth`/`month`: `@Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])")`.
- `taxRatePercent`: `@DecimalMin("0") @DecimalMax("100")`. `contingencyMonths`: `@Min(0) @Max(24)`.

### Endpoint de agregados reales

`FlujoCajaRealesController` (o método dentro del mismo controller) ejecuta dos queries agregadas (una sobre `venta`, otra sobre `compra`), agrupando por `to_char(fecha, 'YYYY-MM')`, filtrando por `tenant_id = TenantContext.getTenantId()` y `fecha` dentro de `[desde, hasta]`. Se implementa como método `@Query(nativeQuery = true)` en `VentaRepository`/`CompraRepository` (o en un repositorio de solo lectura del nuevo módulo que consulta esas tablas), devolviendo una projection `(String month, BigDecimal total)`. El servicio del nuevo módulo combina ambos resultados en la lista de respuesta, rellenando con `0` los meses del rango que no tengan ventas o compras.

## Permisos

En `backend/src/main/java/cl/slimerp/permisos/Permiso.java`, agregar:
```java
FLUJO_CAJA_VER,
FLUJO_CAJA_EDITAR
```

En `RolPermisos.java`:
- `ADMIN`: agrega `FLUJO_CAJA_VER`, `FLUJO_CAJA_EDITAR`.
- `VISUALIZADOR`: agrega `FLUJO_CAJA_VER`.
- `VENDEDOR`, `COMPRADOR`: sin cambios (no tienen acceso, es una herramienta de planificación financiera general, no de ventas/compras operativas).
- `SUPER_ADMIN`: ya tiene todos los permisos vía `EnumSet.allOf(Permiso.class)`.

## Cambios en frontend

### `core/models.ts`

- `Category.id`, `RecurringItem.id`, `OneOffItem.id`: `string` → `number`.
- `RecurringItem.categoryId`, `OneOffItem.categoryId`: `string` → `number`.
- `Override`: gana `id: number` (antes se identificaba solo por `recurringId`+`month`); `recurringId: string` → `recurringId: number`.
- Se agregan dos constantes de categoría "sintética" para las líneas reales, usadas solo en el frontend para etiquetarlas en la UI (no son filas de `flujo_caja_categoria`):
  ```ts
  export const CATEGORIA_VENTAS_REAL = -1; // sentinel, nunca colisiona con un id real (BIGSERIAL > 0)
  export const CATEGORIA_COMPRAS_REAL = -2;
  ```

### `core/projection.ts`

- `Line['source']` gana el valor `'real'`.
- `projectMonth(state, month, openingBalance, accumulatedTaxProvision, real?: { totalVentas: number; totalCompras: number })`: si `real` viene definido y `totalVentas > 0`, hace `push(..., 'real', 'income', CATEGORIA_VENTAS_REAL, 'Ventas del mes', real.totalVentas, false, null)`; análogo para `totalCompras` como `expense`/`CATEGORIA_COMPRAS_REAL`/`'Compras del mes'`. La función `classify()`/`push()` ya resuelve categorías desconocidas a `ORPHAN_CATEGORY` (`operational`/`variable`) — como los sentinels nunca existen en `state.categories`, no se necesita ningún caso especial ahí.
- `projectMonths`/`projectThrough`/`projectYear` reciben y propagan un `Map<MonthKey, { totalVentas: number; totalCompras: number }>` opcional (uno por mes del rango).

### `core/storage.service.ts` → reemplazado por `core/flujo-caja-api.service.ts`

Nuevo servicio `HttpClient`-based, mismo estilo que otros `*.service.ts` del proyecto (ver `frontend/src/app/core/services/cliente.service.ts` como referencia de convención). Expone un método por endpoint de la tabla de arriba, más `getReales(desde: MonthKey, hasta: MonthKey)`. Se elimina `StorageService`, `STORAGE_BACKEND`, `parseState` y todo el parsing defensivo de JSON (ya no aplica: la fuente de verdad es la API, no un blob de `localStorage` que puede corromperse) — la función `download()` (exportar respaldo) se traslada a este nuevo servicio o se mantiene como utilidad standalone reutilizando `serialize()`.

`core/migrate.ts` (migraciones de versiones antiguas de `localStorage`) deja de tener sentido y se elimina junto con `STATE_VERSION`.

### `core/cashflow.store.ts`

Cambios estructurales:
- Se agrega `readonly loading = signal(true)` y `readonly loadError = signal<string | null>(null)`.
- El constructor ya no puede leer el estado de forma síncrona (`this.loaded = this.storage.load()`); pasa a disparar la carga inicial (settings + categorías + recurrentes + one-off + overrides + reales del rango visible) vía `HttpClient`, en paralelo (`forkJoin`), y poblar `_state` al resolver.
- Se elimina el `effect(() => this.storage.save(this._state()))` — ya no hay "guardar todo el blob"; cada mutador llama al endpoint específico.
- Cada mutador (`addRecurring`, `updateRecurring`, `removeRecurring`, `addOneOff`, `updateOneOff`, `removeOneOff`, `setOverride`, `clearOverride`, `addCategory`, `updateCategory`, `removeCategory`, `updateSettings`, `updateProvisions`) pasa de "mutar el signal directo" a "llamar la API y, en éxito, mutar el signal con la entidad devuelta por el backend (con su id real)". Se mantiene la misma firma pública donde sea posible para minimizar cambios en los componentes que consumen el store (`month-detail.ts`, `settings.ts`, `item-dialog.ts`, `override-dialog.ts`, `dashboard.ts`).
- `removeCategory`/`categoryUsage`: la validación de "no borrar si está en uso" ahora la hace el backend (409); el store puede seguir exponiendo `categoryUsage()` como chequeo optimista en el frontend (deshabilitar el botón de borrar) pero la fuente de verdad del error es la respuesta HTTP.
- El "año visible" (`_year`) y "mes seleccionado" (`_selectedMonth`) siguen siendo estado puramente de UI, sin persistir — no cambian.
- Al cambiar de año/mes visible, el store vuelve a pedir `/reales` para el nuevo rango de 12 meses (con cache simple en memoria para no repetir la misma consulta).

### Componentes UI

- `month-detail.ts` / `dashboard.ts`: cuando una línea tiene `categoryId` igual a `CATEGORIA_VENTAS_REAL`/`CATEGORIA_COMPRAS_REAL`, mostrar la etiqueta "Ventas del mes (real)" / "Compras del mes (real)" en vez de intentar buscarla en `categories()` (que devolvería "Sin categoría"). Estas líneas no son editables ni eliminables desde la UI (no tienen fila en `flujo_caja_recurrente`/`flujo_caja_one_off`).
- `settings.ts`: los formularios de categorías/recurrentes ya no generan su propio id; el guardado exitoso viene de la respuesta HTTP.
- Loading/error: mientras `store.loading()` es `true`, `shell.ts` muestra un estado de carga (reutilizar el patrón de loading ya usado en otros módulos del frontend, p. ej. `productos`). Si `loadError()` tiene valor, mostrar un mensaje de error con reintento — no hay fallback silencioso a datos de ejemplo (`seedState()`/`sampleState()` se mantienen solo como "cargar flujo de ejemplo" explícito a pedido del usuario, no como fallback de error).

## Manejo de errores

- 401/403: interceptor HTTP global ya existente en el proyecto (`frontend/src/app/core/interceptors`) — sin cambios, se reutiliza.
- 404 en `GET /settings`: no debería ocurrir (se crea perezosamente); si ocurre por bug, el store lo trata como error de carga.
- 409 al borrar categoría en uso: el store propaga el mensaje del backend para mostrarlo en el diálogo de confirmación (reemplaza el actual `categoryUsage(id) > 0` que bloqueaba el botón solo del lado cliente).
- 400 de validación: mismo formato `Map<String,Object>` que ya devuelve `GlobalExceptionHandler` para el resto de la app.

## Testing

- **Backend**: tests de integración por controlador (mismo patrón que el resto del backend en `backend/src/test`), cubriendo: CRUD completo por entidad, aislamiento multi-tenant (usuario de tenant A no puede leer/editar/borrar recursos de tenant B — replicar el patrón de test ya usado para otros módulos si existe), y el endpoint `/reales` con datos de `venta`/`compra` sembrados en el test.
- **Frontend**: `projection.ts` ya tiene (o debería tener) tests unitarios dado que es lógica pura — agregar casos para la nueva rama `real`. `cashflow.store.ts` pasa a requerir mocks de `HttpClient` (`HttpTestingController`) en vez de un `StorageLike` en memoria — los tests existentes que inyectan un storage falso se migran a interceptar las llamadas HTTP.

## Decisiones ya validadas con el usuario

1. Ventas y Compras reales **alimentan automáticamente** el mes correspondiente de la proyección (no reemplazan lo planeado, no se muestran en columnas separadas real-vs-proyectado).
2. El módulo **Caja** (caja diaria) queda fuera de este trabajo.
3. **No se migran** datos existentes de `localStorage` — cada tenant parte limpio.
