package cl.slimerp.inventario;

public enum TipoMovimiento {
    ENTRADA,
    SALIDA,
    TRASLADO,
    AJUSTE,
    SALIDA_VENTA,
    ENTRADA_COMPRA,
    // Nota de crédito: la entrada al emitirla y la salida que la revierte al
    // anularla. Superan los 20 caracteres de la columna original, por eso
    // V37__notas_credito.sql la ensancha a VARCHAR(30).
    ENTRADA_NOTA_CREDITO,
    SALIDA_ANULA_NOTA_CREDITO,
    // Nota de débito: la salida al emitirla (revierte la ENTRADA_NOTA_CREDITO de
    // la NC asociada) y la entrada que la revierte al anularla.
    SALIDA_NOTA_CREDITO,
    ENTRADA_NOTA_DEBITO
}
