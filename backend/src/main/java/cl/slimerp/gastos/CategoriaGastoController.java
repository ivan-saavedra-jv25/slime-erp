package cl.slimerp.gastos;

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
@RequestMapping("/api/gastos/categorias")
public class CategoriaGastoController {

    private final CategoriaGastoRepository categoriaGastoRepository;

    public CategoriaGastoController(CategoriaGastoRepository categoriaGastoRepository) {
        this.categoriaGastoRepository = categoriaGastoRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TESORERIA_VER')")
    public List<CategoriaGasto> listar() {
        return categoriaGastoRepository.findByTenantIdAndActivoTrue(TenantContext.getTenantId());
    }

    @GetMapping("/pagina")
    @PreAuthorize("hasAuthority('TESORERIA_VER')")
    public PaginaResponse<CategoriaGasto> listarPagina(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamano) {
        String busqueda = "%" + (q == null ? "" : q.trim().toLowerCase()) + "%";
        var pageable = PageRequest.of(pagina, tamano, Sort.by("nombre").ascending());
        return PaginaResponse.de(categoriaGastoRepository.buscar(TenantContext.getTenantId(), busqueda, pageable));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('TESORERIA_EDITAR')")
    public ResponseEntity<CategoriaGasto> crear(@Valid @RequestBody CategoriaGastoRequest request) {
        CategoriaGasto categoria = CategoriaGasto.builder()
                .tenantId(TenantContext.getTenantId())
                .nombre(request.nombre())
                .build();
        return ResponseEntity.ok(categoriaGastoRepository.save(categoria));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('TESORERIA_EDITAR')")
    public ResponseEntity<CategoriaGasto> actualizar(@PathVariable Long id, @Valid @RequestBody CategoriaGastoRequest request) {
        return categoriaGastoRepository.findByIdAndTenantIdAndActivoTrue(id, TenantContext.getTenantId())
                .map(categoria -> {
                    categoria.setNombre(request.nombre());
                    return ResponseEntity.ok(categoriaGastoRepository.save(categoria));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('TESORERIA_EDITAR')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        return categoriaGastoRepository.findByIdAndTenantIdAndActivoTrue(id, TenantContext.getTenantId())
                .map(categoria -> {
                    categoria.setActivo(false);
                    categoriaGastoRepository.save(categoria);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
