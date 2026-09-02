package cl.slimerp.ventas;

// Clasificación interna del documento de venta — no tiene relación con la
// facturación electrónica (folios SII, CAF, etc.), solo determina cómo se
// interpretan los montos de la venta:
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
