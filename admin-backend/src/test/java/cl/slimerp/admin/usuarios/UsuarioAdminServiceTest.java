package cl.slimerp.admin.usuarios;

import cl.slimerp.admin.auditoria.AuditService;
import cl.slimerp.admin.tenant.Rol;
import cl.slimerp.admin.tenant.Tenant;
import cl.slimerp.admin.tenant.TenantRepository;
import cl.slimerp.admin.tenant.Usuario;
import cl.slimerp.admin.tenant.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class UsuarioAdminServiceTest {

    private TenantRepository tenantRepository;
    private UsuarioRepository usuarioRepository;
    private PasswordEncoder passwordEncoder;
    private AuditService auditService;
    private UsuarioAdminService service;

    @BeforeEach
    void setUp() {
        tenantRepository = mock(TenantRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        auditService = mock(AuditService.class);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(passwordEncoder.encode(any())).thenReturn("hash-cifrado");
        service = new UsuarioAdminService(usuarioRepository, tenantRepository, passwordEncoder, auditService);
    }

    private Tenant empresa(boolean plataforma) {
        return Tenant.builder()
                .id(plataforma ? 99L : 1L)
                .nombre(plataforma ? "Plataforma" : "Empresa Demo")
                .rut(plataforma ? "99.999.999-9" : "76.123.456-7")
                .plan(plataforma ? "plataforma" : "basico")
                .build();
    }

    private Usuario usuario(Long id, Long tenantId, String email, Rol rol) {
        return Usuario.builder().id(id).tenantId(tenantId).email(email).rut("11.222.333-4")
                .passwordHash("x").nombre("Juan Pérez").rol(rol).build();
    }

    private UsuarioAdminRequest request() {
        return new UsuarioAdminRequest(
                1L, "Juan Pérez", "11.222.333-4", "juan@empresa.cl", "clave123", Rol.VENDEDOR, null);
    }

    @Test
    void crearGuardaUsuarioConTenantYHashDePassword() {
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(empresa(false)));
        when(usuarioRepository.existsByEmail("juan@empresa.cl")).thenReturn(false);

        UsuarioAdminResponse res = service.crear(request());

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        Usuario guardado = captor.getValue();
        assertEquals(1L, guardado.getTenantId());
        assertEquals(Rol.VENDEDOR, guardado.getRol());
        assertEquals("hash-cifrado", guardado.getPasswordHash());
        assertTrue(guardado.isActivo());
        assertEquals("Empresa Demo", res.tenantNombre());
    }

    @Test
    void crearSinPasswordLanzaError() {
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(empresa(false)));
        when(usuarioRepository.existsByEmail(any())).thenReturn(false);

        UsuarioAdminRequest sinPassword = new UsuarioAdminRequest(
                1L, "Juan Pérez", "11.222.333-4", "juan@empresa.cl", "  ", Rol.VENDEDOR, null);

        assertThrows(IllegalArgumentException.class, () -> service.crear(sinPassword));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void crearEmailDuplicadoLanzaConflicto() {
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(empresa(false)));
        when(usuarioRepository.existsByEmail("juan@empresa.cl")).thenReturn(true);

        assertThrows(UsuarioConflictException.class, () -> service.crear(request()));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void crearRolSuperAdminEsRechazado() {
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(empresa(false)));
        when(usuarioRepository.existsByEmail(any())).thenReturn(false);

        UsuarioAdminRequest superAdmin = new UsuarioAdminRequest(
                1L, "Súper", "1-9", "s@cl", "clave", Rol.SUPER_ADMIN, null);

        assertThrows(IllegalArgumentException.class, () -> service.crear(superAdmin));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void crearEnTenantDePlataformaEsRechazado() {
        when(tenantRepository.findById(99L)).thenReturn(Optional.of(empresa(true)));

        UsuarioAdminRequest enPlataforma = new UsuarioAdminRequest(
                99L, "X", "1-9", "x@cl", "clave", Rol.ADMIN, null);

        assertThrows(IllegalArgumentException.class, () -> service.crear(enPlataforma));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void listarExcluyeUsuariosDeTenantPlataformaYFiltra() {
        Tenant negocio = empresa(false);
        Tenant plataforma = empresa(true);
        when(tenantRepository.findAll()).thenReturn(List.of(negocio, plataforma));
        Usuario u1 = usuario(1L, 1L, "a@cl", Rol.ADMIN);
        Usuario u2 = usuario(2L, 1L, "b@cl", Rol.VENDEDOR);
        when(usuarioRepository.findByTenantIdIn(List.of(1L))).thenReturn(List.of(u1, u2));

        List<UsuarioAdminResponse> activos = service.listar(null, null);
        assertEquals(2, activos.size());
        assertNotEquals(99L, activos.get(0).tenantId());
        verify(usuarioRepository).findByTenantIdIn(List.of(1L));

        u2.setActivo(false);
        List<UsuarioAdminResponse> soloActivos = service.listar(null, true);
        assertEquals(1, soloActivos.size());
    }

    @Test
    void actualizarCambiaDatosYRechazaRolSuperAdmin() {
        Usuario u = usuario(5L, 1L, "juan@empresa.cl", Rol.VENDEDOR);
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(u));
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(empresa(false)));
        when(usuarioRepository.existsByEmail("nuevo@empresa.cl")).thenReturn(false);

        UsuarioAdminRequest edicion = new UsuarioAdminRequest(
                1L, "Juan Actualizado", "11.222.333-4", "nuevo@empresa.cl", null, Rol.ADMIN, true);
        UsuarioAdminResponse res = service.actualizar(5L, edicion);

        assertEquals("Juan Actualizado", res.nombre());
        assertEquals("nuevo@empresa.cl", res.email());
        assertEquals(Rol.ADMIN, res.rol());
        assertTrue(res.activo());

        UsuarioAdminRequest conSuperAdmin = new UsuarioAdminRequest(
                1L, "Juan Actualizado", "11.222.333-4", "nuevo@empresa.cl", null, Rol.SUPER_ADMIN, true);
        assertThrows(IllegalArgumentException.class, () -> service.actualizar(5L, conSuperAdmin));
    }

    @Test
    void cambiarEstadoActivaYDesactivaAuditado() {
        Usuario u = usuario(5L, 1L, "juan@empresa.cl", Rol.VENDEDOR);
        u.setActivo(false);
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(u));
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(empresa(false)));

        assertTrue(service.cambiarEstado(5L, true, null).activo());
        verify(auditService).registrar(eq("USER_ACTIVATED"), eq("usuarios"), any(), eq("usuario"),
                eq(5L), any(), any());

        assertFalse(service.cambiarEstado(5L, false, "Baja por inactividad").activo());
        verify(auditService).registrar(eq("USER_DISABLED"), eq("usuarios"), any(), eq("usuario"),
                eq(5L), contains("true"), contains("Baja por inactividad"));
    }

    @Test
    void bloquearRequiereMotivoYAudita() {
        Usuario u = usuario(5L, 1L, "juan@empresa.cl", Rol.VENDEDOR);
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(u));
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(empresa(false)));

        assertThrows(IllegalArgumentException.class, () -> service.bloquear(5L, "  "));
        verify(usuarioRepository, never()).save(any());

        assertFalse(service.bloquear(5L, "Acceso irregular").activo());
        verify(auditService).registrar(eq("USER_BLOCKED"), eq("usuarios"), any(), eq("usuario"),
                eq(5L), any(), contains("Acceso irregular"));
    }

    @Test
    void revocarSesionesRequiereMotivoYRegistraAuditoria() {
        Usuario u = usuario(5L, 1L, "juan@empresa.cl", Rol.VENDEDOR);
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(u));
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(empresa(false)));

        assertThrows(IllegalArgumentException.class, () -> service.revocarSesiones(5L, null));

        service.revocarSesiones(5L, "Sospecha de acceso compartido");
        verify(auditService).registrar(eq("SESSION_REVOKED"), eq("usuarios"), any(), eq("usuario"),
                eq(5L), isNull(), contains("Sospecha de acceso compartido"));
    }

    @Test
    void resetearPasswordRechazaVacio() {
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(empresa(false)));
        Usuario u = usuario(5L, 1L, "juan@empresa.cl", Rol.VENDEDOR);
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(u));

        assertThrows(IllegalArgumentException.class, () -> service.resetearPassword(5L, "   "));
        verify(usuarioRepository, never()).save(any());

        service.resetearPassword(5L, "nuevaClave");
        assertEquals("hash-cifrado", u.getPasswordHash());
        verify(usuarioRepository).save(u);
    }
}