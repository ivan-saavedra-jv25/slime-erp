package cl.slimerp.inventario;

import cl.slimerp.catalogo.Producto;
import cl.slimerp.catalogo.ProductoRepository;
import cl.slimerp.config.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/stock")
public class StockController {

    private final StockProductoBodegaRepository stockRepository;
    private final BodegaRepository bodegaRepository;
    private final ProductoRepository productoRepository;
    private final StockService stockService;

    public StockController(StockProductoBodegaRepository stockRepository, BodegaRepository bodegaRepository,
                            ProductoRepository productoRepository, StockService stockService) {
        this.stockRepository = stockRepository;
        this.bodegaRepository = bodegaRepository;
        this.productoRepository = productoRepository;
        this.stockService = stockService;
    }

    public record StockPorBodega(Long bodegaId, String bodegaNombre, BigDecimal cantidad) {
    }

    public record AjusteRequest(@NotNull Long productoId, @NotNull Long bodegaId, @NotNull BigDecimal cantidad) {
    }

    public record InventarioItem(Long productoId, String sku, String nombre, BigDecimal cantidad) {
    }

    @GetMapping("/inventario")
    @PreAuthorize("hasAuthority('BODEGAS_VER')")
    public List<InventarioItem> inventarioPorBodega(@RequestParam Long bodegaId) {
        Long tenantId = TenantContext.getTenantId();
        bodegaRepository.findByIdAndTenantIdAndActivoTrue(bodegaId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Bodega no encontrada: " + bodegaId));

        Map<Long, BigDecimal> cantidades = stockRepository.findByTenantIdAndBodegaId(tenantId, bodegaId).stream()
                .collect(Collectors.toMap(StockProductoBodega::getProductoId, StockProductoBodega::getCantidad));

        return productoRepository.findByTenantIdAndActivoTrue(tenantId).stream()
                .map(p -> new InventarioItem(p.getId(), p.getSku(), p.getNombre(),
                        cantidades.getOrDefault(p.getId(), BigDecimal.ZERO)))
                .sorted(Comparator.comparing(InventarioItem::nombre))
                .collect(Collectors.toList());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('BODEGAS_VER')")
    public List<StockPorBodega> porProducto(@RequestParam Long productoId) {
        Long tenantId = TenantContext.getTenantId();

        List<Bodega> bodegas = bodegaRepository.findByTenantIdAndActivoTrue(tenantId);
        Map<Long, BigDecimal> cantidades = stockRepository.findByTenantIdAndProductoId(tenantId, productoId).stream()
                .collect(Collectors.toMap(StockProductoBodega::getBodegaId, StockProductoBodega::getCantidad));

        return bodegas.stream()
                .map(b -> new StockPorBodega(b.getId(), b.getNombre(), cantidades.getOrDefault(b.getId(), BigDecimal.ZERO)))
                .collect(Collectors.toList());
    }

    @PostMapping("/ajuste")
    @PreAuthorize("hasAuthority('BODEGAS_EDITAR')")
    public List<StockPorBodega> ajustar(@Valid @RequestBody AjusteRequest request) {
        Long tenantId = TenantContext.getTenantId();

        Producto producto = productoRepository.findByIdAndTenantIdAndActivoTrue(request.productoId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + request.productoId()));
        bodegaRepository.findByIdAndTenantIdAndActivoTrue(request.bodegaId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Bodega no encontrada: " + request.bodegaId()));

        StockProductoBodega actual = stockRepository.findByTenantIdAndProductoIdAndBodegaId(
                tenantId, request.productoId(), request.bodegaId()).orElse(null);
        BigDecimal actualCantidad = actual == null ? BigDecimal.ZERO : actual.getCantidad();
        BigDecimal delta = request.cantidad().subtract(actualCantidad);

        stockService.sumar(tenantId, request.productoId(), request.bodegaId(), delta);

        return porProducto(request.productoId());
    }
}
