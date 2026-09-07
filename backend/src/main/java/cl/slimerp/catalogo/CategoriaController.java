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

    // Listado paginado y con búsqueda server-side, usado por la pantalla de
    // mantenedor de Categorías. El listado completo (arriba) se mantiene para
    // los buscadores en memoria de Productos/etc.
    @GetMapping("/pagina")
    @PreAuthorize("hasAuthority('CATEGORIAS_VER')")
    public PaginaResponse<Categoria> listarPagina(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamano) {
        String busqueda = "%" + (q == null ? "" : q.trim().toLowerCase()) + "%";
        var pageable = PageRequest.of(pagina, tamano, Sort.by("nombre").ascending());
        return PaginaResponse.de(categoriaRepository.buscar(TenantContext.getTenantId(), busqueda, pageable));
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
