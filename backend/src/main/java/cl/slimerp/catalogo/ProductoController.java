package cl.slimerp.catalogo;

import cl.slimerp.config.TenantContext;
import jakarta.validation.Valid;
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
        Producto producto = Producto.builder()
                .tenantId(TenantContext.getTenantId())
                .sku(request.sku())
                .nombre(request.nombre())
                .descripcion(request.descripcion())
                .precioVenta(request.precioVenta())
                .precioCompra(request.precioCompra() != null ? request.precioCompra() : BigDecimal.ZERO)
                .stock(request.stock() != null ? request.stock() : BigDecimal.ZERO)
                .controlaStock(request.controlaStock())
                .build();
        return ResponseEntity.ok(productoRepository.save(producto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCTOS_EDITAR')")
    public ResponseEntity<Producto> actualizar(@PathVariable Long id, @Valid @RequestBody ProductoRequest request) {
        return productoRepository.findByIdAndTenantIdAndActivoTrue(id, TenantContext.getTenantId())
                .map(producto -> {
                    producto.setSku(request.sku());
                    producto.setNombre(request.nombre());
                    producto.setDescripcion(request.descripcion());
                    producto.setPrecioVenta(request.precioVenta());
                    producto.setPrecioCompra(request.precioCompra() != null ? request.precioCompra() : BigDecimal.ZERO);
                    producto.setStock(request.stock() != null ? request.stock() : BigDecimal.ZERO);
                    producto.setControlaStock(request.controlaStock());
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
}
