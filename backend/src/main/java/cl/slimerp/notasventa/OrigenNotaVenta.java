package cl.slimerp.notasventa;

// Procedencia de una nota de venta: creada a partir de una cotización aceptada
// (COTIZACION, con cotizacion_id) o directamente sin documento previo
// (VENTA_DIRECTA). Se guarda como texto en la base.
public enum OrigenNotaVenta {
    COTIZACION,
    VENTA_DIRECTA
}