# Módulo Inventario (vista con filtros) — Design

## Contexto

`docs/prompt_modulo_inventario.md` describe un módulo de Inventario con tarjetas
de estado de stock (Crítico/Reposición/Normal/Exceso), filtros combinados,
importador de Excel para umbrales, exportación CSV/XLSX, tabla con historial
de movimientos por producto y ordenamiento/paginación.

Durante el brainstorming con el usuario el alcance se redujo varias veces
hasta quedar en: **una pantalla de solo lectura para consultar y filtrar el
stock por bodega**, sin clasificación de estado, sin importador y sin
historial. Este documento describe lo que efectivamente se va a construir.

### Qué se descarta del prompt original (decisión explícita del usuario)

- Tarjetas de resumen "FILTROS STOCK" (Crítico/Reposición/Normal/Exceso).
- Filtros rápidos por estado (Todos/Crítico/Reposición/Normal/Exceso/Limpiar).
- Campos y columnas `Mínimo`, `Reposición`, `Máximo`, `Tipo`.
- Importador de Excel (sección 5 del prompt).
- Botón/columna "Ver Histórico" y el modal de movimientos asociado.
- Badge de color en la columna Stock (sin umbrales, no hay con qué clasificar).
- El checkbox "Ordenar por Stock": duplica la función de los encabezados de
  tabla clickeables (sección 8 del prompt), que sí se conservan.
- "Categoría" como filtro independiente: se reutilizan las entidades
  `Categoria`/`Subcategoria` ya existentes como "Familia"/"Subfamilia", así
  que un filtro "Categoría" aparte sería el mismo dato duplicado.
- Menú lateral y barra superior propios: la pantalla vive dentro del
  `LayoutComponent` existente, que ya los provee (igual que Productos,
  Bodegas, etc.). El prompt los excluye porque describe una imagen de
  referencia aislada, no la app real.

### Qué se mantiene

- Panel "Filtro de inventario específico": Bodega, Familia, Subfamilia
  (depende de Familia), Ver Deshabilitados, exportar CSV/XLSX.
- Panel "Filtro por tipo de búsqueda": tipo de búsqueda + buscador + botón
  Buscar.
