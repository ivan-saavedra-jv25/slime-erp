package cl.slimerp.admin.empresas;

import cl.slimerp.admin.alerta.Alerta;
import cl.slimerp.admin.alerta.AlertaRepository;
import cl.slimerp.admin.alerta.AlertaService;
import cl.slimerp.admin.auditoria.AuditService;
import cl.slimerp.admin.cobranza.CobranzaService;
import cl.slimerp.admin.common.Paginated;
import cl.slimerp.admin.suscripcion.Suscripcion;
import cl.slimerp.admin.suscripcion.SuscripcionRepository;
import cl.slimerp.admin.tenant.Rol;
import cl.slimerp.admin.tenant.Tenant;
import cl.slimerp.admin.tenant.TenantRepository;
import cl.slimerp.admin.tenant.Usuario;
import cl.slimerp.admin.tenant.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmpresaAdminServiceTest {

    private TenantRepository tenantRepository;
    private UsuarioRepository usuarioRepository;
    private PasswordEncoder passwordEncoder;
    private CobranzaService cobranzaService;
    private AuditService auditService;
    private AlertaService alertaService;
    private SuscripcionRepository suscripcionRepository;
    private AlertaRepository alertaRepository;
    private EmpresaAdminService service;

    @BeforeEach
    void setUp() {
        tenantRepository = mock(TenantRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        cobranzaService = mock(CobranzaService.class);
        auditService = mock(AuditService.class);
        alertaService = mock(AlertaService.class);
        suscripcionRepository = mock(SuscripcionRepository.class);
        alertaRepository = mock(AlertaRepository.class);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> {
            Tenant t = inv.getArgument(0);
            if (t.getId() == null) t.setId(100L);
            return t;
        });
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(passwordEncoder.encode(any())).thenReturn("hash-cifrado");
        when(cobranzaService.saldoPendienteTenant(any())).thenReturn(BigDecimal.ZERO);
        when(alertaService.crear(any(), any(), any(), any(), any())).thenReturn(Alerta.builder().build());
        service = new EmpresaAdminService(tenantRepository, usuarioRepository, passwordEncoder,
                cobranzaService, auditService, alertaService, suscripcionRepository, alertaRepository);
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
        assertEquals("ACTIVE", tenant.getStatus());

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        Usuario admin = captor.getValue();
        assertEquals(100L, admin.getTenantId());
        assertEquals(Rol.ADMIN, admin.getRol());
        assertEquals("hash-cifrado", admin.getPasswordHash());
        assertEquals("2.222.222-2", admin.getRut());
        verify(auditService).registrar(eq("EMPRESA_CREADA"), eq("empresas"), any(), any(), any(), any(), any());
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
    void listarPaginadoDevuelveTotalYTotalPages() {
        Tenant negocio = Tenant.builder().id(1L).nombre("Empresa Demo").rut("76.123.456-7")
                .plan("basico").status("ACTIVE").build();
        Page<Tenant> pagina = new PageImpl<>(List.of(negocio), PageRequest.of(0, 10), 1);
        when(tenantRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pagina);

        Paginated<EmpresaResponse> resultado = service.listar(0, 10, null, null, null, null, null, null, null);

        assertEquals(1, resultado.totalElements());
        assertEquals(1, resultado.totalPages());
        assertEquals(1, resultado.content().size());
        assertEquals("Empresa Demo", resultado.content().get(0).nombre());
        verify(tenantRepository).findAll(any(Specification.class), eq(PageRequest.of(0, 10, org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "fechaAlta"))));
    }

    @Test
    void listarPorIdFiltraEnBackend() {
        Tenant negocio = Tenant.builder().id(7L).nombre("Empresa Demo").rut("76.123.456-7")
                .plan("basico").status("ACTIVE").build();
        Page<Tenant> pagina = new PageImpl<>(List.of(negocio), PageRequest.of(0, 10), 1);
        when(tenantRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pagina);

        Paginated<EmpresaResponse> resultado = service.listar(0, 10, 7L, null, null, null, null, null, null);

        assertEquals(7L, resultado.content().get(0).id());
        verify(tenantRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void cambiarEstadoPersisteSincronizaActivoYAudita() {
        Tenant tenant = Tenant.builder().id(1L).nombre("Empresa Demo").rut("76.123.456-7")
                .plan("basico").status("ACTIVE").activo(true).build();
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));

        Tenant resultado = service.cambiarEstado(1L, EstadoEmpresa.SUSPENDED, "Impago de suscripción");

        assertEquals("SUSPENDED", resultado.getStatus());
        assertFalse(resultado.isActivo());
        verify(auditService).registrar(eq("EMPRESA_ESTADO_CAMBIADO"), eq("empresas"), eq(tenant),
                eq("tenant"), eq(1L), contains("\"ACTIVE\""), contains("\"SUSPENDED\""));
        verify(alertaService).crear(eq(1L), eq("CRITICAL"), eq("EMPRESA_SUSPENDED"), any(), any());
    }

    @Test
    void cambiarEstadoSinMotivoLanzaExcepcion() {
        Tenant tenant = Tenant.builder().id(1L).nombre("Empresa Demo").rut("76.123.456-7").plan("basico").build();
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));

        assertThrows(IllegalArgumentException.class,
                () -> service.cambiarEstado(1L, EstadoEmpresa.SUSPENDED, " "));
        verify(tenantRepository, never()).save(any());
        verify(auditService, never()).registrar(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void activarDevuelveEstadoActivo() {
        Tenant tenant = Tenant.builder().id(1L).nombre("Empresa Demo").rut("76.123.456-7")
                .plan("basico").status("SUSPENDED").activo(false).build();
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));

        Tenant activado = service.activar(1L);

        assertTrue(activado.isActivo());
        assertEquals("ACTIVE", activado.getStatus());
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
    void detalleAgregaKpisDeUsuariosSuscripcionYAlertas() {
        Tenant tenant = Tenant.builder().id(1L).nombre("Empresa Demo").rut("76.123.456-7")
                .plan("basico").status("ACTIVE").activo(true).build();
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(usuarioRepository.countByTenantId(1L)).thenReturn(5L);
        when(usuarioRepository.countByTenantIdAndActivoTrue(1L)).thenReturn(3L);
        when(suscripcionRepository.findByCompanyIdOrderByFechaInicioDesc(1L))
                .thenReturn(List.of(Suscripcion.builder().estado("ACTIVE").build()));
        when(alertaRepository.countByCompanyIdAndStatus(1L, "OPEN")).thenReturn(2L);

        EmpresaDetalleResponse detalle = service.detalle(1L);

        assertEquals(5L, detalle.usuariosTotales());
        assertEquals(3L, detalle.usuariosActivos());
        assertEquals("ACTIVE", detalle.suscripcionEstado());
        assertEquals(2L, detalle.alertasAbiertas());
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
}