package cl.slimerp.compras;

import cl.slimerp.catalogo.ProveedorRepository;
import cl.slimerp.catalogo.ProductoRepository;
import cl.slimerp.config.TenantContext;
import cl.slimerp.inventario.Bodega;
import cl.slimerp.inventario.BodegaRepository;
import cl.slimerp.inventario.StockService;
import cl.slimerp.inventario.TipoMovimiento;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class CompraService {

    private final CompraRepository compraRepository;
    private final ProveedorRepository proveedorRepository;
    private final ProductoRepository productoRepository;
    private final BodegaRepository bodegaRepository;
    private final StockService stockService;

    public CompraService(CompraRepository compraRepository, ProveedorRepository proveedorRepository,
                          ProductoRepository productoRepository, BodegaRepository bodegaRepository,
                          StockService stockService) {
        this.compraRepository = compraRepository;
        this.proveedorRepository = proveedorRepository;
        this.productoRepository = productoRepository;
        this.bodegaRepository = bodegaRepository;
        this.stockService = stockService;
    }

    @Transactional
    public Compra crear(CompraRequest request) {
        Long tenantId = TenantContext.getTenantId();

        proveedorRepository.findByIdAndTenantIdAndActivoTrue(request.proveedorId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Proveedor no encontrado: " + request.proveedorId()));

        Bodega bodega = request.bodegaId() != null
                ? bodegaRepository.findByIdAndTenantIdAndActivoTrue(request.bodegaId(), tenantId)
                        .orElseThrow(() -> new IllegalArgumentException("Bodega no encontrada: " + request.bodegaId()))
                : stockService.bodegaPrincipal(tenantId);

        Compra compra = Compra.builder()
                .tenantId(tenantId)
                .proveedorId(request.proveedorId())
                .bodegaId(bodega.getId())
                .observacion(request.observacion())
                .build();
        compra = compraRepository.save(compra);

        List<CompraDetalle> detalle = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (CompraRequest.Item item : request.items()) {
            productoRepository.findByIdAndTenantIdAndActivoTrue(item.productoId(), tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + item.productoId()));

            BigDecimal subtotal = item.precioUnitario().multiply(item.cantidad());
            total = total.add(subtotal);

            detalle.add(CompraDetalle.builder()
                    .compra(compra)
                    .productoId(item.productoId())
                    .cantidad(item.cantidad())
                    .precioUnitario(item.precioUnitario())
                    .subtotal(subtotal)
                    .build());
        }

        compra.setDetalle(detalle);
        compra.setTotal(total);
        compra = compraRepository.save(compra);

        for (CompraRequest.Item item : request.items()) {
            stockService.sumar(tenantId, item.productoId(), bodega.getId(),
                    item.cantidad(), TipoMovimiento.ENTRADA_COMPRA, null, compra.getId());
        }

        return compra;
    }
}
