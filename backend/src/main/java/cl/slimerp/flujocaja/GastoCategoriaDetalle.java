package cl.slimerp.flujocaja;

import java.math.BigDecimal;
import java.util.List;

public record GastoCategoriaDetalle(
        Long categoriaGastoId,
        String categoria,
        BigDecimal total,
        List<LineaGasto> lineas) {
}