package cl.slimerp.admin.auth;

import cl.slimerp.admin.usuario.AdminUsuarioRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resuelve el id del administrador autenticado (tabla {@code admin.usuario}) a
 * partir del principal (email) puesto en el SecurityContext por
 * {@link cl.slimerp.admin.config.JwtAuthFilter}.
 */
@Component
public class AdminActual {

    private final AdminUsuarioRepository adminUsuarioRepository;

    public AdminActual(AdminUsuarioRepository adminUsuarioRepository) {
        this.adminUsuarioRepository = adminUsuarioRepository;
    }

    public Long id() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return adminUsuarioRepository.findByEmailAndActivoTrue(email)
                .map(usuario -> usuario.getId())
                .orElseThrow(() -> new IllegalStateException("Administrador autenticado no encontrado"));
    }
}