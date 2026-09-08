package cl.slimerp.admin.auth;

import cl.slimerp.admin.config.JwtAuthFilter;
import cl.slimerp.admin.config.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthFilterTest {

    private JwtService jwtService;
    private JwtAuthFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;
    private Claims claims;

    @BeforeEach
    void setUp() throws Exception {
        jwtService = mock(JwtService.class);
        filter = new JwtAuthFilter(jwtService);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
        claims = mock(Claims.class);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void tokenAdminValidoPueblaRolYPermisos() throws Exception {
        request.addHeader("Authorization", "Bearer tok");
        when(jwtService.parseClaims("tok")).thenReturn(claims);
        when(claims.get("email", String.class)).thenReturn("admin@slimerp.cl");
        when(claims.get("adminRol", String.class)).thenReturn("SUPER_ADMIN");

        filter.doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals("admin@slimerp.cl", auth.getName());
        Set<String> authorities = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        assertTrue(authorities.contains(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN").getAuthority()));
        assertTrue(authorities.contains("PERM_EMPRESAS_VER"));
        assertTrue(authorities.contains("PERM_SESIONES_EDITAR"));
        assertTrue(authorities.stream().anyMatch(a -> a.equals("PERM_AUDITORIA_VER")));
    }

    @Test
    void tokenSinAdminRolCaeAlFallbackLegacy() throws Exception {
        request.addHeader("Authorization", "Bearer tok");
        when(jwtService.parseClaims("tok")).thenReturn(claims);
        when(claims.get("email", String.class)).thenReturn("admin@slimerp.cl");
        when(claims.get("adminRol", String.class)).thenReturn(null);
        when(claims.get("rol", String.class)).thenReturn("ADMIN");

        filter.doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        Set<String> authorities = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        assertEquals(Set.of("ROLE_ADMIN"), authorities);
    }

    @Test
    void sinHeaderNoAutentica() throws Exception {
        filter.doFilter(request, response, chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}