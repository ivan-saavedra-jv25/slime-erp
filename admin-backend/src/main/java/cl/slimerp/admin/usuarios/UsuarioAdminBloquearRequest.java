package cl.slimerp.admin.usuarios;

import jakarta.validation.constraints.NotBlank;

public record UsuarioAdminBloquearRequest(
        @NotBlank String motivo) {
}