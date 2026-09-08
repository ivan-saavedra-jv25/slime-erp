package cl.slimerp.admin.usuarios;

import cl.slimerp.admin.tenant.Rol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UsuarioAdminRequest(
        Long tenantId,
        @NotBlank String nombre,
        @NotBlank String rut,
        @NotBlank @Email String email,
        String password,
        @NotNull Rol rol,
        Boolean activo
) {
}