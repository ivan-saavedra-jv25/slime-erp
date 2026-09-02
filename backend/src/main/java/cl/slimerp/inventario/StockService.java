package cl.slimerp.inventario;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class StockService {

    private final StockProductoBodegaRepository stockRepository;
    private final BodegaRepository bodegaRepository;

    public StockService(StockProductoBodegaRepository stockRepository, BodegaRepository bodegaRepository) {
        this.stockRepository = stockRepository;
        this.bodegaRepository = bodegaRepository;
    }

    public Bodega bodegaPrincipal(Long tenantId) {
        return bodegaRepository.findByTenantIdAndPrincipalTrueAndActivoTrue(tenantId)
                .orElseThrow(() -> new IllegalStateException("El tenant no tiene una bodega principal configurada"));
    }

    @Transactional
    public void sumar(Long tenantId, Long productoId, Long bodegaId, BigDecimal cantidad) {
        StockProductoBodega stock = stockRepository.findByTenantIdAndProductoIdAndBodegaId(tenantId, productoId, bodegaId)
                .orElseGet(() -> stockRepository.save(StockProductoBodega.builder()
                        .tenantId(tenantId)
                        .productoId(productoId)
                        .bodegaId(bodegaId)
                        .cantidad(BigDecimal.ZERO)
                        .build()));

        stock.setCantidad(stock.getCantidad().add(cantidad));
        stockRepository.save(stock);
    }

    @Transactional
    public void transferir(Long tenantId, Long productoId, Long bodegaOrigenId, Long bodegaDestinoId, BigDecimal cantidad) {
        sumar(tenantId, productoId, bodegaOrigenId, cantidad.negate());
        sumar(tenantId, productoId, bodegaDestinoId, cantidad);
    }

    public BigDecimal stockDisponible(Long tenantId, Long productoId, Long bodegaId) {
        return stockRepository.findByTenantIdAndProductoIdAndBodegaId(tenantId, productoId, bodegaId)
                .map(StockProductoBodega::getCantidad)
                .orElse(BigDecimal.ZERO);
    }
}
