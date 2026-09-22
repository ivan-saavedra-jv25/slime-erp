package cl.slimerp.auth;

import java.util.List;

public record LoginResponse(
        String token,
        Long usuarioId,
        Long tenantId,
        String tenantNombre,
        String tenantRut,
        String nombre,
        String email,
        String rut,
        String rol,
        List<String> permisos,
        boolean esPrimerIngreso
) {
}
