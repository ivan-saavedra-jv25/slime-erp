package cl.slimerp.notascredito;

// Cuánto de la nota de crédito devuelve mercadería a bodega. Se muestra como una
// columna del listado, por eso son tres valores y no un boolean: al usuario le
// importa distinguir una devolución total de una parcial.
public enum RecuperacionInventario {
    NO,
    PARCIAL,
    TOTAL
}
