package cl.slimerp.admin.cobranza;

import java.math.BigDecimal;

public record ResumenCobranza(
        BigDecimal totalEmitido,
        BigDecimal totalCobrado,
        BigDecimal saldoPendiente,
        long cargosEnDeuda,
        long cargosParciales,
        long cargosPagados
) {}