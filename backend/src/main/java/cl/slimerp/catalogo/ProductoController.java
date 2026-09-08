package cl.slimerp.catalogo;

import cl.slimerp.common.PaginaResponse;
import cl.slimerp.config.TenantContext;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    private final ProductoRepository productoRepository;

    public ProductoController(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCTOS_VER')")
    public List<Producto> listar() {
        return productoRepository.findByTenantIdAndActivoTrue(TenantContext.getTenantId());
    }

    // Listado paginado y con búsqueda server-side, usado por la pantalla de
    // mantenedor de Productos. El listado completo (arriba) se mantiene para
    // los buscadores en memoria de Ventas/Compras/Bodegas/etc.
    @GetMapping("/pagina")
    @PreAuthorize("hasAuthority('PRODUCTOS_VER')")
    public PaginaResponse<Producto> listarPagina(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamano) {
        String busqueda = "%" + (q == null ? "" : q.trim().toLowerCase()) + "%";
        var pageable = PageRequest.of(pagina, tamano, Sort.by("nombre").ascending());
        return PaginaResponse.de(productoRepository.buscar(TenantContext.getTenantId(), busqueda, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCTOS_VER')")
    public ResponseEntity<Producto> obtener(@PathVariable Long id) {
        return productoRepository.findByIdAndTenantIdAndActivoTrue(id, TenantContext.getTenantId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PRODUCTOS_EDITAR')")
    public ResponseEntity<Producto> crear(@Valid @RequestBody ProductoRequest request) {
        Long tenantId = TenantContext.getTenantId();
        validarCodigoBarraUnico(tenantId, request.codigoBarra(), null);

        Producto producto = Producto.builder()
                .tenantId(tenantId)
                .sku(request.sku())
                .codigoBarra(normalizarCodigoBarra(request.codigoBarra()))
                .nombre(request.nombre())
                .descripcion(request.descripcion())
                .categoriaId(request.categoriaId())
                .subcategoriaId(request.subcategoriaId())
                .precioVenta(request.precioVenta())
                .precioCompra(request.precioCompra() != null ? request.precioCompra() : BigDecimal.ZERO)
                .stockMinimo(request.stockMinimo() != null ? request.stockMinimo() : BigDecimal.ZERO)
                .build();
        return ResponseEntity.ok(productoRepository.save(producto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCTOS_EDITAR')")
    public ResponseEntity<Producto> actualizar(@PathVariable Long id, @Valid @RequestBody ProductoRequest request) {
        Long tenantId = TenantContext.getTenantId();
        return productoRepository.findByIdAndTenantIdAndActivoTrue(id, tenantId)
                .map(producto -> {
                    validarCodigoBarraUnico(tenantId, request.codigoBarra(), id);
                    producto.setSku(request.sku());
                    producto.setCodigoBarra(normalizarCodigoBarra(request.codigoBarra()));
                    producto.setNombre(request.nombre());
                    producto.setDescripcion(request.descripcion());
                    producto.setCategoriaId(request.categoriaId());
                    producto.setSubcategoriaId(request.subcategoriaId());
                    producto.setPrecioVenta(request.precioVenta());
                    producto.setPrecioCompra(request.precioCompra() != null ? request.precioCompra() : BigDecimal.ZERO);
                    producto.setStockMinimo(request.stockMinimo() != null ? request.stockMinimo() : BigDecimal.ZERO);
                    return ResponseEntity.ok(productoRepository.save(producto));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCTOS_EDITAR')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        return productoRepository.findByIdAndTenantIdAndActivoTrue(id, TenantContext.getTenantId())
                .map(producto -> {
                    producto.setActivo(false);
                    productoRepository.save(producto);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // El código de barra es opcional; solo se valida unicidad cuando viene con contenido.
    private String normalizarCodigoBarra(String codigoBarra) {
        if (codigoBarra == null) return null;
        String limpio = codigoBarra.trim();
        return limpio.isEmpty() ? null : limpio;
    }

    private void validarCodigoBarraUnico(Long tenantId, String codigoBarra, Long idExcluido) {
        String normalizado = normalizarCodigoBarra(codigoBarra);
        if (normalizado == null) return;
        boolean existe = idExcluido == null
                ? productoRepository.existsByTenantIdAndCodigoBarra(tenantId, normalizado)
                : productoRepository.existsByTenantIdAndCodigoBarraAndIdNot(tenantId, normalizado, idExcluido);
        if (existe) {
            throw new ProductoConflictException("Ya existe un producto con el código de barra " + normalizado);
        }
    }
}
