package cl.slimerp.flujocaja;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LineaGasto(
        Long pagoId,
        Long cuentaPorPagarId,
        LocalDate fecha,
        String descripcion,
        BigDecimal monto) {
}