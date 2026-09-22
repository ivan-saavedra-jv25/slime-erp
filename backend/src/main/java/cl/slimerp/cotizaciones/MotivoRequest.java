package cl.slimerp.cotizaciones;

// Motivo opcional al rechazar o cancelar una cotización: a diferencia de una
// anulación en tesorería, aquí el motivo lo suele dar el cliente y puede no
// estar disponible.
public record MotivoRequest(String motivo) {
}
