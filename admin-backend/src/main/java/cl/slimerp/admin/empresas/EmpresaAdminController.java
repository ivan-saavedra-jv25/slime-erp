package cl.slimerp.admin.empresas;

import cl.slimerp.admin.common.Paginated;
import cl.slimerp.admin.usuarios.UsuarioAdminResponse;
import cl.slimerp.admin.usuarios.UsuarioAdminService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/empresas")
@PreAuthorize("hasAuthority('PERM_EMPRESAS_VER')")
public class EmpresaAdminController {

    private final EmpresaAdminService empresaAdminService;
    private final UsuarioAdminService usuarioAdminService;

    public EmpresaAdminController(EmpresaAdminService empresaAdminService,
                                  UsuarioAdminService usuarioAdminService) {
        this.empresaAdminService = empresaAdminService;
        this.usuarioAdminService = usuarioAdminService;
    }

    @GetMapping
    public Paginated<EmpresaResponse> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String rut,
            @RequestParam(required = false) String razonSocial,
            @RequestParam(required = false) String nombreComercial,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String plan,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaCreacionDesde) {
        return empresaAdminService.listar(page, limit, id, rut, razonSocial, nombreComercial,
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

    @GetMapping("/{id}/usuarios")
    public List<UsuarioAdminResponse> usuariosDeEmpresa(@PathVariable Long id) {
        return usuarioAdminService.listar(id, null);
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