- Tabla Inventario: `# | Producto | Código | Código Barra | Stock`, con
  ordenamiento por columna (asc/desc/sin orden), paginación con selector de
  tamaño (10/25/50/100) y mensaje de rango ("Registros del X al Y de un
  total de Z").
- Estados de loading, vacío y error (sin detalles técnicos).
- Combinación simultánea de todos los filtros, responsive.

No hay imagen de referencia disponible; el diseño visual sigue el sistema de
diseño ya existente en SAAVIA ERP (tokens, tablas Material, `.form-group`,
paneles `mat-card`), no una referencia externa.

---

## Global Constraints

- El `id` de toda tabla es autoincremental y lo genera la base de datos — el
  frontend nunca lo envía (regla del proyecto, `CLAUDE.md`).
- **No se agregan columnas ni migraciones nuevas.** El módulo es de solo
  lectura sobre datos que ya existen: `Producto`, `Categoria`, `Subcategoria`,
  `Bodega`, `StockProductoBodega`.
- Sigue el patrón multi-tenant existente (`TenantContext.getTenantId()`),
  protegido con `@PreAuthorize("hasAuthority('BODEGAS_VER')")` — se reutiliza
  este permiso (misma naturaleza que `StockController`, ya usado para leer
  stock por bodega) en vez de crear un permiso nuevo, ya que no hay ninguna
  acción de edición en este módulo.
- Formularios/paneles nuevos usan el sistema de diseño existente: HTML nativo
  (`.form-group`, `input`/`select`) con los tokens de diseño; Material solo
  para tabla, paginador, botones e íconos — mismo patrón que
  `productos.component.html` / `bodegas.component.html`.
- Errores de negocio expuestos como `IllegalArgumentException`, capturados
  por `GlobalExceptionHandler` (mensajes amigables, nunca stack traces ni SQL
  crudo — ver el fix reciente en `ProductoController`/`GlobalExceptionHandler`
  para el mismo criterio).
- Reutilizar el patrón de carga (`mostrarCargando`/`cerrarCargando` de
  `core/utils/swal-loading.ts`) para las descargas CSV/XLSX, que son
  operaciones de red visibles.

---

## 1. Backend

### 1.1 Endpoint principal

`GET /api/inventario` — paginado, protegido con `BODEGAS_VER`.

Parámetros:

| Parámetro | Tipo | Descripción |
|---|---|---|
| `bodegaId` | `Long` (opcional) | Si viene, Stock = cantidad en esa bodega. Si no viene ("Todos"), Stock = suma entre todas las bodegas activas del tenant. |
| `familiaId` | `Long` (opcional) | = `categoriaId` de `Producto`. |
| `subfamiliaId` | `Long` (opcional) | = `subcategoriaId` de `Producto`. Depende de `familiaId` en el frontend (mismo comportamiento que ya existe en Productos). |
| `verDeshabilitados` | `boolean` (default `false`) | Si es `false`, solo `activo=true` (comportamiento actual). Si es `true`, incluye también `activo=false`. |
| `tipoBusqueda` | `CODIGO_BARRA \| SKU \| NOMBRE` (opcional) | Junto con `busqueda`, define contra qué campo de `Producto` se compara. |
| `busqueda` | `String` (opcional) | Texto de búsqueda, `LIKE` case-insensitive (mismo patrón que `ProductoRepository.buscar`). |
| `sort` | `nombre \| sku \| codigoBarra \| stock` (default `nombre`) | Columna de orden. |
| `dir` | `asc \| desc` (default `asc`) | Dirección. Sin `sort` explícito = orden por defecto (nombre asc), equivalente a "sin orden" visualmente en la tabla. |
| `pagina`, `tamano` | `int` | Igual que el resto de los listados paginados del sistema. |

Respuesta: `PaginaResponse<InventarioConsultaItem>` donde
`InventarioConsultaItem` = `{ productoId, nombre, sku, codigoBarra, stock }`.

### 1.2 Endpoint de resumen (paginación)

No hace falta un endpoint de "resumen" separado (las tarjetas de estado se
eliminaron): `PaginaResponse.total` ya alcanza para el texto "Registros del X
al Y de un total de Z" en el frontend.

### 1.3 Exportación

`GET /api/inventario/exportar.csv` y `GET /api/inventario/exportar.xlsx` —
mismos parámetros de filtro que el endpoint principal (sin `pagina`/`tamano`,
sin `sort`/`dir` — se exporta ordenado por nombre). Generan el archivo con
**todas** las filas que calzan con los filtros activos.

- CSV: escritura simple con columnas `Producto,Código,Código Barra,Stock`
  (sin librería nueva).
- XLSX: reutiliza Apache POI (`poi-ooxml`), ya presente en `pom.xml` y usado
  en `MovimientoImportService`.

### 1.4 Implementación de la consulta (`InventarioConsultaService`)

Dado que el orden por `stock` requiere un valor calculado (cantidad directa o
suma entre bodegas, según `bodegaId`), no una columna simple de `Producto`,
la consulta se resuelve en dos pasos dentro del service, siguiendo el mismo
espíritu que ya usa `StockController.inventarioPorBodegaPagina` (combinar
`Producto` + `StockProductoBodega` en memoria):

1. Traer los `Producto` del tenant que calzan con `familiaId`, `subfamiliaId`,
   `verDeshabilitados` y la búsqueda por `tipoBusqueda`/`busqueda` (nueva
   query en `ProductoRepository`, análoga a `buscar` pero con estos filtros
   adicionales y sin paginar todavía).
2. Traer el stock de esos productos desde `StockProductoBodegaRepository`
   (filtrado por `bodegaId` si viene, o agrupado por `productoId` sumando
   `cantidad` si no viene) y combinarlo con cada producto (0 si no hay
   registro — mismo criterio que el resto del sistema).
3. Ordenar en memoria según `sort`/`dir` y paginar (skip/limit) sobre la
   lista ya combinada.

Esto es correcto y simple; a la escala esperada (catálogos de un tenant
PyME, no millones de filas) el costo de traer y ordenar en memoria es
aceptable. Si en el futuro se vuelve un cuello de botella, se puede migrar a
una consulta nativa con `GROUP BY`/`ORDER BY` a nivel de base de datos.

### 1.5 Nueva query en `ProductoRepository`

Se agrega un método (o `@Query`) equivalente a `buscar(...)` pero:

- Filtra por `categoriaId`/`subcategoriaId` cuando vienen.
- Permite `activo = true` **o** `activo IN (true, false)` según
  `verDeshabilitados` (hoy `buscar` siempre fuerza `activo = true`).
- Busca por un campo específico (`codigoBarra`, `sku` o `nombre`) según
  `tipoBusqueda`, en vez de "cualquiera de los tres" como hace `buscar` hoy.

No se modifica `buscar(...)` existente (lo siguen usando Productos y
Movimientos) — se agrega un método nuevo dedicado a este caso.

---

## 2. Frontend

### 2.1 Ruta y navegación

- Nueva ruta `/inventario` → `InventarioComponent` (standalone, lazy-loaded,
  mismo patrón que `/productos` o `/bodegas` en `app.routes.ts`).
- Nuevo ítem en `layout.component.ts`, dentro del grupo `inventario` ya
  existente, **antes** de "Movimientos":
  ```ts
  { ruta: '/inventario', label: 'Stock', icono: 'inventory', permiso: 'BODEGAS_VER', exact: true }
  ```
  Se usa el label "Stock" (no "Inventario") para no repetir el nombre del
  grupo de navegación ("Inventario › Inventario" se ve raro).

### 2.2 Componente único `InventarioComponent`

Un solo componente (`.ts`/`.html`/`.scss`), igual que Productos/Bodegas/
Clientes — no el árbol de subcomponentes del prompt original (pensado para
una pantalla con tarjetas, importador e historial que ya no existen). Si en
el futuro se reincorporan esas piezas, ahí sí se justifica separar en
componentes más chicos.

Estado del componente:

```ts
bodegas: Bodega[] = [];
familias: Categoria[] = [];
subfamilias: Subcategoria[] = [];

bodegaId: number | null = null;       // null = "Todos"
familiaId: number | null = null;
subfamiliaId: number | null = null;
verDeshabilitados = false;

tipoBusqueda: 'CODIGO_BARRA' | 'SKU' | 'NOMBRE' = 'NOMBRE';
busqueda = '';

sort: 'nombre' | 'sku' | 'codigoBarra' | 'stock' = 'nombre';
dir: 'asc' | 'desc' = 'asc';

items: InventarioConsultaItem[] = [];
total = 0;
pagina = 0;
tamano = 10;

cargando = false;
error = '';
```

### 2.3 Panel "Filtro de inventario específico"

- Select Bodega (`Todos` + lista de `bodegaService.listar()`).
- Select Familia (`Todos` + `categoriaService.listar()`).
- Select Subfamilia (`Todos` + `subcategoriaService.listar(familiaId)`,
  deshabilitado si no hay Familia seleccionada — mismo patrón que
  Categoría/Subcategoría en Productos).
- Checkbox "Ver Deshabilitados".
- Botones "Descargar CSV" / "Descargar XLSX": arman la URL con los filtros
  activos como query params y abren la descarga (`window.open` o un link con
  el token ya en el interceptor HTTP existente); usan `mostrarCargando`/
  `cerrarCargando` mientras se genera el archivo.

Cualquier cambio en estos filtros reinicia `pagina = 0` y vuelve a consultar
(con `debounceTime` solo en el buscador de texto, igual que en Productos).

### 2.4 Panel "Filtro por tipo de búsqueda"

- Select con 3 opciones: Código de Barra, SKU, Nombre (se colapsan las 4 del
  prompt original — `Producto` no tiene un "código interno" distinto de
  `sku`, así que "Código Producto" y "SKU" serían el mismo campo).
- Input de texto.
- Botón "Buscar" (azul, `mat-flat-button color="primary"`) — dispara la
  consulta con el filtro de texto actual. También se puede buscar con Enter.

### 2.5 Tabla Inventario

Columnas: `#` (índice de fila, no id de base de datos), `Producto`,
`Código` (= sku), `Código Barra`, `Stock`.

- Encabezados clickeables para `Producto`, `Código`, `Código Barra` y
  `Stock`, con indicador visual (flecha) de la dirección activa. Tercer clic
  vuelve al orden por defecto (nombre asc).
- Selector "Registros" (10/25/50/100) antes de la tabla.
- `mat-paginator` + texto "Registros del X al Y de un total de Z", con
  "Anterior"/"Siguiente" deshabilitados en los extremos (comportamiento ya
  incluido por `mat-paginator`). Ese texto exacto no es el label por defecto
  de Angular Material (que muestra "1 – 10 de 54", como ya se ve en
  Bodegas/Productos) — se calcula aparte (`desde = pagina*tamano + 1`,
  `hasta = min((pagina+1)*tamano, total)`) y se muestra en un `<p>` propio
  junto al paginador, dejando que `mat-paginator` solo aporte los controles
  de navegación y el selector de tamaño de página.

### 2.6 Estados

- **Loading**: spinner o fila de "Cargando…" mientras se espera la
  respuesta (mismo criterio visual que el resto del sistema).
- **Vacío**: "No se encontraron productos para los filtros seleccionados."
  (idéntico texto al del prompt).
- **Error**: mensaje genérico "No fue posible cargar el inventario. Intente
  nuevamente." — nunca el detalle crudo del backend (mismo criterio que ya
  se aplicó en `ProductoController`/`GlobalExceptionHandler`).

