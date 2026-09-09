package cl.slimerp.admin.cobranza;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EmitirCobranzaRequest(
        @NotNull Long tenantId,
        @NotBlank String concepto,
        @NotBlank String periodo,
        @NotNull @Positive BigDecimal montoTotal,
        LocalDate fechaVencimiento,
        String observaciones
) {}