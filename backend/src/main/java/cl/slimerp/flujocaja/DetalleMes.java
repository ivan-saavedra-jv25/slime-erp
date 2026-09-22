package cl.slimerp.flujocaja;

import java.math.BigDecimal;
import java.util.List;

public record DetalleMes(
        String mes,
        BigDecimal ingresos,
        BigDecimal compras,
        BigDecimal gastos,
        BigDecimal resultado,
        BigDecimal saldo,
        List<LineaIngreso> ingresosDetalle,
        List<LineaCompra> comprasDetalle,
        List<GastoCategoriaDetalle> gastosPorCategoria) {
}