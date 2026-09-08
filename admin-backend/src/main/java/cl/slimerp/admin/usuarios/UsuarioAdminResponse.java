package cl.slimerp.admin.usuarios;

import cl.slimerp.admin.tenant.Rol;
import cl.slimerp.admin.tenant.Usuario;

import java.time.LocalDateTime;

public record UsuarioAdminResponse(
        Long id,
        Long tenantId,
        String tenantNombre,
        String nombre,
        String rut,
        String email,
        Rol rol,
        boolean activo,
        LocalDateTime fechaCreacion
) {
    public static UsuarioAdminResponse desde(Usuario usuario, String tenantNombre) {
        return new UsuarioAdminResponse(
                usuario.getId(),
                usuario.getTenantId(),
                tenantNombre,
                usuario.getNombre(),
                usuario.getRut(),
                usuario.getEmail(),
                usuario.getRol(),
                usuario.isActivo(),
                usuario.getFechaCreacion());
    }
}