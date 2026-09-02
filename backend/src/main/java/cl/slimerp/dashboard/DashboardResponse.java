package cl.slimerp.dashboard;

import java.util.List;

public record DashboardResponse(KpiResumen kpis, List<Alerta> alertas, List<VentaResumenItem> ultimasVentas) {
}
