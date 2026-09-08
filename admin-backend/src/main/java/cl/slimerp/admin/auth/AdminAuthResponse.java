package cl.slimerp.admin.auth;

import java.util.List;

public record AdminAuthResponse(
        String token,
        Long adminId,
        String nombre,
        String email,
        String adminRol,
        List<String> permisos) {
}