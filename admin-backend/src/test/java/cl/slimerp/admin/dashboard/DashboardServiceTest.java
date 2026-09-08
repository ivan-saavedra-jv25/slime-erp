package cl.slimerp.admin.dashboard;

import cl.slimerp.admin.alerta.AlertaRepository;
import cl.slimerp.admin.auditoria.AuditLog;
import cl.slimerp.admin.auditoria.AuditLogRepository;
import cl.slimerp.admin.pago.PagoRepository;
import cl.slimerp.admin.suscripcion.SuscripcionRepository;
import cl.slimerp.admin.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DashboardServiceTest {

    private TenantRepository tenantRepository;
    private SuscripcionRepository suscripcionRepository;
    private PagoRepository pagoRepository;
    private AlertaRepository alertaRepository;
    private AuditLogRepository auditLogRepository;
    private DashboardService service;

    @BeforeEach
    void setUp() {
        tenantRepository = mock(TenantRepository.class);
        suscripcionRepository = mock(SuscripcionRepository.class);
        pagoRepository = mock(PagoRepository.class);
        alertaRepository = mock(AlertaRepository.class);
        auditLogRepository = mock(AuditLogRepository.class);
        service = new DashboardService(
                tenantRepository, suscripcionRepository, pagoRepository, alertaRepository, auditLogRepository);
    }

    @Test
    void obtieneKpisPorEstado() {
        when(tenantRepository.count()).thenReturn(50L);
        when(tenantRepository.countByActivoTrue()).thenReturn(40L);
        when(tenantRepository.countByStatus("TRIAL")).thenReturn(5L);
        when(tenantRepository.countByStatus("SUSPENDED")).thenReturn(3L);
        when(tenantRepository.countByStatus("EXPIRED")).thenReturn(2L);
        when(tenantRepository.countByStatus("CANCELLED")).thenReturn(1L);
        when(tenantRepository.countByFechaAltaBetween(any(), any())).thenReturn(7L);

        DashboardResponse.Companies c = service.obtener().companies();

        assertEquals(50L, c.total());
        assertEquals(40L, c.activas());
        assertEquals(5L, c.prueba());
        assertEquals(3L, c.suspendidas());
        assertEquals(2L, c.vencidas());
        assertEquals(1L, c.canceladas());
        assertEquals(7L, c.nuevasPeriodo());
    }

    @Test
    void nuevasPeriodoUsaInicioDelMesActual() {
        service.obtener();
        LocalDateTime inicioMes = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        verify(tenantRepository, atLeastOnce()).countByFechaAltaBetween(eq(inicioMes), any(LocalDateTime.class));
    }

    @Test
    void suscripcionesActivasPorVencerYPorPlan() {
        when(suscripcionRepository.countByEstado("ACTIVE")).thenReturn(20L);
        when(suscripcionRepository.countByEstadoAndFechaVencimientoBetween(eq("ACTIVE"), any(), any()))
                .thenReturn(3L);
        when(suscripcionRepository.countByEstadoIn(any())).thenReturn(4L);
        when(suscripcionRepository.contarPorPlan("ACTIVE")).thenReturn(List.of(
                new Object[]{"Basico", 8L},
                new Object[]{"Profesional", 12L}));

        DashboardResponse.Subscriptions s = service.obtener().subscriptions();

        assertEquals(20L, s.activas());
        assertEquals(3L, s.porVencer());
        assertEquals(4L, s.vencidas());
        assertEquals(2, s.porPlan().size());
        assertEquals("Basico", s.porPlan().get(0).plan());
        assertEquals(8L, s.porPlan().get(0).cantidad());
    }

    @Test
    void pagosPendientesYVencidos() {
        when(pagoRepository.countByEstado("PENDING")).thenReturn(6L);
        when(pagoRepository.countByEstadoAndCreadoEnBefore(eq("PENDING"), any())).thenReturn(1L);

        DashboardResponse.Payments p = service.obtener().payments();

        assertEquals(6L, p.pendientes());
        assertEquals(1L, p.vencidos());
    }

    @Test
    void alertasCriticasYAdvertencias() {
        when(alertaRepository.countBySeverityAndStatus("CRITICAL", "OPEN")).thenReturn(2L);
        when(alertaRepository.countBySeverityAndStatus("WARNING", "OPEN")).thenReturn(5L);

        DashboardResponse.Alerts a = service.obtener().alerts();

        assertEquals(2L, a.criticas());
        assertEquals(5L, a.advertencias());
    }

    @Test
    void evolucionEmpresasTieneSeisMeses() {
        when(tenantRepository.countByFechaAltaBetween(any(), any())).thenReturn(1L);
        List<DashboardResponse.EvolucionEmpresa> evolucion = service.obtener().evolucionEmpresas();
        assertEquals(6, evolucion.size());
    }

    @Test
    void actividadRecienteSonLosUltimosAuditLogs() {
        AuditLog log = AuditLog.builder()
                .action("EMPRESA_ESTADO_CAMBIADO")
                .modulo("empresas")
                .build();
        when(auditLogRepository.findTop10ByOrderByCreadoEnDesc(any(Pageable.class)))
                .thenReturn(List.of(log));

        List<DashboardResponse.ActividadReciente> actividad = service.obtener().actividadReciente();

        assertEquals(1, actividad.size());
        assertEquals("EMPRESA_ESTADO_CAMBIADO", actividad.get(0).action());
        assertEquals("empresas", actividad.get(0).modulo());
    }
}