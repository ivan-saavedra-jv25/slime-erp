# NC-004 — Documento asociado

## Objetivo

Toda Nota de Crédito debe estar asociada a un documento existente. El usuario selecciona
el documento a corregir y su información se usa como referencia para generar la NC.

## Alcance del documento asociado

**Solo `Venta`** (`cl.slimerp.ventas.Venta`), con `tipoDocumento` ∈ `{BOLETA, FACTURA,
VOUCHER}`. Es el único documento del ERP que descuenta stock, y por lo tanto el único
donde la recuperación de inventario tiene sentido. Cotizaciones y Notas de Venta quedan
fuera de alcance.

## Datos de la asociación

La especificación pide tipo, folio, fecha y razón. Se guardan como **FK dura + snapshot**:

| Campo | Origen |
|---|---|
| `venta_id` | FK a `venta(id)` — permite navegar al documento vivo |
| `doc_asociado_tipo` | `venta.tipoDocumento` al momento de crear |
| `doc_asociado_folio` | `venta.folio` |
| `doc_asociado_fecha` | `venta.fecha` (solo la fecha) |
| `doc_asociado_razon` | ingresado por el usuario |

El snapshot existe porque la NC es un documento histórico: aunque la venta cambie, la NC
debe seguir mostrando lo que se corrigió.

Además se copian de la venta al crear la NC:

- `cliente_id` — la NC siempre es del mismo cliente que el documento corregido.
- `bodega_id` — la mercadería vuelve a la bodega desde la que salió.
- `exenta` — para que el cálculo de montos use la misma base impositiva.

## Selección del documento

### `GET /api/notas-credito/documentos-asociables?clienteId&q`

Devuelve las ventas candidatas del tenant, ordenadas por fecha descendente y limitadas
(20 resultados). Filtros:

- `clienteId` — obligatorio en la práctica; el formulario elige primero el cliente.
- `q` — búsqueda libre por folio (acepta `1042` o el número formateado).
- Solo ventas con `activo = true`.

Respuesta (record anidado en el service):

```java
public record DocumentoAsociable(
        Long ventaId, TipoDocumentoVenta tipoDocumento, Integer folio, String numero,
        LocalDate fecha, BigDecimal montoTotal, boolean exento, Long bodegaId,
        boolean tieneNotasCredito) { }
```

`tieneNotasCredito` avisa en la UI que ese documento ya fue corregido antes.

Usar `VentaRepository.findByIdAndTenantIdAndActivoTrue` para la lectura puntual y una
`Specification` para el listado, siguiendo el patrón del resto del módulo.

### `GET /api/notas-credito/ventas/{ventaId}/lineas`

Devuelve las líneas del documento original **con la cantidad todavía disponible para
recuperar**. Es la fuente del detalle del formulario.

```java
public record LineaDocumentoOriginal(
        Long ventaDetalleId, Long productoId, String codigo, String descripcion,
        BigDecimal cantidad,            // la vendida
        BigDecimal cantidadRecuperada,  // ya recuperada por NC EMITIDAS
        BigDecimal cantidadDisponible,  // cantidad - cantidadRecuperada
        BigDecimal precioUnitario, BigDecimal descuento, BigDecimal subtotal) { }
```

`codigo` y `descripcion` se resuelven desde `ProductoRepository` (el `venta_detalle` no
guarda snapshot del nombre). El cálculo de `cantidadRecuperada` se especifica en
`06-productos-inventario.md`.

Acepta `?excluyendoNotaCreditoId=` para que, al **editar** un borrador, sus propias líneas
no se cuenten como ya recuperadas.

## Validaciones

- La venta debe existir, pertenecer al tenant y estar activa → si no,
  `IllegalArgumentException("Documento asociado no encontrado")`.
- `venta_id` es obligatorio al crear; no se puede cambiar una vez creada la NC
  (cambiar el documento corregido es crear otra NC).
- El cliente de la NC se deriva de la venta; si el request trae uno distinto, gana el de
  la venta.
- `doc_asociado_razon` es obligatoria: es la "razón o motivo de la asociación" que pide la
  especificación.

## Trazabilidad

La dirección **Documento original → Nota de Crédito** queda resuelta con el índice
`idx_nota_credito_venta ON nota_credito(tenant_id, venta_id)`: consultar qué NC corrigen
una venta es una consulta directa. La cadena completa se arma en `09-trazabilidad`
(spec 12, frontend) y en el detalle de la NC.

## Validación de la tarea

- `GET /api/notas-credito/documentos-asociables?clienteId=X` devuelve solo ventas activas
  de ese cliente y del tenant en sesión.
- `GET /api/notas-credito/ventas/{id}/lineas` devuelve `cantidadDisponible == cantidad`
  cuando no hay NC emitidas sobre esa venta.
- Pedir una venta de otro tenant responde 400, no 200 con datos ajenos.
