package cl.slimerp.admin.suscripcion;

import jakarta.validation.constraints.NotNull;

public record CambiarPlanRequest(
        @NotNull(message = "El nuevo plan es obligatorio")
        Long planId,

        String motivo
) {
}