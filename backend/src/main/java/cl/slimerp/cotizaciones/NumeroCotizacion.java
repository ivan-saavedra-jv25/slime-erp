package cl.slimerp.cotizaciones;

// Única fuente de verdad del número visible de una cotización: el folio es un
// entero correlativo en la base de datos y el prefijo es presentación, así que
// pantalla, PDF y Excel deben formatearlo todos por acá.
public final class NumeroCotizacion {

    private static final String PREFIJO = "COT-";

    private NumeroCotizacion() {
    }

    public static String formatear(Integer folio) {
        return folio == null ? null : PREFIJO + String.format("%06d", folio);
    }
}
