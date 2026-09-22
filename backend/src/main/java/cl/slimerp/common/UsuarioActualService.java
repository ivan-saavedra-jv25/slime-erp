package cl.slimerp.common;

import cl.slimerp.tenant.Usuario;
import cl.slimerp.tenant.UsuarioRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

// El JWT deja el email como principal (ver JwtAuthFilter), no el id del
// usuario, así que hay que resolverlo contra la base en cada acción que necesita
// saber quién actuó. Este servicio centraliza ese paso.
@Service
public class UsuarioActualService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioActualService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public Long idUsuarioActual(Long tenantId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth != null ? auth.getName() : null;
        if (email == null) {
            throw new IllegalStateException("No hay usuario autenticado");
        }
        return usuarioRepository.findByEmailAndTenantId(email, tenantId)
                .map(Usuario::getId)
                .orElseThrow(() -> new IllegalStateException("Usuario no encontrado: " + email));
    }
}
