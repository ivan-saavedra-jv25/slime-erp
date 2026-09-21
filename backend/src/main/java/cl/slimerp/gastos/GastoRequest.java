package cl.slimerp.gastos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record GastoRequest(
        @NotNull Long categoriaGastoId,
        @NotNull @Positive BigDecimal monto,
        @NotBlank String descripcion,
        @NotNull LocalDate fecha
) {
}
