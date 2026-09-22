package cl.slimerp.notasventa;

// Acciones que quedan registradas en nota_venta_evento (historial append-only).
public enum AccionNotaVenta {
    CREADA,
    EDITADA,
    CONFIRMADA,
    PREPARADA,
    ENTREGA_REGISTRADA,
    ENTREGADA,
    FACTURADA,
    CANCELADA,
    DUPLICADA
}