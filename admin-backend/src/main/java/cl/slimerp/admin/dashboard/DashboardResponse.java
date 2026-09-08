package cl.slimerp.admin.dashboard;

import java.time.LocalDateTime;
import java.util.List;

public record DashboardResponse(
        Companies companies,
        Subscriptions subscriptions,
        Payments payments,
        Dte dte,
        Alerts alerts,
        List<EvolucionEmpresa> evolucionEmpresas,
        List<ActividadReciente> actividadReciente,
        LocalDateTime generadoEn) {

    public record Companies(long total, long activas, long prueba, long suspendidas,
                            long vencidas, long canceladas, long nuevasPeriodo) {
    }

    public record Subscriptions(long activas, long porVencer, long vencidas, List<PorPlan> porPlan) {
    }

    public record PorPlan(String plan, long cantidad) {
    }

    public record Payments(long pendientes, long vencidos) {
    }

    public record Dte(long emitidos, long aceptados, long rechazados, long pendientes, long errores) {
    }

    public record Alerts(long criticas, long advertencias) {
    }

    public record EvolucionEmpresa(String mes, long cantidad) {
    }

    public record ActividadReciente(String action, String modulo, String empresa, String admin,
                                    LocalDateTime fecha) {
    }
}