package cl.slimerp.flujocaja;

import java.math.BigDecimal;
import java.util.List;

public record MesResumen(
        String mes,
        BigDecimal ingresos,
        BigDecimal compras,
        BigDecimal gastos,
        BigDecimal resultado,
        BigDecimal saldo,
        List<GastoCategoriaTotal> gastosPorCategoria) {
}