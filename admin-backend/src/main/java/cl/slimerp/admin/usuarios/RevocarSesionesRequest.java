package cl.slimerp.admin.usuarios;

import jakarta.validation.constraints.NotBlank;

public record RevocarSesionesRequest(
        @NotBlank String motivo) {
}