package cl.slimerp.admin.usuarios;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(
        @NotBlank String password
) {
}