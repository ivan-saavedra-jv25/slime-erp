package cl.slimerp.admin.pago;

import cl.slimerp.admin.cobranza.CobranzaPago;
import cl.slimerp.admin.cobranza.EstadoPagoCobranza;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Pago consolidado de la plataforma (spec §12, sobre cobranza). */
public record PagoResponse(
        Long id,
        Long cobranzaEmpresaId,
        Long companyId,
        String companyNombre,
        Long suscripcionId,
        BigDecimal monto,
        String metodo,
        String estado,
        String referencia,
        LocalDateTime fecha,
        String adminNombre) {

    public static PagoResponse desde(CobranzaPago pago, String companyNombre, String adminNombre) {
        return new PagoResponse(
                pago.getId(),
                pago.getCobranzaEmpresaId(),
                pago.getTenantId(),
                companyNombre,
                null,
                pago.getMonto(),
                pago.getMedioPago().name(),
                estadoEspec(pago.getEstado()),
                pago.getNumeroOperacion(),
                pago.getFecha(),
                adminNombre);
    }

    public static String estadoEspec(EstadoPagoCobranza estado) {
        return switch (estado) {
            case CONFIRMADA -> "PAID";
            case ANULADA -> "CANCELLED";
        };
    }
}