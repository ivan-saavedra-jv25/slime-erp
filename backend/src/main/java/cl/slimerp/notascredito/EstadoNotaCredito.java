package cl.slimerp.notascredito;

// Ciclo de vida de una nota de crédito. Las transiciones válidas se validan en
// NotaCreditoService; ANULADA es terminal.
//   - BORRADOR: permite modificar la información, sin efectos sobre inventario.
//   - EMITIDA: bloquea las modificaciones y aplica los efectos sobre la
//     operación e inventario.
//   - ANULADA: revierte los efectos sobre inventario cuando corresponde.
public enum EstadoNotaCredito {
    BORRADOR,
    EMITIDA,
    ANULADA
}
