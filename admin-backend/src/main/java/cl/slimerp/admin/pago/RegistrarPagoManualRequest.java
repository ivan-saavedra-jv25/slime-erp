package cl.slimerp.admin.pago;

import cl.slimerp.admin.cobranza.MedioPago;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Registro manual de un pago sobre la cobranza existente (spec §12). */
public record RegistrarPagoManualRequest(
        @NotNull Long companyId,
        Long suscripcionId,
        @NotNull @Positive BigDecimal monto,
        @NotNull MedioPago metodo,
        String referencia,
        LocalDateTime fecha
) {}