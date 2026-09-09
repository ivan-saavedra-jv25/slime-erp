package cl.slimerp.admin.suscripcion;

import cl.slimerp.admin.auditoria.AuditService;
import cl.slimerp.admin.configuracion.Configuracion;
import cl.slimerp.admin.configuracion.ConfiguracionRepository;
import cl.slimerp.admin.plan.Plan;
import cl.slimerp.admin.plan.PlanRepository;
import cl.slimerp.admin.tenant.Tenant;
import cl.slimerp.admin.tenant.TenantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SuscripcionServiceTest {

    private SuscripcionRepository suscripcionRepository;
    private PlanRepository planRepository;
    private TenantRepository tenantRepository;
    private ConfiguracionRepository configuracionRepository;
    private AuditService auditService;
    private SuscripcionService service;

    @BeforeEach
    void setUp() {
        suscripcionRepository = mock(SuscripcionRepository.class);
        planRepository = mock(PlanRepository.class);
        tenantRepository = mock(TenantRepository.class);
        configuracionRepository = mock(ConfiguracionRepository.class);
        auditService = mock(AuditService.class);
        when(suscripcionRepository.save(any(Suscripcion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new SuscripcionService(suscripcionRepository, planRepository, tenantRepository,
                configuracionRepository, auditService, new ObjectMapper());
    }

    private Tenant tenant(Long id, String nombre) {
        return Tenant.builder().id(id).nombre(nombre).rut("76543210-1").plan("basico")
                .status("ACTIVE").activo(true).build();
    }

    private Plan plan(Long id, String nombre, BigDecimal mensual, BigDecimal anual, String estado) {
        return Plan.builder().id(id).nombre(nombre).descripcion("Plan")
                .precioMensual(mensual).precioAnual(anual)
                .maxUsuarios(10).maxDocumentos(1000)
                .modulos("[\"ventas\"]").estado(estado).build();
    }

    private Suscripcion suscripcion(Long id, Plan plan, String estado, LocalDate vencimiento,
                                    LocalDate inicio, String ciclo) {
        return Suscripcion.builder()
                .id(id)
                .companyId(1L)
                .plan(plan)
                .estado(estado)
                .fechaInicio(inicio)
                .fechaVencimiento(vencimiento)
                .cicloFacturacion(ciclo)
                .precio(new BigDecimal("29990"))
                .periodoGraciaDias(7)
                .build();
    }

    private void configurarBasico(SuscripcionRequest request) {
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant(1L, "Empresa Demo")));
        when(planRepository.findById(2L)).thenReturn(Optional.of(plan(2L, "Profesional",
                new BigDecimal("29990"), new BigDecimal("299900"), "ACTIVE")));
        when(configuracionRepository.findById("suscripcion.gracePeriodDays"))
                .thenReturn(Optional.of(Configuracion.builder().clave("suscripcion.gracePeriodDays")
                        .valor("{\"gracePeriodDays\":7}").build()));
    }

    private SuscripcionRequest request(LocalDate inicio, LocalDate vencimiento) {
        return new SuscripcionRequest(1L, 2L, inicio, vencimiento, "MONTHLY",
                new BigDecimal("29990"), null, null);
    }

    @Test
    void crearPersisteConEstadoActivoYGraciaDesdeConfiguracion() {
        configurarBasico(null);
        when(suscripcionRepository.countByCompanyIdAndEstadoIn(eq(1L), any())).thenReturn(0L);

        SuscripcionResponse response = service.crear(request(LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 2, 1)));

        assertEquals("ACTIVE", response.estado());
        assertEquals(7, response.periodoGraciaDias());
        assertEquals("Profesional", response.planNombre());
        verify(auditService).registrar(eq("SUBSCRIPTION_CREATED"), eq("suscripciones"),
                any(), eq("suscripcion"), any(), isNull(), contains("\"estado\": \"ACTIVE\""));
    }

    @Test
    void crearPermiteEstadoTrial() {
        configurarBasico(null);
        when(suscripcionRepository.countByCompanyIdAndEstadoIn(eq(1L), any())).thenReturn(0L);

        SuscripcionRequest trialRequest = new SuscripcionRequest(1L, 2L,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 15), "MONTHLY",
                new BigDecimal("0"), "TRIAL", null);

        SuscripcionResponse response = service.crear(trialRequest);

        assertEquals("TRIAL", response.estado());
    }

    @Test
    void crearRechazaOverlapDeEmpresaActiva() {
        configurarBasico(null);
        when(suscripcionRepository.countByCompanyIdAndEstadoIn(eq(1L), any())).thenReturn(1L);

        SuscripcionConflictException ex = assertThrows(SuscripcionConflictException.class,
                () -> service.crear(request(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1))));
        assertTrue(ex.getMessage().contains("suscripción activa"));
        verify(suscripcionRepository, never()).save(any());
    }

    @Test
    void crearRechazaVencimientoNoPosteriorAlInicio() {
        configurarBasico(null);
        when(suscripcionRepository.countByCompanyIdAndEstadoIn(eq(1L), any())).thenReturn(0L);

        assertThrows(IllegalArgumentException.class,
                () -> service.crear(request(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 1))));
    }

    @Test
    void crearRechazaPlanInactivo() {
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant(1L, "Empresa Demo")));
        when(planRepository.findById(2L)).thenReturn(Optional.of(plan(2L, "Basico",
                new BigDecimal("15990"), new BigDecimal("159900"), "INACTIVE")));

        assertThrows(SuscripcionConflictException.class,
                () -> service.crear(request(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1))));
    }

    @Test
    void crearRequiereConfiguracionDePeriodoDeGracia() {
        configurarBasico(null);
        when(configuracionRepository.findById("suscripcion.gracePeriodDays")).thenReturn(Optional.empty());
        when(suscripcionRepository.countByCompanyIdAndEstadoIn(eq(1L), any())).thenReturn(0L);

        assertThrows(IllegalStateException.class,
                () -> service.crear(request(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1))));
    }

    @Test
    void extenderPorDiasActualizaVencimientoYAuditaOldNew() {
        LocalDate vencimiento = LocalDate.of(2026, 3, 1);
        Suscripcion sub = suscripcion(1L, plan(2L, "Profesional", new BigDecimal("29990"), null, "ACTIVE"),
                "ACTIVE", vencimiento, LocalDate.of(2026, 1, 1), "MONTHLY");
        when(suscripcionRepository.findById(1L)).thenReturn(Optional.of(sub));
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant(1L, "Empresa Demo")));

        SuscripcionResponse response = service.extender(1L, new ExtenderSuscripcionRequest(null, 30, null));

        assertEquals(LocalDate.of(2026, 3, 31), response.fechaVencimiento());
        verify(auditService).registrar(eq("SUBSCRIPTION_EXTENDED"), eq("suscripciones"),
                any(), eq("suscripcion"), any(), contains("2026-03-01"), contains("2026-03-31"));
    }

    @Test
    void extenderConNuevoVencimientoExplicito() {
        Suscripcion sub = suscripcion(1L, plan(2L, "Profesional", new BigDecimal("29990"), null, "ACTIVE"),
                "TRIAL", LocalDate.of(2026, 1, 15), LocalDate.of(2026, 1, 1), "MONTHLY");
        when(suscripcionRepository.findById(1L)).thenReturn(Optional.of(sub));
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant(1L, "Empresa Demo")));

        SuscripcionResponse response = service.extender(1L,
                new ExtenderSuscripcionRequest(LocalDate.of(2026, 1, 31), null, "Cortesía"));

        assertEquals(LocalDate.of(2026, 1, 31), response.fechaVencimiento());
        verify(auditService).registrar(eq("SUBSCRIPTION_EXTENDED"), eq("suscripciones"),
                any(), eq("suscripcion"), any(), any(), contains("\"motivo\": \"Cortesía\""));
    }

    @Test
    void extenderRechazaVencimientoNoPosterior() {
        Suscripcion sub = suscripcion(1L, plan(2L, "Profesional", new BigDecimal("29990"), null, "ACTIVE"),
                "ACTIVE", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 1, 1), "MONTHLY");
        when(suscripcionRepository.findById(1L)).thenReturn(Optional.of(sub));
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant(1L, "Empresa Demo")));

        assertThrows(IllegalArgumentException.class,
                () -> service.extender(1L, new ExtenderSuscripcionRequest(null, 0, null)));
        assertThrows(IllegalArgumentException.class,
                () -> service.extender(1L, new ExtenderSuscripcionRequest(null, null, null)));
        assertThrows(IllegalArgumentException.class,
                () -> service.extender(1L, new ExtenderSuscripcionRequest(LocalDate.of(2026, 3, 1), null, null)));
    }

    @Test
    void cambiarPlanRecalculaPrecioYAuditaPlanChange() {
        Suscripcion sub = suscripcion(1L, plan(2L, "Profesional", new BigDecimal("29990"), null, "ACTIVE"),
                "ACTIVE", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 1, 1), "MONTHLY");
        when(suscripcionRepository.findById(1L)).thenReturn(Optional.of(sub));
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant(1L, "Empresa Demo")));
        when(planRepository.findById(3L)).thenReturn(Optional.of(plan(3L, "Empresa",
                new BigDecimal("59990"), new BigDecimal("599900"), "ACTIVE")));

        SuscripcionResponse response = service.cambiarPlan(1L,
                new CambiarPlanRequest(3L, "Crecimiento"));

        assertEquals("Empresa", response.planNombre());
        assertEquals(new BigDecimal("59990"), response.precio());
        verify(auditService).registrar(eq("PLAN_CHANGE"), eq("suscripciones"),
                any(), eq("suscripcion"), any(), contains("\"plan\": \"Profesional\""),
                contains("\"plan\": \"Empresa\""));
    }

    @Test
    void cambiarPlanAnualUsaPrecioAnual() {
        Suscripcion sub = suscripcion(1L, plan(2L, "Profesional", new BigDecimal("29990"), null, "ACTIVE"),
                "ACTIVE", LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1), "ANNUAL");
        when(suscripcionRepository.findById(1L)).thenReturn(Optional.of(sub));
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant(1L, "Empresa Demo")));
        when(planRepository.findById(3L)).thenReturn(Optional.of(plan(3L, "Empresa",
                new BigDecimal("59990"), new BigDecimal("599900"), "ACTIVE")));

        SuscripcionResponse response = service.cambiarPlan(1L, new CambiarPlanRequest(3L, null));

        assertEquals(new BigDecimal("599900"), response.precio());
    }

    @Test
    void cambiarPlanRechazaMismoPlan() {
        Suscripcion sub = suscripcion(1L, plan(2L, "Profesional", new BigDecimal("29990"), null, "ACTIVE"),
                "ACTIVE", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 1, 1), "MONTHLY");
        when(suscripcionRepository.findById(1L)).thenReturn(Optional.of(sub));
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant(1L, "Empresa Demo")));
        when(planRepository.findById(2L)).thenReturn(Optional.of(plan(2L, "Profesional",
                new BigDecimal("29990"), null, "ACTIVE")));

        assertThrows(SuscripcionConflictException.class,
                () -> service.cambiarPlan(1L, new CambiarPlanRequest(2L, null)));
    }

    @Test
    void suspenderCambiaEstadoYTenantYAudita() {
        Suscripcion sub = suscripcion(1L, plan(2L, "Profesional", new BigDecimal("29990"), null, "ACTIVE"),
                "ACTIVE", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 1, 1), "MONTHLY");
        Tenant tenant = tenant(1L, "Empresa Demo");
        when(suscripcionRepository.findById(1L)).thenReturn(Optional.of(sub));
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));

        SuscripcionResponse response = service.suspender(1L, "Mora de pago");

        assertEquals("SUSPENDED", response.estado());
        assertEquals("SUSPENDED", tenant.getStatus());
        assertFalse(tenant.isActivo());
        verify(tenantRepository).save(tenant);
        verify(auditService).registrar(eq("SUBSCRIPTION_SUSPENDED"), eq("suscripciones"),
                any(), eq("suscripcion"), any(), contains("\"estado\": \"ACTIVE\""),
                contains("\"motivo\": \"Mora de pago\""));
    }

    @Test
    void suspenderRechazaSuscripcionYaSuspendida() {
        Suscripcion sub = suscripcion(1L, plan(2L, "Profesional", new BigDecimal("29990"), null, "ACTIVE"),
                "SUSPENDED", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 1, 1), "MONTHLY");
        when(suscripcionRepository.findById(1L)).thenReturn(Optional.of(sub));
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant(1L, "Empresa Demo")));

        assertThrows(IllegalArgumentException.class, () -> service.suspender(1L, "Mora"));
    }

    @Test
    void reactivarCambiaEstadoYTenantYAudita() {
        Suscripcion sub = suscripcion(1L, plan(2L, "Profesional", new BigDecimal("29990"), null, "ACTIVE"),
                "SUSPENDED", LocalDate.of(2025, 12, 1), LocalDate.of(2025, 1, 1), "MONTHLY");
        Tenant tenant = tenant(1L, "Empresa Demo");
        tenant.setStatus("SUSPENDED");
        tenant.setActivo(false);
        when(suscripcionRepository.findById(1L)).thenReturn(Optional.of(sub));
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));

        SuscripcionResponse response = service.reactivar(1L, "Pago recibido");

        assertEquals("ACTIVE", response.estado());
        assertEquals("ACTIVE", tenant.getStatus());
        assertTrue(tenant.isActivo());
        verify(tenantRepository).save(tenant);
        verify(auditService).registrar(eq("SUBSCRIPTION_REACTIVATED"), eq("suscripciones"),
                any(), eq("suscripcion"), any(), any(), contains("\"motivo\": \"Pago recibido\""));
    }

    @Test
    void reactivarRechazaEstadoNoSuspendido() {
        Suscripcion sub = suscripcion(1L, plan(2L, "Profesional", new BigDecimal("29990"), null, "ACTIVE"),
                "ACTIVE", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 1, 1), "MONTHLY");
        when(suscripcionRepository.findById(1L)).thenReturn(Optional.of(sub));
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant(1L, "Empresa Demo")));

        assertThrows(IllegalArgumentException.class, () -> service.reactivar(1L, "Pago"));
    }

    @Test
    void listarFiltraYPagina() {
        Suscripcion sub = suscripcion(1L, plan(2L, "Profesional", new BigDecimal("29990"), null, "ACTIVE"),
                "ACTIVE", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 1, 1), "MONTHLY");
        Page<Suscripcion> pagina = new PageImpl<>(List.of(sub), PageRequest.of(0, 20), 1);
        when(suscripcionRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(pagina);
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant(1L, "Empresa Demo")));

        var resultado = service.listar(0, 20, "ACTIVE", 2L, 1L, 30);

        assertEquals(1, resultado.totalElements());
        assertEquals("Empresa Demo", resultado.content().get(0).empresaNombre());
        assertEquals("Profesional", resultado.content().get(0).planNombre());
    }

    @Test
    void obtenerDevuelveNombreDeEmpresa() {
        Suscripcion sub = suscripcion(1L, plan(2L, "Profesional", new BigDecimal("29990"), null, "ACTIVE"),
                "ACTIVE", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 1, 1), "MONTHLY");
        when(suscripcionRepository.findById(1L)).thenReturn(Optional.of(sub));
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant(1L, "Empresa Demo")));

        SuscripcionResponse response = service.obtener(1L);

        assertEquals("Empresa Demo", response.empresaNombre());
    }

    @Test
    void vencimientosDistribuyeLasVentanasPorRangos() {
        Suscripcion hoy = suscripcion(1L, plan(2L, "Profesional", new BigDecimal("29990"), null, "ACTIVE"),
                "ACTIVE", LocalDate.now(), LocalDate.of(2026, 1, 1), "MONTHLY");
        Suscripcion en3 = suscripcion(2L, plan(2L, "Profesional", new BigDecimal("29990"), null, "ACTIVE"),
                "ACTIVE", LocalDate.now().plusDays(2), LocalDate.of(2026, 1, 1), "MONTHLY");
        Suscripcion en7 = suscripcion(3L, plan(2L, "Profesional", new BigDecimal("29990"), null, "ACTIVE"),
                "TRIAL", LocalDate.now().plusDays(5), LocalDate.of(2026, 1, 1), "MONTHLY");
        Suscripcion en30 = suscripcion(4L, plan(2L, "Profesional", new BigDecimal("29990"), null, "ACTIVE"),
                "ACTIVE", LocalDate.now().plusDays(15), LocalDate.of(2026, 1, 1), "MONTHLY");
        Suscripcion vencida = suscripcion(5L, plan(2L, "Profesional", new BigDecimal("29990"), null, "ACTIVE"),
                "PAST_DUE", LocalDate.now().minusDays(3), LocalDate.of(2026, 1, 1), "MONTHLY");

        when(suscripcionRepository.findByEstadoNotAndFechaVencimiento("CANCELLED", LocalDate.now()))
                .thenReturn(List.of(hoy));
        when(suscripcionRepository.findByEstadoNotAndFechaVencimientoBetween(
                "CANCELLED", LocalDate.now().plusDays(1), LocalDate.now().plusDays(3))).thenReturn(List.of(en3));
        when(suscripcionRepository.findByEstadoNotAndFechaVencimientoBetween(
                "CANCELLED", LocalDate.now().plusDays(4), LocalDate.now().plusDays(7))).thenReturn(List.of(en7));
        when(suscripcionRepository.findByEstadoNotAndFechaVencimientoBetween(
                "CANCELLED", LocalDate.now().plusDays(8), LocalDate.now().plusDays(30))).thenReturn(List.of(en30));
        when(suscripcionRepository.findByEstadoInAndFechaVencimientoBefore(
                List.of("PAST_DUE", "EXPIRED", "ACTIVE"), LocalDate.now())).thenReturn(List.of(vencida));
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant(1L, "Empresa Demo")));

        VencimientosResponse resultado = service.vencimientos();

        assertEquals(1, resultado.vencenHoy().size());
        assertEquals(1, resultado.vencenEn3Dias().size());
        assertEquals(1, resultado.vencenEn7Dias().size());
        assertEquals(1, resultado.vencenEn30Dias().size());
        assertEquals(1, resultado.vencidas().size());
        assertEquals("Empresa Demo", resultado.vencenHoy().get(0).empresaNombre());
    }

    @Test
    void vencimientosNoIncluyeSuscripcionesCanceladas() {
        when(suscripcionRepository.findByEstadoNotAndFechaVencimiento("CANCELLED", LocalDate.now())).thenReturn(List.of());
        when(suscripcionRepository.findByEstadoNotAndFechaVencimientoBetween(
                "CANCELLED", LocalDate.now().plusDays(1), LocalDate.now().plusDays(3))).thenReturn(List.of());
        when(suscripcionRepository.findByEstadoNotAndFechaVencimientoBetween(
                "CANCELLED", LocalDate.now().plusDays(4), LocalDate.now().plusDays(7))).thenReturn(List.of());
        when(suscripcionRepository.findByEstadoNotAndFechaVencimientoBetween(
                "CANCELLED", LocalDate.now().plusDays(8), LocalDate.now().plusDays(30))).thenReturn(List.of());
        when(suscripcionRepository.findByEstadoInAndFechaVencimientoBefore(
                List.of("PAST_DUE", "EXPIRED", "ACTIVE"), LocalDate.now())).thenReturn(List.of());

        VencimientosResponse resultado = service.vencimientos();

        assertTrue(resultado.vencenHoy().isEmpty());
        assertTrue(resultado.vencidas().isEmpty());
    }
}