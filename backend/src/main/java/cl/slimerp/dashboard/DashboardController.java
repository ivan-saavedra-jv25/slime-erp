package cl.slimerp.dashboard;

import cl.slimerp.config.TenantContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Sin @PreAuthorize: es un resumen de solo lectura visible para cualquier
// usuario autenticado del tenant, sin importar su rol (igual que en el
// proyecto de referencia, que tampoco restringe el dashboard por rol).
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public DashboardResponse resumen() {
        return dashboardService.resumen(TenantContext.getTenantId());
    }

    @GetMapping("/ventas-evolucion")
    public List<PuntoVenta> ventasEvolucion(@RequestParam(required = false) String rango) {
        return dashboardService.ventasEvolucion(TenantContext.getTenantId(), rango);
    }
}
