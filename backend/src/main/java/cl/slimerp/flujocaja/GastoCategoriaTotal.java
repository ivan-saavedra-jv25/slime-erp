package cl.slimerp.flujocaja;

import java.math.BigDecimal;

public record GastoCategoriaTotal(Long categoriaGastoId, String categoria, BigDecimal total) {
}