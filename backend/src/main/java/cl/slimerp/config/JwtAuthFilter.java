package cl.slimerp.config;

import cl.slimerp.permisos.PermisoEfectivoService;
import cl.slimerp.tenant.Rol;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Valida el JWT en cada request, autentica al usuario ante Spring Security con
 * el rol y los permisos efectivos (los del rol más los extra otorgados al
 * usuario, ver {@link PermisoEfectivoService}) como autoridades, y deja el
 * tenant_id disponible en {@link TenantContext} para el resto del pipeline.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final PermisoEfectivoService permisoEfectivoService;

    public JwtAuthFilter(JwtService jwtService, PermisoEfectivoService permisoEfectivoService) {
        this.jwtService = jwtService;
        this.permisoEfectivoService = permisoEfectivoService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtService.parseClaims(token);
                Long tenantId = Long.parseLong(claims.get("tenantId", String.class));
                Long usuarioId = Long.parseLong(claims.getSubject());
                String rol = claims.get("rol", String.class);
                String email = claims.get("email", String.class);

                TenantContext.setTenantId(tenantId);

                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                authorities.add(new SimpleGrantedAuthority("ROLE_" + rol));
                permisoEfectivoService.calcular(tenantId, usuarioId, Rol.valueOf(rol))
                        .forEach(permiso -> authorities.add(new SimpleGrantedAuthority(permiso.name())));

                var authentication = new UsernamePasswordAuthenticationToken(email, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException ex) {
                // Token inválido o expirado: se deja sin autenticar, Spring Security responderá 401/403.
                SecurityContextHolder.clearContext();
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
