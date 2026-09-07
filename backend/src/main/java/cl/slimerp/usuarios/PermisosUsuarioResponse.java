package cl.slimerp.usuarios;

import cl.slimerp.tenant.Rol;

import java.util.Set;

public record PermisosUsuarioResponse(
        Rol rol,
        Set<String> permisosRol,
        Set<String> permisosExtra
) {
}
