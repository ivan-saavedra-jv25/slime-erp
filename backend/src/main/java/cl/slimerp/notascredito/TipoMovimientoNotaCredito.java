package cl.slimerp.notascredito;

// Rol de cada movimiento de inventario dentro de la vida de la nota de crédito:
//   - RECUPERACION: la entrada generada al emitir.
//   - REVERSA_ANULACION: la salida que la deshace al anular.
// Las filas de RECUPERACION no se borran al anular; el historial de inventario
// es append-only, así que la reversa se agrega como una fila nueva.
public enum TipoMovimientoNotaCredito {
    RECUPERACION,
    REVERSA_ANULACION
}
