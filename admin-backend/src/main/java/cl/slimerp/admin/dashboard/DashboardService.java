package cl.slimerp.admin.dashboard;

import cl.slimerp.admin.alerta.AlertaRepository;
import cl.slimerp.admin.auditoria.AuditLog;
import cl.slimerp.admin.auditoria.AuditLogRepository;
import cl.slimerp.admin.pago.PagoRepository;
import cl.slimerp.admin.suscripcion.SuscripcionRepository;
import cl.slimerp.admin.tenant.TenantRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class DashboardService {

    private static final List<String> ESTADOS_VENCIDAS = List.of("EXPIRED", "PAST_DUE", "SUSPENDED");
    private static final LocalDate HOY = LocalDate.now();

    private final TenantRepository tenantRepository;
    private final SuscripcionRepository suscripcionRepository;
    private final PagoRepository pagoRepository;
    private final AlertaRepository alertaRepository;
    private final AuditLogRepository auditLogRepository;

    public DashboardService(TenantRepository tenantRepository,
                            SuscripcionRepository suscripcionRepository,
                            PagoRepository pagoRepository,
                            AlertaRepository alertaRepository,
                            AuditLogRepository auditLogRepository) {
        this.tenantRepository = tenantRepository;
        this.suscripcionRepository = suscripcionRepository;
        this.pagoRepository = pagoRepository;
        this.alertaRepository = alertaRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResponse obtener() {
        return new DashboardResponse(
                empresas(),
                suscripciones(),
                pagos(),
                dte(),
                alertas(),
                evolucionEmpresas(),
                actividadReciente(),
                LocalDateTime.now());
    }

    private DashboardResponse.Companies empresas() {
        LocalDateTime inicioMes = YearMonth.now().atDay(1).atStartOfDay();
        return new DashboardResponse.Companies(
                tenantRepository.count(),
                tenantRepository.countByActivoTrue(),
                tenantRepository.countByStatus("TRIAL"),
                tenantRepository.countByStatus("SUSPENDED"),
                tenantRepository.countByStatus("EXPIRED"),
                tenantRepository.countByStatus("CANCELLED"),
                tenantRepository.countByFechaAltaBetween(inicioMes, LocalDateTime.now()));
    }

    private DashboardResponse.Subscriptions suscripciones() {
        long activas = suscripcionRepository.countByEstado("ACTIVE");
        long porVencer = suscripcionRepository.countByEstadoAndFechaVencimientoBetween(
                "ACTIVE", HOY, HOY.plusDays(30));
        long vencidas = suscripcionRepository.countByEstadoIn(ESTADOS_VENCIDAS);

        List<DashboardResponse.PorPlan> porPlan = suscripcionRepository
                .contarPorPlan("ACTIVE")
                .stream()
                .map(fila -> new DashboardResponse.PorPlan((String) fila[0], (Long) fila[1]))
                .toList();

        return new DashboardResponse.Subscriptions(activas, porVencer, vencidas, porPlan);
    }

    private DashboardResponse.Payments pagos() {
        return new DashboardResponse.Payments(
                pagoRepository.countByEstado("PENDING"),
                pagoRepository.countByEstadoAndCreadoEnBefore("PENDING", LocalDateTime.now().minusDays(30)));
    }

    private DashboardResponse.Dte dte() {
        // Lectura real en Task 15. En esta fase la plataforma aún no consolida DTE.
        return new DashboardResponse.Dte(0, 0, 0, 0, 0);
    }

    private DashboardResponse.Alerts alertas() {
        return new DashboardResponse.Alerts(
                alertaRepository.countBySeverityAndStatus("CRITICAL", "OPEN"),
                alertaRepository.countBySeverityAndStatus("WARNING", "OPEN"));
    }

    private List<DashboardResponse.EvolucionEmpresa> evolucionEmpresas() {
        List<DashboardResponse.EvolucionEmpresa> resultado = new ArrayList<>();
        YearMonth mes = YearMonth.now();
        DateTimeFormatter formato = DateTimeFormatter.ofPattern("yyyy-MM");
        for (int i = 0; i < 6; i++) {
            YearMonth actual = mes.minusMonths(5 - i);
            long cantidad = tenantRepository.countByFechaAltaBetween(
                    actual.atDay(1).atStartOfDay(),
                    actual.atEndOfMonth().plusDays(1).atStartOfDay());
            resultado.add(new DashboardResponse.EvolucionEmpresa(
                    actual.atDay(1).format(formato), cantidad));
        }
        return resultado;
    }

    private List<DashboardResponse.ActividadReciente> actividadReciente() {
        List<AuditLog> logs = auditLogRepository.findTop10ByOrderByCreadoEnDesc(Pageable.ofSize(10));
        return logs.stream()
                .map(log -> new DashboardResponse.ActividadReciente(
                        log.getAction(),
                        log.getModulo(),
                        log.getCompany() != null ? log.getCompany().getNombre() : null,
                        log.getAdminUsuario() != null ? log.getAdminUsuario().getNombre() : null,
                        log.getCreadoEn()))
                .toList();
    }
}