# ND-008 — Controller, listado y dashboard

## Objetivo

Exponer `/api/notas-debito` con listado filtrable/paginado y dashboard por período, espejo
de `NotaCreditoController`.

## Endpoints

| Método | Ruta | Permiso | Request → Response |
|---|---|---|---|
| GET | `/api/notas-debito` | `NOTAS_DEBITO_VER` | params opcionales `estado, clienteId, tipoReversion, notaCreditoId, desde, hasta (ISO DATE), q, sort, dir` + `pagina` (def 0) y `tamano` (def 10) → `PaginaResponse<NotaDebitoResumen>` |
| GET | `/api/notas-debito/dashboard` | `NOTAS_DEBITO_VER` | `desde` y `hasta` obligatorios → `DashboardNotasDebito` |
| GET | `/api/notas-debito/notas-credito-asociables` | `NOTAS_DEBITO_VER` | `clienteId`?, `q`? → `List<DocumentoNotaCreditoAsociable>` |
| GET | `/api/notas-debito/notas-credito/{notaCreditoId}/lineas` | `NOTAS_DEBITO_VER` | `excluyendoNotaDebitoId`? → `List<LineaNotaCreditoOriginal>` |
| GET | `/api/notas-debito/{id}` | `NOTAS_DEBITO_VER` | → `NotaDebitoCompleta` |
| POST | `/api/notas-debito` | `NOTAS_DEBITO_EDITAR` | `@Valid NotaDebitoRequest` → `NotaDebitoCompleta` |
| PUT | `/api/notas-debito/{id}` | `NOTAS_DEBITO_EDITAR` | `@Valid NotaDebitoRequest` → `NotaDebitoCompleta` |
| DELETE | `/api/notas-debito/{id}` | `NOTAS_DEBITO_EDITAR` | → `204` |
| POST | `/api/notas-debito/{id}/emitir` | `NOTAS_DEBITO_EDITAR` | sin body → `NotaDebitoCompleta` |
| POST | `/api/notas-debito/{id}/anular` | `NOTAS_DEBITO_EDITAR` | `@RequestBody(required=false) MotivoRequest` → `NotaDebitoCompleta` |
| GET | `/api/notas-debito/{id}/pdf` | `NOTAS_DEBITO_VER` | `obtenerEntidad(id)` → PDF |

## Request

`NotaDebitoRequest(@NotNull Long notaCreditoId, @NotNull TipoReversion tipoReversion,
@NotNull LocalDate fecha, @NotBlank String ncRazon, String motivo, String observaciones,
String textoCorreccion, BigDecimal descuento, @Valid List<Item> items)`.

`Item(@NotNull Long productoId, Long notaCreditoDetalleId, @NotNull BigDecimal cantidad,
@NotNull BigDecimal precioUnitario, BigDecimal descuento, boolean revierteInventario)`.

`items` sin `@NotEmpty` a propósito (REVIERTE_TEXTO va sin líneas); la validación de
negocio ocurre en el service. Helper `itemsOVacio()`.

## Listado (`buscar`)

`PaginaResponse<NotaDebitoResumen>` con:
- `id, folio, numero ("ND-000006"), estado, tipoReversion, fecha, clienteId, clienteNombre,
  clienteRut, ncNumero ("NC-000012"), ncFolio, motivo, montoTotal, impactoInventario`.

`ImpactoInventario` (NO/PARCIAL/TOTAL): espejo de `RecuperacionInventario` de la NC, con
los 3 valores según qué % de líneas con `revierteInventario` tiene la ND.

Filtros:
- `estado`, `clienteId`, `tipoReversion`, `notaCreditoId`, rango de fechas, y búsqueda
  `q` por folio propio, folio de la NC o nombre/RUT de cliente.
- Orden válido: `fecha | folio | montoTotal`, desempate por `folio`. Paginación server-side.

## Dashboard

`DashboardNotasDebito(desde, hasta, cantidad, borradores, emitidas, anuladas,
montoTotalEmitido, conReversionInventario, notasCreditoRevertidas, porEstado[], porTipoReversion[])`:
- `montoTotalEmitido`: suma de `monto_total` de NDs `EMITIDA` del período.
- `conReversionInventario`: NDs emitidas con ≥1 línea `revierteInventario`.
- `notasCreditoRevertidas`: nº distinto de `notaCreditoId` entre las emitidas.
- `porEstado` y `porTipoReversion`: `ConteoPorEstado`/`ConteoPorTipo` (espejo NC).
- Implementación: `findByTenantIdAndFechaBetweenOrderByFolioAsc` + `groupingBy`.

## Validación

- `NotaCreditoControllerPermissionTest` como plantilla: `@WebMvcTest` con
  `NOTAS_DEBITO_VER` (permite leer, 403 a escribir) y `NOTAS_DEBITO_EDITAR` (permite
  escribir).