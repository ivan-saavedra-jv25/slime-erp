package cl.slimerp.notasventa;

// Única fuente de verdad del número visible de una nota de venta: el folio es
// un entero correlativo en la base de datos y el prefijo es presentación, así
// que pantalla, PDF y Excel deben formatearlo todos por acá.
public final class NumeroNotaVenta {

    private static final String PREFIJO = "NV-";

    private NumeroNotaVenta() {
    }

    public static String formatear(Integer folio) {
        return folio == null ? null : PREFIJO + String.format("%06d", folio);
    }
}