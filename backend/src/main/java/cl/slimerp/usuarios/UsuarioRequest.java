package cl.slimerp.usuarios;

import cl.slimerp.tenant.Rol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UsuarioRequest(
        @NotBlank String nombre,
        @NotBlank String rut,
        @NotBlank @Email String email,
        String password,
        @NotNull Rol rol,
        Boolean activo
) {
}
