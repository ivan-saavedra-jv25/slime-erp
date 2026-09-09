package cl.slimerp.admin.cobranza;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CobranzaResponse(
        Long id,
        Long tenantId,
        String concepto,
        String periodo,
        BigDecimal montoTotal,
        BigDecimal montoPagado,
        BigDecimal saldoPendiente,
        EstadoCobranza estado,
        LocalDateTime fechaEmision,
        LocalDate fechaVencimiento,
        LocalDateTime fechaUltimoPago,
        String observaciones,
        LocalDateTime fechaAnulacion,
        String motivoAnulacion
) {
    public static CobranzaResponse desde(CobranzaEmpresa c) {
        return new CobranzaResponse(
                c.getId(), c.getTenantId(), c.getConcepto(), c.getPeriodo(),
                c.getMontoTotal(), c.getMontoPagado(), c.getSaldoPendiente(), c.getEstado(),
                c.getFechaEmision(), c.getFechaVencimiento(), c.getFechaUltimoPago(),
                c.getObservaciones(), c.getFechaAnulacion(), c.getMotivoAnulacion());
    }
}