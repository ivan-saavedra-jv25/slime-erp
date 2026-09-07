package cl.slimerp.catalogo;

import cl.slimerp.common.PaginaResponse;
import cl.slimerp.config.TenantContext;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

    // Listado paginado y con búsqueda server-side de las subcategorías de una
    // categoría, usado por la pantalla de mantenedor de Categorías. El
    // listado completo (arriba) se mantiene para los buscadores en memoria.
    @GetMapping("/pagina")
    @PreAuthorize("hasAuthority('CATEGORIAS_VER')")
    public PaginaResponse<Subcategoria> listarPagina(
            @RequestParam Long categoriaId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamano) {
        String busqueda = "%" + (q == null ? "" : q.trim().toLowerCase()) + "%";
        var pageable = PageRequest.of(pagina, tamano, Sort.by("nombre").ascending());
        return PaginaResponse.de(
                subcategoriaRepository.buscar(TenantContext.getTenantId(), categoriaId, busqueda, pageable));
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
