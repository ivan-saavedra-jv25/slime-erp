package cl.slimerp.notascredito;

// Única fuente de verdad del número visible de una nota de crédito: el folio es
// un entero correlativo en la base de datos y el prefijo es presentación, así
// que pantalla, PDF y Excel deben formatearlo todos por acá.
public final class NumeroNotaCredito {

    private static final String PREFIJO = "NC-";

    private NumeroNotaCredito() {
    }

    public static String formatear(Integer folio) {
        return folio == null ? null : PREFIJO + String.format("%06d", folio);
    }

    // Interpreta una búsqueda libre como folio: acepta "NC-000012", "nc-12" o
    // "12". Devuelve null si el texto no representa un folio.
    public static Integer parsearFolio(String texto) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.trim();
        if (limpio.regionMatches(true, 0, PREFIJO, 0, PREFIJO.length())) {
            limpio = limpio.substring(PREFIJO.length());
        }
        try {
            return Integer.valueOf(limpio.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
