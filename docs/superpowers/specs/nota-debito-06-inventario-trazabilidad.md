# ND-007 — Inventario y trazabilidad

## Objetivo

Al emitir la ND, reversar la recuperación de inventario que generó la NC asociada. Al
anular, reversar la salida propia de la ND. Mantener vínculo `nota_debito_movimiento` con
los movimientos y evitar revertir dos veces la misma cantidad.

## Cadena de trazabilidad

**Con inventario**: Venta → NC → Movimiento de inventario (ENTRADA_NOTA_CREDITO) → ND →
Movimiento de reversión (SALIDA_NOTA_CREDITO).

**Sin inventario**: si la NC no produjo movimientos (`recuperaInventario = false` en todas
sus líneas) o el tipo de reversión es `REVIERTE_TEXTO`, la ND **no** genera movimientos.

## Emisión (`emitir`)

1. Líneas a revertir: si `REVIERTE_TEXTO` → ninguna; si no → `detalle.stream().filter(revierteInventario)`.
2. Si hay líneas a revertir:
   - Exigir `bodegaId != null` (snapshot de la NC; si falta, la bodega principal del tenant).
   - `validarCantidadesDisponibles(nd, aRevertir, tenantId)` — valida **todo** antes de
     escribir el primer movimiento.
3. Crear cabecera `MovimientoInventarioHeader` tipo `SALIDA` con `bodegaOrigenId =
   nd.getBodegaId()` y observación `"Reversión de Nota de Crédito mediante ND-000001"`.
4. Por cada línea a revertir:
   - `stockService.sumar(tenantId, productoId, nd.getBodegaId(), -cantidad, TipoMovimiento.SALIDA_NOTA_CREDITO, headerId, nd.getId())`
     → devuelve el `MovimientoInventario` creado, `referenciaId = nd.getId()`.
   - Guardar `NotaDebitoMovimiento{... tipo=REVERSION, notaDebitoDetalleId=linea.getId()}`.
5. `EMITIDA`, `fechaEmision = now()`, save.
6. Evento `EMITIDA`.

**Nota sobre signos**: `MovimientoInventario.cantidad` guarda `cantidad.abs()`; el sentido
lo da el `tipo`, igual que `SalidaService`/`NotaCreditoService`.

## Anulación (`anular`)

1. `movimientoRepository.findByTenantIdAndNotaDebitoIdAndTipo(tenantId, id, REVERSION)`.
2. Si hay reversiones, header tipo `ENTRADA` con `bodegaDestinoId = nd.getBodegaId()` y
   observación `"Anulación de ND-000001"`.
3. Por cada reversión:
   - Cargar el `MovimientoInventario` original.
   - `stockService.sumar(tenantId, original.getProductoId(), original.getBodegaId(), original.getCantidad(), TipoMovimiento.ENTRADA_NOTA_DEBITO, headerId, nd.getId())`.
   - Guardar `NotaDebitoMovimiento{... tipo=REVERSA_ANULACION}`.
4. `ANULADA`, `fechaAnulacion = now()`, save. Las filas `REVERSION` **no** se borran; la
   `REVERSA_ANULACION` es una fila nueva (historial).
5. El disponible de la NC vuelve a subir automáticamente (se calcula dinámicamente: la ND
   ya no está EMITIDA y deja de sumar al monto revertido).

## Anti doble reversión

`validarCantidadesDisponibles(nd, aRevertir, tenantId)`:
- Carga la NC asociada con detalle + `cantidadesRevertidas(tenantId, ncId, nd.id())` (de
  `NotaDebitoDetalleRepository`, ver spec 03).
- Acumula lo solicitado por `notaCreditoDetalleId` en un `LinkedHashMap` con `merge`
  (evita burlar el control dividiendo una línea en varias).
- Para cada entrada: `solicitado + yaRevertido <= cantidadOriginalDeLaNC`, con mensaje claro
  con nombres de producto.

## Repositorios

- `NotaDebitoMovimientoRepository`:
  - `findByTenantIdAndNotaDebitoIdOrderByFechaAscIdAsc`
  - `findByTenantIdAndNotaDebitoIdAndTipo(tenantId, ndId, tipo)`
- `NotaDebitoDetalleRepository`:
  - `cantidadesRevertidas(tenantId, ncId)` y `cantidadesRevertidasExcluyendo(tenantId, ncId, ndId)`

## Validación

- Unit test: emitir con recuperación genera header SALIDA + movimientos con vínculo;
  anular los revierte con ENTRADA; NC sin recuperación → sin movimientos; doble reversión
  de una línea se rechaza.