package cl.slimerp.admin;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/empresas")
@PreAuthorize("hasAuthority('EMPRESAS_ADMINISTRAR')")
public class EmpresaAdminController {

    private final EmpresaAdminService empresaAdminService;

    public EmpresaAdminController(EmpresaAdminService empresaAdminService) {
        this.empresaAdminService = empresaAdminService;
    }

    @GetMapping
    public List<EmpresaResponse> listar() {
        return empresaAdminService.listar().stream().map(EmpresaResponse::desde).toList();
    }

    @PostMapping
    public ResponseEntity<EmpresaResponse> crear(@Valid @RequestBody CrearEmpresaRequest request) {
        return ResponseEntity.ok(EmpresaResponse.desde(empresaAdminService.crear(request)));
    }

    @PatchMapping("/{id}/activar")
    public EmpresaResponse activar(@PathVariable Long id) {
        return EmpresaResponse.desde(empresaAdminService.activar(id));
    }

    @PatchMapping("/{id}/desactivar")
    public EmpresaResponse desactivar(@PathVariable Long id) {
        return EmpresaResponse.desde(empresaAdminService.desactivar(id));
    }
}
