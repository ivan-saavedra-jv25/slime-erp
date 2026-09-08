package cl.slimerp.ventas;

// Clasificación interna del documento de venta. Junto con el flag "exento" de
// Venta, determina cómo se interpretan los montos de la venta y (vía
// CodigoSiiVenta) a qué código de documento tributario del SII equivale y qué
// folio le corresponde — pero no implica integración real con el SII (sin
// XML, CAF ni envío), solo referencia el código y numera internamente:
//   - FACTURA: los montos de detalle van en NETO; el IVA se calcula y se suma.
//   - BOLETA: los montos de detalle van en BRUTO (IVA incluido); el total se
//     desglosa en neto + IVA a partir del bruto.
//   - VOUCHER: documento no fiscal, sin IVA. El campo "exento" en Venta solo
//     clasifica si es una venta interna o una venta exenta; no cambia el cálculo.
public enum TipoDocumentoVenta {
    BOLETA,
    FACTURA,
    VOUCHER
}
