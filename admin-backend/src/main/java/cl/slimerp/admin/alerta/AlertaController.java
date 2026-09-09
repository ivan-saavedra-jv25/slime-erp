package cl.slimerp.admin.alerta;

import cl.slimerp.admin.common.Paginated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/alerts")
@PreAuthorize("hasAuthority('PERM_ALERTAS_VER')")
public class AlertaController {

    private final AlertaService alertaService;

    public AlertaController(AlertaService alertaService) {
        this.alertaService = alertaService;
    }

    @GetMapping
    public Paginated<AlertaResponse> listar(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int limit,
                                            @RequestParam(required = false) String severity,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) Long companyId,
                                            @RequestParam(required = false) String tipo) {
        return alertaService.listar(page, limit, severity, status, companyId, tipo);
    }

    @GetMapping("/summary")
    public Map<String, Long> summary() {
        return alertaService.resumenAbiertas();
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("hasAuthority('PERM_ALERTAS_EDITAR')")
    public AlertaResponse marcarLeida(@PathVariable Long id) {
        return alertaService.marcarLeida(id);
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAuthority('PERM_ALERTAS_EDITAR')")
    public AlertaResponse resolver(@PathVariable Long id, @RequestBody(required = false) ResolverAlertaRequest request) {
        return alertaService.resolver(id, request == null ? null : request.motivo());
    }
}