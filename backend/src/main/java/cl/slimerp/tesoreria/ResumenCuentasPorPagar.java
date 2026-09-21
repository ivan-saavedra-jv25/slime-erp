package cl.slimerp.tesoreria;

import java.math.BigDecimal;

public record ResumenCuentasPorPagar(
        BigDecimal totalPorPagar,
        BigDecimal totalPagado,
        BigDecimal saldoPendiente,
        long cuentasEnDeuda,
        long cuentasParciales,
        long cuentasPagadas
) {
}
