# ND-005 — Tipo de reversión y montos

## Objetivo

Aplicar las reglas de los tres tipos de reversión y calcular los montos reutilizando
`CalculadoraMontosVenta` con el tipo de documento y exención de la **venta original**
(heredados de la NC asociada).

## Tipos de reversión (`TipoReversion`)

| Valor | Comportamiento |
|---|---|
| `REVIERTE_DOCUMENTO` | Revierte la NC asociada en su totalidad: precarga todas sus líneas con `revierteInventario = true`. El monto a revertir puede ser hasta el total disponible de la NC. |
| `REVIERTE_MONTO` | Reversión total o parcial: usa las líneas que traiga el usuario. La suma a revertir no puede superar el monto disponible de la NC. |
| `REVIERTE_TEXTO` | Corrección textual: no admite líneas; exige `textoCorreccion` no vacío; montos en cero. No genera movimientos de inventario. |

## Reglas por línea

- `notaCreditoDetalleId` debe pertenecer a la NC asociada.
- Si la línea trae `notaCreditoDetalleId` y la línea original de la NC tenía
  `recuperaInventario = true`, puede `revierteInventario = true`.
- Sin `notaCreditoDetalleId` la línea no puede `revierteInventario = true`.
- `cantidad > 0`; `precioUnitario >= 0`; descuento de línea `>= 0` y `<=` subtotal bruto.
- La suma de cantidades revertidas por `notaCreditoDetalleId` (incluyendo la ND propia en
  edición) **no puede superar** la cantidad de la línea original de la NC
  (validación anti doble reversión, detallada en spec 06).

## Cálculo de montos

Mismo patrón que `NotaCreditoService.aplicarLineasYMontos`:

- `subtotalLinea = precioUnitario * cantidad - descuentoLinea`
- `montoSubtotal` = Σ subtotales brutos (sin descontar)
- `montoDescuento` = Σ descuentos de línea + descuento global (validado `>= 0` y
  `<=` Σ subtotales)
- `CalculadoraMontosVenta.calcular(nc.getDocAsociadoTipo(), nc.isExenta(), sumaSubtotal - descuentoGlobal)`
  → `montoNeto` / `montoIva` / `montoTotal`.

**Importante**: el `doc_asociado_tipo` y `exenta` usados son los de la **venta original**
(copiados a la NC y de ahí a la ND en `nc_doc_asociado_tipo` / `exenta`), no valores fijos:
una ND sobre una NC de boleta cuadra en bruto; sobre factura en neto.

### Control del monto disponible

En la emisión (método dedicado), validar que el `montoTotal` calculado de la ND no supere
`ncMontoTotal - montoRevertido`, donde `montoRevertido` suma los `monto_total` de las NDs
`EMITIDA` sobre la misma NC (excluyendo la ND propia en edición). Para
`REVIERTE_DOCUMENTO` se permite igualar el disponible; para `REVIERTE_TEXTO` no aplica.
El disponible siempre se calcula dinámicamente, nunca desde una columna cacheada.

## Validación

- Unit test: cada tipo aplica su set de reglas; montos sobre boleta (bruto) y factura
  (neto) cuadran; revertir más del disponible se rechaza; `REVIERTE_TEXTO` no acepta
  líneas y exige texto.