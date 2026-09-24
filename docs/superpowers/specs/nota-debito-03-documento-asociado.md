# ND-004 — Documento asociado

## Objetivo

Permitir seleccionar la **Nota de Crédito** que la ND revertirá y mantener un snapshot
histórico de sus datos: tipo de documento original, folio, fecha y razón, más el monto
total para controlar la validación "no superar el disponible".

## Reglas

- Solo se pueden asociar NCs en estado `EMITIDA`. Un borrador no es documento vigente y
  una anulada no tiene efectos que revertir.
- La NC asociada debe pertenecer al mismo tenant.
- La ND copia de la NC: `cliente_id`, `bodega_id`, `exenta`, `moneda`, y el snapshot
  `nc_doc_asociado_tipo` (tipo de documento de la venta original), `nc_folio`, `nc_fecha`,
  `nc_monto_total`.
- El "disponible" de la NC **se calcula dinámicamente** en el service (nunca se persiste en
  la cabecera): `disponible = nc_monto_total − Σ monto_total de las NDs EMITIDA que apuntan
  a la misma NC (excluyendo la ND propia en edición)`. Así no hay que mantener cachés
  sincronizadas entre documentos.

## Endpoints

### `GET /api/notas-debito/notas-credito-asociables?clienteId=&q=`

Devuelve las NCs `EMITIDA` de pago. Similar a `documentosAsociables` de la NC, con registro
`DocumentoNotaCreditoAsociable`:
- `notaCreditoId`, `numero` (`NC-000012`), `folio`, `fecha`, `clienteId`, `clienteNombre`,
  `clienteRut`, `docAsociadoTipo` (de la venta original), `docAsociadoFolio`, `montoTotal`,
  `montoDisponible` (= `montoTotal` − `nc_monto_revertido`), `tieneNotasDebito` (bool).

El `q` busca por número (`NC-000012`), folio de NC, o nombre/RUT de cliente. Cada NC puede
ser revertida por **varias** NDs (reversión parcial), por lo que se listan todas las
EMITIDA, indicando el disponible.

### `GET /api/notas-debito/notas-credito/{notaCreditoId}/lineas?excluyendoNotaDebitoId=`

Devuelve las líneas de la NC con la cantidad disponible a revertir. Registro
`LineaNotaCreditoOriginal`:
- `notaCreditoDetalleId`, `productoId`, `codigo`, `descripcion`, `cantidad` (de la NC),
  `cantidadRevertida`, `cantidadDisponible` (= `cantidad` − `cantidadRevertida`, mín 0),
  `precioUnitario`, `subtotal`.

Solo se exponen líneas de la NC con `recuperaInventario = true` para REVIERTE_DOCUMENTO /
REVIERTE_MONTO; para REVIERTE_TEXTO el formulario no usa líneas.

## Query anti doble reversión

En `NotaDebitoDetalleRepository`, una consulta JPQL constructora que suma la cantidad
revertida por `nota_credito_detalle_id` solo de NDs `EMITIDA` con `revierte_inventario =
true` y `nota_credito_detalle_id IS NOT NULL`:

```java
@Query("""
    select new cl.slimerp.notasdebito.CantidadRevertida(d.notaCreditoDetalleId,
           coalesce(sum(d.cantidad), 0))
    from NotaDebitoDetalle d
    join NotaDebito nd on d.notaDebito.id = nd.id
    where nd.tenantId = :tenantId and nd.estado = cl.slimerp.notasdebito.EstadoNotaDebito.EMITIDA
      and d.revierteInventario = true and d.notaCreditoDetalleId is not null
      and nd.notaCreditoId = :notaCreditoId
    group by d.notaCreditoDetalleId
    """)
List<CantidadRevertida> cantidadesRevertidas(...);
```

Y una variante con `nd.id <> :excluyendoId` para la edición. Proyección top-level
`CantidadRevertida(Long notaCreditoDetalleId, BigDecimal cantidad)`.

## Servicio

En `NotaDebitoService`:
- `notasCreditoAsociables(Long clienteId, String q)`: Specification sobre `NotaCredito`
  con `estado = EMITIDA` + tenant (+ cliente/q), `PageRequest.of(0, 20, Sort DESC fecha)`.
- `lineasNotaCredito(Long notaCreditoId, Long excluyendoNotaDebitoId)`: carga la NC con su
  detalle (`@EntityGraph`), calcula `cantidadesRevertidas` (con la exclusión), resuelve
  productos en lote y arma el registro.
- Guardia: si la NC no existe, es de otro tenant, o su estado no es `EMITIDA`, lanzar
  `IllegalArgumentException` con mensaje claro.

## DTOs de respuesta (records anidados en el service)

- `DocumentoNotaCreditoAsociable(notaCreditoId, numero, folio, fecha, clienteId,
  clienteNombre, clienteRut, docAsociadoTipo, docAsociadoFolio, montoTotal,
  montoDisponible, tieneNotasDebito)`
- `LineaNotaCreditoOriginal(notaCreditoDetalleId, productoId, codigo, descripcion,
  cantidad, cantidadRevertida, cantidadDisponible, precioUnitario, subtotal)`

## Validación

- Unit test: asociar a una NC BORRADOR/ANULADA se rechaza; el disponNote se calcula bien
  con varias NDs emitidas; `q` filtra por NC-000012 y por cliente.