package cl.slimerp.catalogo;

import cl.slimerp.config.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categorias")
public class CategoriaController {

    private final CategoriaRepository categoriaRepository;

    public CategoriaController(CategoriaRepository categoriaRepository) {
        this.categoriaRepository = categoriaRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CATEGORIAS_VER')")
    public List<Categoria> listar() {
        return categoriaRepository.findByTenantIdAndActivoTrue(TenantContext.getTenantId());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CATEGORIAS_EDITAR')")
    public ResponseEntity<Categoria> crear(@Valid @RequestBody CategoriaRequest request) {
        Categoria categoria = Categoria.builder()
                .tenantId(TenantContext.getTenantId())
                .nombre(request.nombre())
                .build();
        return ResponseEntity.ok(categoriaRepository.save(categoria));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CATEGORIAS_EDITAR')")
    public ResponseEntity<Categoria> actualizar(@PathVariable Long id, @Valid @RequestBody CategoriaRequest request) {
        return categoriaRepository.findByIdAndTenantIdAndActivoTrue(id, TenantContext.getTenantId())
                .map(categoria -> {
                    categoria.setNombre(request.nombre());
                    return ResponseEntity.ok(categoriaRepository.save(categoria));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CATEGORIAS_EDITAR')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        return categoriaRepository.findByIdAndTenantIdAndActivoTrue(id, TenantContext.getTenantId())
                .map(categoria -> {
                    categoria.setActivo(false);
                    categoriaRepository.save(categoria);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
