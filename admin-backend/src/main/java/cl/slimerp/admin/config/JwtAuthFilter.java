package cl.slimerp.admin.config;

import cl.slimerp.admin.rbac.AdminPermisos;
import cl.slimerp.admin.rbac.AdminRol;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Valida el JWT del portal admin (claims {@code adminId}/{@code adminRol}) y
 * poblado con autoridades {@code ROLE_<rol>} + {@code PERM_<permiso>} según la
 * matriz de {@link AdminPermisos}. Si el token no trae {@code adminRol} (tokens
 * legacy del backend de negocio), cae al fallback {@code rol} para no romper las
 * pantallas actuales durante la transición.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
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
                String email = claims.get("email", String.class);

                List<GrantedAuthority> authorities = new ArrayList<>();
                String adminRol = claims.get("adminRol", String.class);
                if (adminRol != null && !adminRol.isBlank()) {
                    AdminRol rol = AdminRol.valueOf(adminRol);
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + rol.name()));
                    for (String permiso : AdminPermisos.permisosDe(rol)) {
                        authorities.add(new SimpleGrantedAuthority("PERM_" + permiso));
                    }
                } else {
                    String rolLegacy = claims.get("rol", String.class);
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + rolLegacy));
                }

                var authentication = new UsernamePasswordAuthenticationToken(email, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException ex) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}