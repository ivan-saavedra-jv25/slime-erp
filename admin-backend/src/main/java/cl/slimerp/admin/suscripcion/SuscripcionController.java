package cl.slimerp.admin.suscripcion;

import cl.slimerp.admin.common.Paginated;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/suscripciones")
@PreAuthorize("hasAuthority('PERM_SUSCRIPCIONES_VER')")
public class SuscripcionController {

    private final SuscripcionService suscripcionService;

    public SuscripcionController(SuscripcionService suscripcionService) {
        this.suscripcionService = suscripcionService;
    }

    @GetMapping
    public Paginated<SuscripcionResponse> listar(@RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int limit,
                                                 @RequestParam(required = false) String estado,
                                                 @RequestParam(required = false) Long planId,
                                                 @RequestParam(required = false) Long empresaId,
                                                 @RequestParam(required = false) Integer proximasAVencerDias) {
        return suscripcionService.listar(page, limit, estado, planId, empresaId, proximasAVencerDias);
    }

    @GetMapping("/expiring")
    public VencimientosResponse expiring() {
        return suscripcionService.vencimientos();
    }

    @GetMapping("/{id}")
    public SuscripcionResponse obtener(@PathVariable Long id) {
        return suscripcionService.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_SUSCRIPCIONES_EDITAR')")
    public SuscripcionResponse crear(@Valid @RequestBody SuscripcionRequest request) {
        return suscripcionService.crear(request);
    }

    @PostMapping("/{id}/extend")
    @PreAuthorize("hasAuthority('PERM_SUSCRIPCIONES_EDITAR')")
    public SuscripcionResponse extender(@PathVariable Long id, @Valid @RequestBody ExtenderSuscripcionRequest request) {
        return suscripcionService.extender(id, request);
    }

    @PostMapping("/{id}/cambiar-plan")
    @PreAuthorize("hasAuthority('PERM_SUSCRIPCIONES_EDITAR')")
    public SuscripcionResponse cambiarPlan(@PathVariable Long id, @Valid @RequestBody CambiarPlanRequest request) {
        return suscripcionService.cambiarPlan(id, request);
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasAuthority('PERM_SUSCRIPCIONES_EDITAR')")
    public SuscripcionResponse suspender(@PathVariable Long id, @Valid @RequestBody SuscripcionEstadoRequest request) {
        return suscripcionService.suspender(id, request.motivo());
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasAuthority('PERM_SUSCRIPCIONES_EDITAR')")
    public SuscripcionResponse reactivar(@PathVariable Long id, @Valid @RequestBody SuscripcionEstadoRequest request) {
        return suscripcionService.reactivar(id, request.motivo());
    }
}