### 2.7 Responsive

- Los selects de los paneles de filtro pasan a una columna en pantallas
  angostas (mismo patrón `.form-grid` con `grid-template-columns` que ya
  usan Productos/Clientes).
- La tabla tiene `overflow-x: auto` en su contenedor para scroll horizontal
  en tablet/mobile.

---

## 3. Combinación de filtros — ejemplo

```text
GET /api/inventario
    ?bodegaId=2
    &familiaId=3
    &subfamiliaId=7
    &verDeshabilitados=false
    &tipoBusqueda=CODIGO_BARRA
    &busqueda=7801234567890
    &sort=stock&dir=desc
    &pagina=0&tamano=25
```

Todos los filtros se combinan con AND. Cambiar cualquiera reinicia la
paginación a la página 0.

---

## 4. Fuera de alcance

- Clasificación de estado de stock (Crítico/Reposición/Normal/Exceso).
- Configuración de umbrales de stock (mínimo/reposición/máximo) — no existe
  hoy ninguna forma de cargarlos; si se necesita en el futuro, es un
  proyecto aparte (probablemente agregando los campos a `Producto` o a
  `StockProductoBodega` con su propia UI de edición).
- Importador de Excel.
- Historial de movimientos por producto desde esta pantalla (ya existe una
  vista de movimientos en `/movimientos/historial`, sin filtrar por
  producto puntual).
- Cualquier acción de edición: el módulo es 100% de lectura.

## 5. Testing

- Backend: tests de `InventarioConsultaService`/`Controller` cubriendo — sin
  filtros, cada filtro individual, combinación de varios, `bodegaId` nulo
  (suma entre bodegas) vs específico, `verDeshabilitados`, cada `tipoBusqueda`,
  orden por cada columna en ambas direcciones, paginación en los bordes
  (primera/última página), exportación CSV/XLSX respetando filtros.
- Frontend: verificar manualmente (no hay suite de tests e2e en el proyecto)
  que los filtros combinados llaman al backend con los parámetros correctos,
  que cambiar un filtro resetea la página, y los tres estados (loading/vacío/
  error).
