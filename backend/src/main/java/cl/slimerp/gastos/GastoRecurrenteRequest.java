package cl.slimerp.gastos;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record GastoRecurrenteRequest(
        @NotNull Long categoriaGastoId,
        @NotNull @Positive BigDecimal monto,
        @NotBlank String descripcion,
        @NotNull FrecuenciaGastoRecurrente frecuencia,
        @Min(1) @Max(28) Integer diaMes,
        @NotNull LocalDate fechaInicio,
        LocalDate fechaFin
) {
}