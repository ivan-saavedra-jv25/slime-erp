package cl.slimerp.ventas;

import java.util.List;

// Traduce la clasificación interna (TipoDocumentoVenta + exento) al código de
// documento tributario del SII. Único punto de esta traducción en el
// proyecto — VentaService, VentaPdfService y LibroVentasService la reutilizan
// en vez de repetirla. No implica integración real con el SII (sin XML, CAF
// ni envío): solo referencia el código y sirve de clave para el folio.
public final class CodigoSiiVenta {

    private CodigoSiiVenta() {
    }

    // 33 Factura Electrónica, 34 Factura Exenta, 39 Boleta Electrónica,
    // 41 Boleta Exenta. Voucher no es un documento tributario real: null.
    public static Integer codigo(TipoDocumentoVenta tipo, boolean exento) {
        return switch (tipo) {
            case FACTURA -> exento ? 34 : 33;
            case BOLETA -> exento ? 41 : 39;
            case VOUCHER -> null;
        };
    }

    // Etiqueta legible, usada también como clave del contador de folios y en
    // el Libro de Ventas. Orden fijo Factura -> Factura Exenta -> Boleta ->
    // Boleta Exenta -> Voucher (no alfabético) para que el libro se lea
    // siempre igual aunque un tipo no tenga ventas en el período.
    public static final List<String> ORDEN_ETIQUETAS =
            List.of("Factura", "Factura Exenta", "Boleta", "Boleta Exenta", "Voucher");

    public static String etiqueta(TipoDocumentoVenta tipo, boolean exento) {
        return switch (tipo) {
            case FACTURA -> exento ? "Factura Exenta" : "Factura";
            case BOLETA -> exento ? "Boleta Exenta" : "Boleta";
            case VOUCHER -> "Voucher";
        };
    }
}
