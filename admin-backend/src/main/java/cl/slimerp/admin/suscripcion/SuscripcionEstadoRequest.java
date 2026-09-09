package cl.slimerp.admin.suscripcion;

import jakarta.validation.constraints.NotBlank;

public record SuscripcionEstadoRequest(
        @NotBlank(message = "El motivo es obligatorio")
        String motivo
) {
}