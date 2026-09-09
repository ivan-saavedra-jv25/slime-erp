package cl.slimerp.inventario;

import cl.slimerp.common.PaginaResponse;
import cl.slimerp.config.TenantContext;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
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

    // Listado paginado y con búsqueda server-side, usado por la pantalla de
    // mantenedor de Bodegas. El listado completo (arriba) se mantiene para
    // los selectores en memoria de Movimientos/Ventas/Compras/etc.
    @GetMapping("/pagina")
    @PreAuthorize("hasAuthority('BODEGAS_VER')")
    public PaginaResponse<Bodega> listarPagina(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamano) {
        String busqueda = "%" + (q == null ? "" : q.trim().toLowerCase()) + "%";
        // Más nuevas primero: el id autoincremental refleja el orden real de creación.
        var pageable = PageRequest.of(pagina, tamano, Sort.by("id").descending());
        return PaginaResponse.de(bodegaRepository.buscar(TenantContext.getTenantId(), busqueda, pageable));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('BODEGAS_EDITAR')")
    public ResponseEntity<Bodega> crear(@Valid @RequestBody BodegaRequest request) {
        Bodega bodega = Bodega.builder()
                .tenantId(TenantContext.getTenantId())
                .nombre(request.nombre())
                .tipo(request.tipo())
                .build();
        return ResponseEntity.ok(bodegaRepository.save(bodega));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('BODEGAS_EDITAR')")
    public ResponseEntity<Bodega> actualizar(@PathVariable Long id, @Valid @RequestBody BodegaRequest request) {
        return bodegaRepository.findByIdAndTenantIdAndActivoTrue(id, TenantContext.getTenantId())
                .map(bodega -> {
                    bodega.setNombre(request.nombre());
                    bodega.setTipo(request.tipo());
                    return ResponseEntity.ok(bodegaRepository.save(bodega));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Marca una bodega como la principal del tenant y le quita la marca a la
    // que la tuviera antes (por regla de negocio solo puede haber una). La
    // bodega anterior vuelve a tipo BODEGAJE para no dejar dos con tipo
    // PRINCIPAL a la vez.
    @PutMapping("/{id}/principal")
    @PreAuthorize("hasAuthority('BODEGAS_EDITAR')")
    @Transactional
    public ResponseEntity<Bodega> marcarPrincipal(@PathVariable Long id) {
        Long tenantId = TenantContext.getTenantId();
        Bodega nueva = bodegaRepository.findByIdAndTenantIdAndActivoTrue(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Bodega no encontrada: " + id));

        bodegaRepository.findByTenantIdAndPrincipalTrueAndActivoTrue(tenantId)
                .filter(actual -> !actual.getId().equals(id))
                .ifPresent(actual -> {
                    actual.setPrincipal(false);
                    actual.setTipo(TipoBodega.BODEGAJE);
                    bodegaRepository.save(actual);
                });

        nueva.setPrincipal(true);
        nueva.setTipo(TipoBodega.PRINCIPAL);
        return ResponseEntity.ok(bodegaRepository.save(nueva));
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
