# NC-011 — Frontend: listado

## Objetivo

Listado de Notas de Crédito con todas las columnas y filtros que pide la especificación.

## Archivos

`frontend/src/app/features/notas-credito/notas-credito.component.ts | .html | .scss | .spec.ts`

Imports del componente (standalone):
`CommonModule, FormsModule, RouterLink, MatButtonModule, MatIconModule, MatCardModule,
MatPaginatorModule, MatSortModule, HighchartsChartModule, MonedaPipe`.

## Layout (orden fijo, como el de Notas de Venta)

1. `.page-header` — `<h1>Notas de Crédito</h1>` + botón **Nueva** envuelto en
   `@if (auth.tienePermiso('NOTAS_CREDITO_EDITAR'))`.
2. `@if (error) { <p class="page-error">{{ error }}</p> }`
3. `mat-card.form-panel` **Período** — `.rango-selector` de botones
   (`mes / mes anterior / trimestre / año / personalizado`); el rango personalizado muestra
   dos `<input type="date">` y un botón Consultar.
4. `.kpi-grid` de `mat-card.kpi-card`:
   Emitidas · Monto total emitido · Borradores · Anuladas · Con recuperación de inventario ·
   Documentos corregidos.
5. `mat-card.form-panel` con `<highcharts-chart>` — columnas por estado; los colores se
   leen de los tokens en runtime, nunca hardcodeados:
   ```ts
   const estilo = getComputedStyle(document.documentElement);
   EMITIDA: estilo.getPropertyValue('--success-base').trim() || '#16a34a',
   ```
6. `mat-card.form-panel` con **filtros** + tabla + `mat-paginator`.

## Filtros (los cinco que pide la especificación)

| Filtro | Control |
|---|---|
| Fecha | ya cubierto por el selector de período (`desde` / `hasta`) |
| Cliente | buscador con `<input>` + dropdown `.search-results` (debounce 300 ms) |
| Estado | `<select>` alimentado por `ESTADOS` |
| Tipo de corrección | `<select>` alimentado por `TIPOS_CORRECCION` |
| Documento asociado | `<select>` de tipo de documento (Boleta/Factura/Voucher) + `<input>` de folio |

Más un `<input>` de búsqueda libre `q` (número de NC o cliente), con
`Subject<string>` + `debounceTime(300)` + `distinctUntilChanged()` creado en `ngOnInit` y
cerrado en `ngOnDestroy`.

Inputs y selects son **HTML nativo** dentro de `.form-group`; no usar `mat-form-field`.
Un botón discreto **Limpiar filtros** cuando hay al menos uno activo.

## Tabla

Tabla HTML nativa con clase `.libro-table` y las directivas de Material para el sort:

```html
<table class="libro-table" matSort [matSortActive]="orden" [matSortDirection]="direccion"
       (matSortChange)="onSortChange($event)">
```

| Columna | Notas |
|---|---|
| Número | `mat-sort-header="folio"`, muestra `NC-000012` |
| Fecha | `mat-sort-header="fecha"` |
| Cliente | |
| Tipo doc. asociado | Boleta / Factura / Voucher |
| Folio asociado | alineado a la derecha |
| Tipo de corrección | `ETIQUETAS_TIPO_CORRECCION` |
| Motivo | truncado con `title` completo |
| Total | `.right`, `{{ n.montoTotal \| moneda }}`, `mat-sort-header="montoTotal"` |
| Estado | `<span [class]="'tag ' + tagEstado(n.estado)">` |
| Recuperación de inventario | Sí / Parcial / No |
| Acciones | ver detalle, PDF, y editar solo si `estado === 'BORRADOR'` y hay permiso |

`<tr class="clickable-row" (click)="abrir(n)">` para ir al detalle.
`.empty-state` cuando no hay resultados, con un texto distinto si hay filtros activos.

## Paginación

```html
<mat-paginator [length]="total" [pageIndex]="paginaActual" [pageSize]="tamanoPagina"
               [pageSizeOptions]="[10, 25, 50]" showFirstLastButtons
               (page)="onPageChange($event)">
```

Server-side: `onPageChange` vuelve a llamar `cargarListado()`.

## Estado del componente

Propiedades públicas planas: `desde`, `hasta`, `estado`, `tipoCorreccion`,
`docAsociadoTipo`, `clienteId`, `busqueda`, `notas`, `total`, `paginaActual`,
`tamanoPagina`, `orden`, `direccion`, `cargando`, `error`.

Errores siempre como `err?.error?.error ?? 'No se pudo cargar el listado.'`, renderizados
en `<p class="page-error">`. Overlay bloqueante solo para acciones, no para el listado
(usar `cargando` y un estado vacío).

Helper local `formatoFecha(Date): 'yyyy-MM-dd'` (el proyecto lo duplica en cada módulo;
mantener la consistencia).

## Responsive

`.kpi-grid` y la zona de filtros con `grid-template-columns: repeat(auto-fit, minmax(...))`.
La tabla va dentro de un contenedor con `overflow-x: auto` para que en móvil scrollee ella
y no la página. Presupuesto SCSS: warning 3 kB, error 8 kB.

## Validación de la tarea

`notas-credito.component.spec.ts` sin `TestBed`, instanciando la clase con spies:

```ts
const notaCreditoService = {
  listar: jasmine.createSpy('listar').and.returnValue(of({ contenido: [], total: 0 })),
  dashboard: jasmine.createSpy('dashboard').and.returnValue(of(dashboardVacio)),
} as unknown as NotaCreditoService;
```

Casos: carga inicial llama a `listar` y `dashboard`; cambiar de filtro vuelve a la página 0;
la búsqueda hace debounce (`fakeAsync` + `tick`); `onPageChange` recarga con la página nueva.
