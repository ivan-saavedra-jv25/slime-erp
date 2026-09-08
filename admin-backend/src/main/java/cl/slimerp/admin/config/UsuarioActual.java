package cl.slimerp.admin.config;

import cl.slimerp.admin.tenant.UsuarioRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resuelve el id del usuario autenticado (SUPER_ADMIN) a partir del principal
 * (email) puesto en el SecurityContext por {@link JwtAuthFilter}.
 */
@Component
public class UsuarioActual {

    private final UsuarioRepository usuarioRepository;

    public UsuarioActual(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public Long id() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findFirstByEmailAndActivoTrue(email)
                .map(u -> u.getId())
                .orElseThrow(() -> new IllegalStateException("Usuario autenticado no encontrado"));
    }
}