package cl.slimerp.admin.suscripcion;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record SuscripcionRequest(
        @NotNull(message = "La empresa es obligatoria")
        Long companyId,

        @NotNull(message = "El plan es obligatorio")
        Long planId,

        @NotNull(message = "La fecha de inicio es obligatoria")
        LocalDate fechaInicio,

        @NotNull(message = "La fecha de vencimiento es obligatoria")
        LocalDate fechaVencimiento,

        @NotBlank(message = "El ciclo de facturación es obligatorio")
        String cicloFacturacion,

        @NotNull(message = "El precio es obligatorio")
        @DecimalMin(value = "0", message = "El precio no puede ser negativo")
        BigDecimal precio,

        String estado,

        @Min(value = 0, message = "El período de gracia no puede ser negativo")
        Integer periodoGraciaDias
) {
}