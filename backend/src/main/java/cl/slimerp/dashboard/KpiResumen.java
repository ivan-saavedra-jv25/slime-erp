package cl.slimerp.dashboard;

import java.math.BigDecimal;

public record KpiResumen(
        BigDecimal ventasPeriodo,
        BigDecimal ventasPeriodoAnterior,
        Double variacionPct,
        BigDecimal comprasPeriodo,
        long documentosEmitidos,
        long totalClientes,
        long totalProductos,
        BigDecimal stockDisponible,
        long productosStockBajo,
        long productosSinStock,
        BigDecimal cuentasPorCobrarSaldo,
        long cuentasPorCobrarPendientes
) {
}
