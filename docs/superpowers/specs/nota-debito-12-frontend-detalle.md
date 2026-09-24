# ND-013 — Frontend — Detalle y trazabilidad

## Objetivo

Componente `NotaDebitoDetalleComponent` (selector `app-nota-debito-detalle`, standalone),
espejo de `NotaCreditoDetalleComponent`.

## Archivos

- `frontend/src/app/features/notas-debito/nota-debito-detalle.component.ts`
- `frontend/src/app/features/notas-debito/nota-debito-detalle.component.html`
- `frontend/src/app/features/notas-debito/nota-debito-detalle.component.scss`
- `frontend/src/app/features/notas-debito/nota-debito-detalle.component.spec.ts`
- `frontend/src/app/features/notas-debito/nota-credito-asociada-dialog.component.ts`
  (diálogo de la NC asociada, espejo de `DocumentoAsociadoDialogComponent`, con acceso al
  PDF de la NC).

## Contenido

- **Header**: `page-header` con título `ND-000006` + `tag` de estado; acciones según
  estado y permiso `NOTAS_DEBITO_EDITAR`:
  - BORRADOR: Editar, Emitir (con confirmación embebida), Eliminar (`ConfirmActionDialog`).
  - EMITIDA: Anular (con motivo obligatorio vía `ConfirmActionDialog`).
  - ANULADA: solo ver + PDF.
- **PDF**: botón que llama `obtenerPdf(id)` y abre `VentaPdfDialogComponent` (blob → URL
  → iframe).
- **Cadena de trazabilidad** (`cadena`): **Documento original (Venta) → Nota de Crédito →
  Nota de Débito**, con saltos clickeables cuando hay movimientos de inventario
  (Venta → NC → Movimiento de inventario → ND → Movimiento de reversión). Cada NC/Venta
  abre su detalle o PDF.
- **Fichas** (`ficha-grid`): documento asociado (NC-000012, tipo doc original, folio,
  fecha, monto, disponible, razón) y datos de la ND (fecha, cliente, motivo,
  observaciones, bodega, `textoCorreccion` si aplica).
- **Detalle/totales**: tabla de líneas (código, descripción, cantidad, precio, descuento,
  subtotal, "revierte inventario") + `totals-group` alineado a la derecha.
- **Movimientos de inventario**: tabla `movimientosInventario` (fecha, producto, tipo,
  cantidad, bodega) con fila clickeable → `MovimientoDetalleDialogComponent`.
- **Historial**: tabla `historial` (fecha, usuario, acción, estado anterior → nuevo,
  detalle).
- Iconos Sí/No con estilo `icono-si/no` para booleanos.

## Comportamiento

- Carga por `:id` con `obtener(id)`.
- Acciones → refresh del detalle + `mostrarCargando`/`cerrarCargando`.
- La NC asociada se puede consultar desde aquí (abre `nota-credito-asociada-dialog` con
  los datos ya cargados de `documentoAsociado`).
- Errores en `page-error`.

## Validación

- `ng test --include=**/nota-debito-detalle.component.spec.ts` pasa (spec espejo: carga,
  acciones por estado, confirmación de emisión, anulación con motivo, eliminar, diálogos,
  errores).