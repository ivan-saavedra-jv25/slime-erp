package cl.slimerp.admin.auth;

import cl.slimerp.admin.config.JwtService;
import cl.slimerp.admin.rbac.AdminRol;
import cl.slimerp.admin.usuario.AdminSesion;
import cl.slimerp.admin.usuario.AdminSesionRepository;
import cl.slimerp.admin.usuario.AdminUsuario;
import cl.slimerp.admin.usuario.AdminUsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminAuthServiceTest {

    private AdminUsuarioRepository adminUsuarioRepository;
    private AdminSesionRepository adminSesionRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AdminAuthService service;

    @BeforeEach
    void setUp() {
        adminUsuarioRepository = mock(AdminUsuarioRepository.class);
        adminSesionRepository = mock(AdminSesionRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        when(jwtService.generarTokenAdmin(any(), any(), any()))
                .thenReturn("token-admin");
        service = new AdminAuthService(
                adminUsuarioRepository, adminSesionRepository, passwordEncoder, jwtService, 480);
    }

    private AdminUsuario usuario() {
        return AdminUsuario.builder()
                .id(10L)
                .nombre("Super")
                .email("super@slimerp.cl")
                .passwordHash("hash")
                .rol(AdminRol.SUPER_ADMIN)
                .activo(true)
                .build();
    }

    @Test
    void loginValidoEmiteTokenCreaSesionYDevuelvePermisos() {
        when(adminUsuarioRepository.findByEmail("super@slimerp.cl")).thenReturn(Optional.of(usuario()));
        when(passwordEncoder.matches("clave", "hash")).thenReturn(true);

        AdminAuthResponse response = service.login("super@slimerp.cl", "clave", "127.0.0.1", "test-agent");

        assertEquals("token-admin", response.token());
        assertEquals(10L, response.adminId());
        assertEquals("SUPER_ADMIN", response.adminRol());
        assertTrue(response.permisos().contains("EMPRESAS_EDITAR"));
        assertTrue(response.permisos().contains("SESIONES_VER"));

        ArgumentCaptor<AdminSesion> captor = ArgumentCaptor.forClass(AdminSesion.class);
        verify(adminSesionRepository).save(captor.capture());
        assertEquals(10L, captor.getValue().getAdminUsuarioId());
        assertEquals("127.0.0.1", captor.getValue().getIp());
        assertEquals("test-agent", captor.getValue().getUserAgent());
        assertNotNull(captor.getValue().getExpiraEn());
        verify(adminUsuarioRepository).save(argThat(u -> u.getUltimoAcceso() != null));
    }

    @Test
    void loginConPasswordIncorrectaLanzaExcepcion() {
        when(adminUsuarioRepository.findByEmail("super@slimerp.cl")).thenReturn(Optional.of(usuario()));
        when(passwordEncoder.matches("nope", "hash")).thenReturn(false);

        assertThrows(IllegalStateException.class,
                () -> service.login("super@slimerp.cl", "nope", "127.0.0.1", null));
        verify(adminSesionRepository, never()).save(any());
    }

    @Test
    void loginDeAdminInactivoEsRechazado() {
        when(adminUsuarioRepository.findByEmail("super@slimerp.cl"))
                .thenReturn(Optional.of(AdminUsuario.builder()
                        .email("super@slimerp.cl").activo(false).build()));

        assertThrows(IllegalStateException.class,
                () -> service.login("super@slimerp.cl", "clave", "127.0.0.1", null));
    }

    @Test
    void bloqueaCuentaTrasCincoIntentosFallidos() {
        when(adminUsuarioRepository.findByEmail("super@slimerp.cl")).thenReturn(Optional.of(usuario()));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        for (int i = 0; i < 5; i++) {
            assertThrows(IllegalStateException.class,
                    () -> service.login("super@slimerp.cl", "clave", "127.0.0.1", null));
        }
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        assertThrows(IllegalStateException.class,
                () -> service.login("super@slimerp.cl", "clave", "127.0.0.1", null));
    }

    @Test
    void logoutRevocaLaSesionExistente() {
        AdminSesion sesion = AdminSesion.builder().id(1L).tokenHash(AdminAuthService.sha256("tok")).build();
        when(adminSesionRepository.findByTokenHash("abc")).thenReturn(Optional.empty());
        when(adminSesionRepository.findByTokenHash(AdminAuthService.sha256("tok"))).thenReturn(Optional.of(sesion));

        assertTrue(service.logout("tok"));
        assertNotNull(sesion.getRevocadaEn());
        verify(adminSesionRepository).save(sesion);

        assertFalse(service.logout(null));
    }

    @Test
    void sha256GeneraHashConsistente() {
        assertEquals(AdminAuthService.sha256("abc"), AdminAuthService.sha256("abc"));
        assertEquals(64, AdminAuthService.sha256("abc").length());
        assertNotEquals(AdminAuthService.sha256("abc"), AdminAuthService.sha256("abd"));
    }
}