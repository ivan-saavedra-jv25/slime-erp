package cl.slimerp.cotizaciones;

// Ciclo de vida de una cotización. Las transiciones válidas se validan en
// CotizacionService; ACEPTADA, RECHAZADA, VENCIDA y CANCELADA son terminales.
public enum EstadoCotizacion {
    BORRADOR,
    ENVIADA,
    ACEPTADA,
    RECHAZADA,
    VENCIDA,
    CANCELADA
}
