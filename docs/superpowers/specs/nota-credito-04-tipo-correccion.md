# NC-005 — Tipo de corrección y cálculo de montos

## Objetivo

La Nota de Crédito debe exigir uno de tres tipos de corrección, y ese tipo determina su
comportamiento funcional.

## Enum

```java
public enum TipoCorreccion {
    CORRIGE_DOCUMENTO,
    CORRIGE_MONTO,
    CORRIGE_TEXTO
}
```

Obligatorio en `NotaCreditoRequest` (`@NotNull`) y `NOT NULL` en la tabla.

## Comportamiento por tipo

| | **CORRIGE_DOCUMENTO** | **CORRIGE_MONTO** | **CORRIGE_TEXTO** |
|---|---|---|---|
| Uso | reemplazar o corregir el documento completo | modificar total o parcialmente los valores de la operación | corregir información textual |
| Líneas | obligatorias; se precargan con **todas** las del documento original | obligatorias; subconjunto editable en cantidad, precio y descuento | **prohibidas** — se rechaza el request si vienen |
| Montos | los del documento original | calculados desde el detalle | todos en 0 |
| Inventario | permitido; `recuperaInventario` por defecto `true` en todas las líneas | permitido; opt-in por línea | nunca |
| `textoCorreccion` | ignorado | ignorado | **obligatorio** |

### CORRIGE_DOCUMENTO

Es la anulación total. Al crear la NC con este tipo, el service precarga el detalle con
todas las líneas de `venta_detalle` (cantidad, precio y descuento tal cual), con
`recuperaInventario = true` y `ventaDetalleId` apuntando a la línea original. El usuario
puede desmarcar la recuperación de una línea (p. ej. mercadería que no volvió), pero no
quitar líneas: si necesita corregir solo una parte, el tipo correcto es CORRIGE_MONTO.

### CORRIGE_MONTO

El usuario elige qué líneas del documento corregir y con qué cantidad, precio y descuento.
Puede agregar líneas que no estaban en el documento original (`ventaDetalleId = null`),
pero esas **no pueden recuperar inventario**: no hay cantidad vendida contra la cual
validar.

### CORRIGE_TEXTO

No mueve dinero ni inventario: corrige glosa, giro, dirección, descripción. Se guarda en
`texto_correccion` (VARCHAR 2000). Todos los montos quedan en 0 y el detalle vacío.

## Validación (en el Service, no en Bean Validation)

Bean Validation solo cubre presencia. Las reglas de negocio se validan a mano y lanzan
`IllegalArgumentException` con mensaje en español orientado al usuario —
`GlobalExceptionHandler` la mapea a 400. **No usar `IllegalStateException`: está mapeada
a 401.**

```
CORRIGE_TEXTO + items no vacío
  → "Una nota de crédito que corrige texto no puede tener líneas de detalle."
CORRIGE_TEXTO + textoCorreccion en blanco
  → "Debe indicar el texto de la corrección."
CORRIGE_DOCUMENTO | CORRIGE_MONTO + items vacío
  → "Debe indicar al menos una línea a corregir."
línea con recuperaInventario=true y ventaDetalleId=null
  → "La línea \"<descripcion>\" no pertenece al documento original y no puede recuperar inventario."
```

Por línea (mismo estilo que `NotaVentaService.aplicarLineasYMontos`):

- `cantidad > 0` → *"La cantidad debe ser mayor que 0."*
- `precioUnitario >= 0`
- `descuento >= 0` y `descuento <= cantidad * precioUnitario` →
  *"El descuento no puede superar el subtotal de la línea."*
- producto existente, activo y del tenant.
- `ventaDetalleId`, si viene, debe pertenecer a la venta asociada →
  *"La línea no pertenece al documento asociado."*

Descuento global: `>= 0` y `<= suma de subtotales de línea`.

## Cálculo de montos

Reutilizar **`cl.slimerp.ventas.CalculadoraMontosVenta`** — única fuente de verdad de
neto/IVA/total en el ERP (`TASA_IVA = 0.19`, escala 2, `HALF_UP`, invariante
`neto + iva == total`).

```java
BigDecimal sumaDetalle = /* suma de subtotales de línea, ya con descuento de línea */;
CalculadoraMontosVenta.Montos montos = CalculadoraMontosVenta.calcular(
        nc.getDocAsociadoTipo(),   // tipoDocumento de la VENTA original
        nc.isExenta(),             // exento de la VENTA original
        sumaDetalle.subtract(descuentoGlobal));
```

Se pasa el tipo de documento **de la venta**, no un valor fijo, para que la NC use la
misma base impositiva que el documento que corrige:

- FACTURA afecta → los montos de detalle son netos, el IVA se suma.
- BOLETA afecta → los montos de detalle son brutos, el total se desglosa.
- FACTURA/BOLETA exenta y VOUCHER → sin IVA.

Esto es lo que hace que una NC sobre una boleta cuadre con la boleta.

Campos resultantes:

| Campo | Valor |
|---|---|
| `monto_subtotal` | suma de `cantidad * precioUnitario` de las líneas, sin descuentos |
| `monto_descuento` | suma de descuentos de línea + descuento global |
| `monto_neto` | `montos.neto()` |
| `monto_iva` | `montos.iva()` |
| `monto_total` | `montos.total()` |

Todos los montos se guardan **positivos**. El signo semántico lo da el tipo de documento:
una Nota de Crédito, por definición, resta.

## Cambio de tipo

Mientras la NC está en BORRADOR se puede cambiar el tipo de corrección. Al hacerlo, el
service reaplica las reglas del tipo nuevo: pasar a CORRIGE_TEXTO borra el detalle y pone
los montos en 0; pasar a CORRIGE_DOCUMENTO recarga todas las líneas del documento
original. Una vez EMITIDA, el tipo no cambia.

## Validación de la tarea

Tests en `NotaCreditoServiceTest`:

- CORRIGE_TEXTO con líneas → `IllegalArgumentException`.
- CORRIGE_TEXTO sin `textoCorreccion` → `IllegalArgumentException`.
- CORRIGE_MONTO sin líneas → `IllegalArgumentException`.
- CORRIGE_DOCUMENTO precarga tantas líneas como tiene `venta_detalle`, todas con
  `recuperaInventario = true`.
- NC sobre una FACTURA de $100.000 neto → `montoIva = 19.000`, `montoTotal = 119.000`.
- NC sobre una BOLETA de $119.000 bruto → `montoNeto = 100.000`, `montoIva = 19.000`,
  `montoTotal = 119.000`.
- Línea con `recuperaInventario = true` y sin `ventaDetalleId` → `IllegalArgumentException`.
