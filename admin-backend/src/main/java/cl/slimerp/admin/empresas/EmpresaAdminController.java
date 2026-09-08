package cl.slimerp.admin.empresas;

import cl.slimerp.admin.common.Paginated;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/empresas")
@PreAuthorize("hasAuthority('PERM_EMPRESAS_VER')")
public class EmpresaAdminController {

    private final EmpresaAdminService empresaAdminService;

    public EmpresaAdminController(EmpresaAdminService empresaAdminService) {
        this.empresaAdminService = empresaAdminService;
    }

    @GetMapping
    public Paginated<EmpresaResponse> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String rut,
            @RequestParam(required = false) String razonSocial,
            @RequestParam(required = false) String nombreComercial,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String plan,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaCreacionDesde) {
        return empresaAdminService.listar(page, limit, rut, razonSocial, nombreComercial,
                estado, plan, fechaCreacionDesde);
    }

    @GetMapping("/stats")
    public ResponseEntity<Object> stats() {
        return ResponseEntity.ok(Map.of(
                "empresasActivas", empresaAdminService.contarEmpresasActivas(),
                "totalEmpresas", empresaAdminService.contarEmpresas()));
    }

    @GetMapping("/{id}")
    public EmpresaDetalleResponse detalle(@PathVariable Long id) {
        return empresaAdminService.detalle(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_EMPRESAS_EDITAR')")
    public ResponseEntity<EmpresaResponse> crear(@Valid @RequestBody CrearEmpresaRequest request) {
        return ResponseEntity.ok(EmpresaResponse.desde(
                empresaAdminService.crear(request), 1L, java.math.BigDecimal.ZERO));
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAuthority('PERM_EMPRESAS_EDITAR')")
    public EmpresaResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambiarEstadoRequest request) {
        return empresaAdminService.conKpis(empresaAdminService.cambiarEstado(id, request.estado(), request.motivo()));
    }

    @PatchMapping("/{id}/activar")
    public EmpresaResponse activar(@PathVariable Long id) {
        return empresaAdminService.conKpis(empresaAdminService.activar(id));
    }

    @PatchMapping("/{id}/desactivar")
    public EmpresaResponse desactivar(@PathVariable Long id) {
        return empresaAdminService.conKpis(empresaAdminService.desactivar(id));
    }
}