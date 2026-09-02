package cl.slimerp.compras;

import cl.slimerp.config.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/compras")
public class CompraController {

    private final CompraRepository compraRepository;
    private final CompraService compraService;

    public CompraController(CompraRepository compraRepository, CompraService compraService) {
        this.compraRepository = compraRepository;
        this.compraService = compraService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('COMPRAS_VER')")
    public List<Compra> listar() {
        return compraRepository.findByTenantIdAndActivoTrueOrderByFechaDesc(TenantContext.getTenantId());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('COMPRAS_VER')")
    public ResponseEntity<Compra> obtener(@PathVariable Long id) {
        return compraRepository.findByIdAndTenantIdAndActivoTrue(id, TenantContext.getTenantId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('COMPRAS_EDITAR')")
    public ResponseEntity<Compra> crear(@Valid @RequestBody CompraRequest request) {
        return ResponseEntity.ok(compraService.crear(request));
    }
}
