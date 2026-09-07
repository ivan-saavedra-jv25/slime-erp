package cl.slimerp.config;

import cl.slimerp.permisos.Permiso;
import cl.slimerp.permisos.PermisoEfectivoService;
import cl.slimerp.permisos.RolPermisos;
import cl.slimerp.tenant.Rol;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthFilterTest {

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void construyeAutoridadesDeRolYPermisosParaUnTokenValido() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        PermisoEfectivoService permisoEfectivoService = mock(PermisoEfectivoService.class);
        Claims claims = mock(Claims.class);
        when(claims.get("tenantId", String.class)).thenReturn("10");
        when(claims.get("email", String.class)).thenReturn("v1@demo.cl");
        when(claims.get("rol", String.class)).thenReturn("VENDEDOR");
        when(claims.getSubject()).thenReturn("7");
        when(jwtService.parseClaims("token-valido")).thenReturn(claims);
        when(permisoEfectivoService.calcular(10L, 7L, Rol.VENDEDOR)).thenReturn(RolPermisos.permisosDe(Rol.VENDEDOR));

        JwtAuthFilter filter = new JwtAuthFilter(jwtService, permisoEfectivoService);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer token-valido");
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        List<String> autoridades = auth.getAuthorities().stream()
                .map(Object::toString)
                .collect(Collectors.toList());

        assertTrue(autoridades.contains("ROLE_VENDEDOR"));
        assertTrue(autoridades.contains("CLIENTES_VER"));
        assertTrue(autoridades.contains("CLIENTES_EDITAR"));
        assertTrue(autoridades.contains("PRODUCTOS_VER"));
        assertTrue(autoridades.contains("CATEGORIAS_VER"));
        assertTrue(autoridades.contains("BODEGAS_VER"));
        assertTrue(autoridades.contains("FORMAS_PAGO_VER"));
        assertTrue(autoridades.contains("MOVIMIENTOS_VER"));
        assertTrue(autoridades.contains("MOVIMIENTOS_EDITAR"));
        assertTrue(autoridades.contains("VENTAS_VER"));
        assertTrue(autoridades.contains("VENTAS_EDITAR"));
        assertTrue(autoridades.contains("TESORERIA_VER"));
        assertTrue(autoridades.contains("TESORERIA_EDITAR"));
        assertEquals(13, autoridades.size());
        verify(chain).doFilter(request, response);
    }

    @Test
    void incluyeLosPermisosExtraDelUsuarioComoAutoridadesAdicionales() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        PermisoEfectivoService permisoEfectivoService = mock(PermisoEfectivoService.class);
        Claims claims = mock(Claims.class);
        when(claims.get("tenantId", String.class)).thenReturn("10");
        when(claims.get("email", String.class)).thenReturn("v1@demo.cl");
        when(claims.get("rol", String.class)).thenReturn("VENDEDOR");
        when(claims.getSubject()).thenReturn("7");
        when(jwtService.parseClaims("token-valido")).thenReturn(claims);
        Set<Permiso> conExtra = EnumSet.copyOf(RolPermisos.permisosDe(Rol.VENDEDOR));
        conExtra.add(Permiso.COMPRAS_VER);
        when(permisoEfectivoService.calcular(10L, 7L, Rol.VENDEDOR)).thenReturn(conExtra);

        JwtAuthFilter filter = new JwtAuthFilter(jwtService, permisoEfectivoService);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer token-valido");
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        List<String> autoridades = auth.getAuthorities().stream().map(Object::toString).toList();

        assertTrue(autoridades.contains("COMPRAS_VER"));
    }

    @Test
    void sinHeaderAuthorizationNoAutentica() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        PermisoEfectivoService permisoEfectivoService = mock(PermisoEfectivoService.class);
        JwtAuthFilter filter = new JwtAuthFilter(jwtService, permisoEfectivoService);

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }
}
