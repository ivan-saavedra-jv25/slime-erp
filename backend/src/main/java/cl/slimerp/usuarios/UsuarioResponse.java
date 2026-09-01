package cl.slimerp.usuarios;

import cl.slimerp.tenant.Rol;
import cl.slimerp.tenant.Usuario;

import java.time.LocalDateTime;

public record UsuarioResponse(
        Long id,
        String nombre,
        String rut,
        String email,
        Rol rol,
        boolean activo,
        LocalDateTime fechaCreacion
) {
    public static UsuarioResponse desde(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNombre(), usuario.getRut(),
                usuario.getEmail(), usuario.getRol(), usuario.isActivo(), usuario.getFechaCreacion());
    }
}
