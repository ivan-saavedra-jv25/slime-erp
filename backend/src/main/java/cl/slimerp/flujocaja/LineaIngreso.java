package cl.slimerp.flujocaja;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LineaIngreso(
        Long pagoId,
        Long cuentaPorCobrarId,
        LocalDate fecha,
        String descripcion,
        BigDecimal monto) {
}