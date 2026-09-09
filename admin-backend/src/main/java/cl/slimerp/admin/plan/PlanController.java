package cl.slimerp.admin.plan;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/planes")
@PreAuthorize("hasAuthority('PERM_PLANES_VER')")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    public List<PlanResponse> listar() {
        return planService.listar();
    }

    @GetMapping("/{id}")
    public PlanResponse obtener(@PathVariable Long id) {
        return planService.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_PLANES_EDITAR')")
    public ResponseEntity<PlanResponse> crear(@Valid @RequestBody PlanRequest request) {
        return ResponseEntity.ok(planService.crear(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_PLANES_EDITAR')")
    public PlanResponse actualizar(@PathVariable Long id, @Valid @RequestBody PlanRequest request) {
        return planService.actualizar(id, request);
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAuthority('PERM_PLANES_EDITAR')")
    public PlanResponse cambiarEstado(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return planService.cambiarEstado(id, body.get("estado"));
    }
}