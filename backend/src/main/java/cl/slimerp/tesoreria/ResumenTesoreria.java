package cl.slimerp.tesoreria;

import java.math.BigDecimal;

public record ResumenTesoreria(
        BigDecimal totalPorCobrar,
        BigDecimal totalCobrado,
        BigDecimal saldoPendiente,
        long cuentasEnDeuda,
        long cuentasParciales,
        long cuentasPagadas
) {
}
