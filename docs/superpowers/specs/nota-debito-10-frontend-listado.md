# ND-011 — Frontend — Listado

## Objetivo

Componente `NotasDebitoComponent` (selector `app-notas-debito`, standalone), espejo de
`NotasCreditoComponent`.

## Archivos

- `frontend/src/app/features/notas-debito/notas-debito.component.ts`
- `frontend/src/app/features/notas-debito/notas-debito.component.html`
- `frontend/src/app/features/notas-debito/notas-debito.component.scss`
- `frontend/src/app/features/notas-debito/notas-debito.component.spec.ts`

## Contenido

- **Header**: título "Notas de Débito" + botón "Nueva nota de débito" (gated por
  `NOTAS_DEBITO_EDITAR`).
- **Selector de período**: 5 rangos (hoy, semana, mes, quarter, año), igual que NC.
- **KPI grid** (6 tarjetas): cantidad, borradores, emitidas, anuladas, monto total
  emitido, notas de crédito revertidas.
- **Gráfico**: Highcharts de columnas por estado, con colores de tokens CSS
  (`--text-muted`, `--gray-400`, `--success-base`, `--error-base`).
- **Filtros** (mat-card): estado, tipo de reversión, cliente (input), folio NC, búsqueda
  `q` con debounce 300ms, rango de fechas y botón "Limpiar filtros".
- **Tabla** `libro-table` (10 columnas): Nº (`ND-000006`), fecha, cliente, tipo de
  documento de la venta original, folio NC, tipo de reversión, motivo, total, estado
  (`tag`), impacto de inventario (`tag`). Filas clickables → detalle. Botón editar
  (BORRADOR + permiso).
- **Paginación**: `mat-paginator` con options `[10,25,50]`, server-side.
- **Estados**: `page-error` arriba, `empty-state` para cargando/vacío, `field-error` en
  filtros.
- Ordenamiento `matSort` por `fecha | folio | montoTotal`.

Reutilizar: `MonedaPipe`, `mostrarCargando`/`cerrarCargando`, clases globales de
`_components.scss` (`page-container`, `page-header`, `form-row--filtros`, `libro-table`,
`clickable-row`, `truncate`). Responsive 1100/900/720/560 px.

## Validación

- `ng test --include=**/notas-debito.component.spec.ts` pasa (spec espejo: carga,
  filtros, debounce, validación de rango, paginación, limpiar filtros, navegación,
  errores).