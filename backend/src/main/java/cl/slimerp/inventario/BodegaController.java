package cl.slimerp.inventario;

import cl.slimerp.config.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bodegas")
public class BodegaController {

    private final BodegaRepository bodegaRepository;

    public BodegaController(BodegaRepository bodegaRepository) {
        this.bodegaRepository = bodegaRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('BODEGAS_VER')")
    public List<Bodega> listar() {
        return bodegaRepository.findByTenantIdAndActivoTrue(TenantContext.getTenantId());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('BODEGAS_EDITAR')")
    public ResponseEntity<Bodega> crear(@Valid @RequestBody BodegaRequest request) {
        Bodega bodega = Bodega.builder()
                .tenantId(TenantContext.getTenantId())
                .nombre(request.nombre())
                .build();
        return ResponseEntity.ok(bodegaRepository.save(bodega));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('BODEGAS_EDITAR')")
    public ResponseEntity<Bodega> actualizar(@PathVariable Long id, @Valid @RequestBody BodegaRequest request) {
        return bodegaRepository.findByIdAndTenantIdAndActivoTrue(id, TenantContext.getTenantId())
                .map(bodega -> {
                    bodega.setNombre(request.nombre());
                    return ResponseEntity.ok(bodegaRepository.save(bodega));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('BODEGAS_EDITAR')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        return bodegaRepository.findByIdAndTenantIdAndActivoTrue(id, TenantContext.getTenantId())
                .map(bodega -> {
                    if (bodega.isPrincipal()) {
                        throw new IllegalArgumentException("No se puede eliminar la bodega principal");
                    }
                    bodega.setActivo(false);
                    bodegaRepository.save(bodega);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
