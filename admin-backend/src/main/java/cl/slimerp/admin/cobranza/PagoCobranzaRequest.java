package cl.slimerp.admin.cobranza;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PagoCobranzaRequest(
        @NotNull @Positive BigDecimal monto,
        @NotNull MedioPago medioPago,
        String numeroOperacion,
        String observaciones
) {}