package cl.slimerp.catalogo;

import cl.slimerp.config.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subcategorias")
public class SubcategoriaController {

    private final SubcategoriaRepository subcategoriaRepository;
    private final CategoriaRepository categoriaRepository;

    public SubcategoriaController(SubcategoriaRepository subcategoriaRepository, CategoriaRepository categoriaRepository) {
        this.subcategoriaRepository = subcategoriaRepository;
        this.categoriaRepository = categoriaRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CATEGORIAS_VER')")
    public List<Subcategoria> listar(@RequestParam(required = false) Long categoriaId) {
        Long tenantId = TenantContext.getTenantId();
        return categoriaId != null
                ? subcategoriaRepository.findByTenantIdAndCategoriaIdAndActivoTrue(tenantId, categoriaId)
                : subcategoriaRepository.findByTenantIdAndActivoTrue(tenantId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CATEGORIAS_EDITAR')")
    public ResponseEntity<Subcategoria> crear(@Valid @RequestBody SubcategoriaRequest request) {
        Long tenantId = TenantContext.getTenantId();
        categoriaRepository.findByIdAndTenantIdAndActivoTrue(request.categoriaId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada: " + request.categoriaId()));

        Subcategoria subcategoria = Subcategoria.builder()
                .tenantId(tenantId)
                .categoriaId(request.categoriaId())
                .nombre(request.nombre())
                .build();
        return ResponseEntity.ok(subcategoriaRepository.save(subcategoria));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CATEGORIAS_EDITAR')")
    public ResponseEntity<Subcategoria> actualizar(@PathVariable Long id, @Valid @RequestBody SubcategoriaRequest request) {
        Long tenantId = TenantContext.getTenantId();
        categoriaRepository.findByIdAndTenantIdAndActivoTrue(request.categoriaId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada: " + request.categoriaId()));

        return subcategoriaRepository.findByIdAndTenantIdAndActivoTrue(id, tenantId)
                .map(subcategoria -> {
                    subcategoria.setCategoriaId(request.categoriaId());
                    subcategoria.setNombre(request.nombre());
                    return ResponseEntity.ok(subcategoriaRepository.save(subcategoria));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CATEGORIAS_EDITAR')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        return subcategoriaRepository.findByIdAndTenantIdAndActivoTrue(id, TenantContext.getTenantId())
                .map(subcategoria -> {
                    subcategoria.setActivo(false);
                    subcategoriaRepository.save(subcategoria);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
