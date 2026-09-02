package cl.slimerp.inventario;

import cl.slimerp.catalogo.Producto;
import cl.slimerp.catalogo.ProductoRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class MovimientoInventarioService {

    private final MovimientoInventarioHeaderRepository headerRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final BodegaRepository bodegaRepository;
    private final ProductoRepository productoRepository;
    private final StockService stockService;

    public MovimientoInventarioService(MovimientoInventarioHeaderRepository headerRepository,
                                        MovimientoInventarioRepository movimientoRepository,
                                        BodegaRepository bodegaRepository,
                                        ProductoRepository productoRepository,
                                        StockService stockService) {
        this.headerRepository = headerRepository;
        this.movimientoRepository = movimientoRepository;
        this.bodegaRepository = bodegaRepository;
        this.productoRepository = productoRepository;
        this.stockService = stockService;
    }

    public record MovimientoItemRequest(@NotNull Long productoId,
                                        @NotNull @Min(1) BigDecimal cantidad) {}

    public record MovimientoRequest(@NotNull TipoMovimiento tipo,
                                    Long bodegaOrigenId,
                                    Long bodegaDestinoId,
                                    String observacion,
                                    @NotEmpty @Valid List<MovimientoItemRequest> items) {}

    @Transactional
    public MovimientoInventarioHeader crear(Long tenantId, Long usuarioId, MovimientoRequest request) {
        validateBodegas(tenantId, request);
        validateProductos(tenantId, request);

        MovimientoInventarioHeader header = MovimientoInventarioHeader.builder()
                .tenantId(tenantId)
                .tipo(request.tipo())
                .bodegaOrigenId(request.bodegaOrigenId())
                .bodegaDestinoId(request.bodegaDestinoId())
                .usuarioId(usuarioId)
                .observacion(request.observacion())
                .build();
        header = headerRepository.save(header);

        for (MovimientoItemRequest item : request.items()) {
            switch (request.tipo()) {
                case ENTRADA -> stockService.sumar(tenantId, item.productoId(), request.bodegaDestinoId(),
                        item.cantidad(), TipoMovimiento.ENTRADA, header.getId(), null);
                case SALIDA -> stockService.sumar(tenantId, item.productoId(), request.bodegaOrigenId(),
                        item.cantidad().negate(), TipoMovimiento.SALIDA, header.getId(), null);
                case TRASLADO -> stockService.transferir(tenantId, item.productoId(),
                        request.bodegaOrigenId(), request.bodegaDestinoId(),
                        item.cantidad(), header.getId());
                case AJUSTE -> stockService.sumar(tenantId, item.productoId(), request.bodegaOrigenId(),
                        item.cantidad(), TipoMovimiento.AJUSTE, header.getId(), null);
            }
        }

        return header;
    }

    public List<MovimientoInventarioHeader> historial(Long tenantId) {
        return headerRepository.findByTenantIdOrderByFechaDesc(tenantId);
    }

    public MovimientoInventarioHeader detalle(Long tenantId, Long headerId) {
        return headerRepository.findById(headerId)
                .filter(h -> h.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Movimiento no encontrado: " + headerId));
    }

    public List<MovimientoInventario> itemsDelHeader(Long tenantId, Long headerId) {
        return movimientoRepository.findByTenantIdAndHeaderId(tenantId, headerId);
    }

    public BigDecimal stockDisponible(Long tenantId, Long productoId, Long bodegaId) {
        return stockService.stockDisponible(tenantId, productoId, bodegaId);
    }

    private void validateBodegas(Long tenantId, MovimientoRequest request) {
        switch (request.tipo()) {
            case ENTRADA -> {
                if (request.bodegaDestinoId() == null)
                    throw new IllegalArgumentException("Para ENTRADA se requiere bodega destino");
                bodegaRepository.findByIdAndTenantIdAndActivoTrue(request.bodegaDestinoId(), tenantId)
                        .orElseThrow(() -> new IllegalArgumentException("Bodega destino no encontrada"));
            }
            case SALIDA -> {
                if (request.bodegaOrigenId() == null)
                    throw new IllegalArgumentException("Para SALIDA se requiere bodega origen");
                bodegaRepository.findByIdAndTenantIdAndActivoTrue(request.bodegaOrigenId(), tenantId)
                        .orElseThrow(() -> new IllegalArgumentException("Bodega origen no encontrada"));
            }
            case TRASLADO -> {
                if (request.bodegaOrigenId() == null || request.bodegaDestinoId() == null)
                    throw new IllegalArgumentException("Para TRASLADO se requieren ambas bodegas");
                if (request.bodegaOrigenId().equals(request.bodegaDestinoId()))
                    throw new IllegalArgumentException("Las bodegas origen y destino deben ser distintas");
                bodegaRepository.findByIdAndTenantIdAndActivoTrue(request.bodegaOrigenId(), tenantId)
                        .orElseThrow(() -> new IllegalArgumentException("Bodega origen no encontrada"));
                bodegaRepository.findByIdAndTenantIdAndActivoTrue(request.bodegaDestinoId(), tenantId)
                        .orElseThrow(() -> new IllegalArgumentException("Bodega destino no encontrada"));
            }
            case AJUSTE -> {
                if (request.bodegaOrigenId() == null)
                    throw new IllegalArgumentException("Para AJUSTE se requiere bodega");
                bodegaRepository.findByIdAndTenantIdAndActivoTrue(request.bodegaOrigenId(), tenantId)
                        .orElseThrow(() -> new IllegalArgumentException("Bodega no encontrada"));
            }
        }
    }

    private void validateProductos(Long tenantId, MovimientoRequest request) {
        for (MovimientoItemRequest item : request.items()) {
            Producto producto = productoRepository.findByIdAndTenantIdAndActivoTrue(item.productoId(), tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + item.productoId()));

            if (request.tipo() == TipoMovimiento.TRASLADO || request.tipo() == TipoMovimiento.SALIDA) {
                BigDecimal disponible = stockService.stockDisponible(tenantId, item.productoId(), request.bodegaOrigenId());
                if (disponible.compareTo(item.cantidad()) < 0) {
                    throw new IllegalArgumentException(
                            "Stock insuficiente de \"" + producto.getNombre() + "\": disponible " + disponible + ", solicitado " + item.cantidad());
                }
            }
        }
    }
}
