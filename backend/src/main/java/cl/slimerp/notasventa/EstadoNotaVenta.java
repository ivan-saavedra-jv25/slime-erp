package cl.slimerp.notasventa;

// Ciclo de vida de una nota de venta. Las transiciones válidas se validan en
// NotaVentaService; CANCELADA y FACTURADA son terminales. El estado de entrega
// (PARCIALMENTE ENTREGADA / ENTREGADA) se deriva de las cantidades entregadas.
public enum EstadoNotaVenta {
    BORRADOR,
    CONFIRMADA,
    EN_PREPARACION,
    PARCIALMENTE_ENTREGADA,
    ENTREGADA,
    FACTURADA,
    CANCELADA
}