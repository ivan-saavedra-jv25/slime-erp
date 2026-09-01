package cl.slimerp.admin;

import cl.slimerp.tenant.Rol;
import cl.slimerp.tenant.Tenant;
import cl.slimerp.tenant.TenantRepository;
import cl.slimerp.tenant.Usuario;
import cl.slimerp.tenant.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmpresaAdminServiceTest {

    private TenantRepository tenantRepository;
    private UsuarioRepository usuarioRepository;
    private PasswordEncoder passwordEncoder;
    private EmpresaAdminService service;

    @BeforeEach
    void setUp() {
        tenantRepository = mock(TenantRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> {
            Tenant t = inv.getArgument(0);
            if (t.getId() == null) t.setId(100L);
            return t;
        });
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(passwordEncoder.encode(any())).thenReturn("hash-cifrado");
        service = new EmpresaAdminService(tenantRepository, usuarioRepository, passwordEncoder);
    }

    private CrearEmpresaRequest request() {
        return new CrearEmpresaRequest(
                "Empresa Nueva", "1.111.111-1", "basico",
                "Admin Nueva", "2.222.222-2", "admin@nueva.cl", "clave123");
    }

    @Test
    void creaTenantYUsuarioAdminInicial() {
        when(tenantRepository.findByRut("1.111.111-1")).thenReturn(Optional.empty());
        when(usuarioRepository.existsByEmail("admin@nueva.cl")).thenReturn(false);

        Tenant tenant = service.crear(request());

        assertEquals(100L, tenant.getId());
        assertEquals("basico", tenant.getPlan());

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        Usuario admin = captor.getValue();
        assertEquals(100L, admin.getTenantId());
        assertEquals(Rol.ADMIN, admin.getRol());
        assertEquals("hash-cifrado", admin.getPasswordHash());
        assertEquals("2.222.222-2", admin.getRut());
    }

    @Test
    void planVacioUsaBasicoPorDefecto() {
        when(tenantRepository.findByRut(any())).thenReturn(Optional.empty());
        when(usuarioRepository.existsByEmail(any())).thenReturn(false);
        CrearEmpresaRequest sinPlan = new CrearEmpresaRequest(
                "Empresa Nueva", "1.111.111-1", "  ",
                "Admin Nueva", "2.222.222-2", "admin@nueva.cl", "clave123");

        Tenant tenant = service.crear(sinPlan);

        assertEquals("basico", tenant.getPlan());
    }

    @Test
    void rutDuplicadoLanzaConflicto() {
        when(tenantRepository.findByRut("1.111.111-1")).thenReturn(Optional.of(Tenant.builder().id(5L).build()));

        assertThrows(EmpresaConflictException.class, () -> service.crear(request()));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void emailAdminDuplicadoLanzaConflicto() {
        when(tenantRepository.findByRut("1.111.111-1")).thenReturn(Optional.empty());
        when(usuarioRepository.existsByEmail("admin@nueva.cl")).thenReturn(true);

        assertThrows(EmpresaConflictException.class, () -> service.crear(request()));
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void listarExcluyeTenantDePlataforma() {
        Tenant negocio = Tenant.builder().id(1L).nombre("Empresa Demo").rut("76.123.456-7").plan("basico").build();
        Tenant plataforma = Tenant.builder().id(3L).nombre("Plataforma Slime ERP").rut("99.999.999-9").plan("plataforma").build();
        when(tenantRepository.findAll()).thenReturn(List.of(negocio, plataforma));

        List<Tenant> resultado = service.listar();

        assertEquals(1, resultado.size());
        assertEquals(1L, resultado.get(0).getId());
    }

    @Test
    void activarYDesactivarCambianElEstado() {
        Tenant tenant = Tenant.builder().id(1L).nombre("Empresa Demo").rut("76.123.456-7").activo(false).build();
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));

        Tenant activado = service.activar(1L);
        assertTrue(activado.isActivo());

        Tenant desactivado = service.desactivar(1L);
        assertFalse(desactivado.isActivo());
    }

    @Test
    void desactivarTenantDePlataformaLanzaConflicto() {
        Tenant plataforma = Tenant.builder().id(3L).nombre("Plataforma Slime ERP").rut("99.999.999-9")
                .plan("plataforma").activo(true).build();
        when(tenantRepository.findById(3L)).thenReturn(Optional.of(plataforma));

        assertThrows(EmpresaConflictException.class, () -> service.desactivar(3L));
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void activarTenantDePlataformaLanzaConflicto() {
        Tenant plataforma = Tenant.builder().id(3L).nombre("Plataforma Slime ERP").rut("99.999.999-9")
                .plan("plataforma").activo(false).build();
        when(tenantRepository.findById(3L)).thenReturn(Optional.of(plataforma));

        assertThrows(EmpresaConflictException.class, () -> service.activar(3L));
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void crearConPlanPlataformaLanzaConflicto() {
        when(tenantRepository.findByRut("1.111.111-1")).thenReturn(Optional.empty());
        when(usuarioRepository.existsByEmail("admin@nueva.cl")).thenReturn(false);
        CrearEmpresaRequest request = new CrearEmpresaRequest(
                "Empresa Nueva", "1.111.111-1", "plataforma",
                "Admin Nueva", "2.222.222-2", "admin@nueva.cl", "clave123");

        assertThrows(EmpresaConflictException.class, () -> service.crear(request));
        verify(tenantRepository, never()).save(any());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void obtenerGestionableRetornaTenantNoPlataforma() {
        Tenant tenant = Tenant.builder().id(1L).nombre("Empresa Demo").rut("76.123.456-7").plan("basico").build();
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));

        assertEquals(tenant, service.obtenerGestionable(1L));
    }

    @Test
    void obtenerGestionableConTenantDePlataformaLanzaConflicto() {
        Tenant plataforma = Tenant.builder().id(3L).nombre("Plataforma Slime ERP").rut("99.999.999-9")
                .plan("plataforma").build();
        when(tenantRepository.findById(3L)).thenReturn(Optional.of(plataforma));

        assertThrows(EmpresaConflictException.class, () -> service.obtenerGestionable(3L));
    }
}
