package cl.slimerp.inventario;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class StockService {

    private final StockProductoBodegaRepository stockRepository;
    private final BodegaRepository bodegaRepository;
    private final MovimientoInventarioRepository movimientoRepository;

    public StockService(StockProductoBodegaRepository stockRepository, BodegaRepository bodegaRepository,
                         MovimientoInventarioRepository movimientoRepository) {
        this.stockRepository = stockRepository;
        this.bodegaRepository = bodegaRepository;
        this.movimientoRepository = movimientoRepository;
    }

    public Bodega bodegaPrincipal(Long tenantId) {
        return bodegaRepository.findByTenantIdAndPrincipalTrueAndActivoTrue(tenantId)
                .orElseThrow(() -> new IllegalStateException("El tenant no tiene una bodega principal configurada"));
    }

    @Transactional
    public void sumar(Long tenantId, Long productoId, Long bodegaId, BigDecimal cantidad,
                       TipoMovimiento tipo, Long headerId, Long referenciaId) {
        StockProductoBodega stock = stockRepository.findByTenantIdAndProductoIdAndBodegaId(tenantId, productoId, bodegaId)
                .orElseGet(() -> stockRepository.save(StockProductoBodega.builder()
                        .tenantId(tenantId)
                        .productoId(productoId)
                        .bodegaId(bodegaId)
                        .cantidad(BigDecimal.ZERO)
                        .build()));

        stock.setCantidad(stock.getCantidad().add(cantidad));
        stockRepository.save(stock);

        movimientoRepository.save(MovimientoInventario.builder()
                .tenantId(tenantId)
                .productoId(productoId)
                .tipo(tipo)
                .cantidad(cantidad.abs())
                .headerId(headerId)
                .bodegaId(bodegaId)
                .referenciaId(referenciaId)
                .build());
    }

    @Transactional
    public void transferir(Long tenantId, Long productoId, Long bodegaOrigenId, Long bodegaDestinoId,
                            BigDecimal cantidad, Long headerId) {
        sumar(tenantId, productoId, bodegaOrigenId, cantidad.negate(), TipoMovimiento.SALIDA, headerId, null);
        sumar(tenantId, productoId, bodegaDestinoId, cantidad, TipoMovimiento.ENTRADA, headerId, null);
    }

    public BigDecimal stockDisponible(Long tenantId, Long productoId, Long bodegaId) {
        return stockRepository.findByTenantIdAndProductoIdAndBodegaId(tenantId, productoId, bodegaId)
                .map(StockProductoBodega::getCantidad)
                .orElse(BigDecimal.ZERO);
    }
}
