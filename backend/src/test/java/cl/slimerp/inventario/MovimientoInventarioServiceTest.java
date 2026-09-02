package cl.slimerp.inventario;

import cl.slimerp.catalogo.Producto;
import cl.slimerp.catalogo.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class MovimientoInventarioServiceTest {

    private MovimientoInventarioHeaderRepository headerRepository;
    private MovimientoInventarioRepository movimientoRepository;
    private BodegaRepository bodegaRepository;
    private ProductoRepository productoRepository;
    private StockProductoBodegaRepository stockRepository;
    private StockService stockService;
    private MovimientoInventarioService service;

    // Fake en memoria: un mock plano de StockProductoBodegaRepository no recuerda
    // estado entre save()/find(), y esta clase sí necesita ese estado real.
    private final Map<String, StockProductoBodega> stockPorClave = new HashMap<>();

    private final Long tenantId = 1L;
    private final Bodega bodegaA = Bodega.builder().id(1L).tenantId(1L).nombre("A").activo(true).build();
    private final Bodega bodegaB = Bodega.builder().id(2L).tenantId(1L).nombre("B").activo(true).build();
    private final Producto producto = Producto.builder().id(10L).tenantId(1L).nombre("Producto X").activo(true).build();

    @BeforeEach
    void setUp() {
        headerRepository = mock(MovimientoInventarioHeaderRepository.class);
        movimientoRepository = mock(MovimientoInventarioRepository.class);
        bodegaRepository = mock(BodegaRepository.class);
        productoRepository = mock(ProductoRepository.class);
        stockRepository = mock(StockProductoBodegaRepository.class);
        stockPorClave.clear();

        stockService = new StockService(stockRepository, bodegaRepository, movimientoRepository);
        service = new MovimientoInventarioService(headerRepository, movimientoRepository, bodegaRepository,
                productoRepository, stockService);

        when(headerRepository.save(any(MovimientoInventarioHeader.class))).thenAnswer(inv -> {
            MovimientoInventarioHeader h = inv.getArgument(0);
            h.setId(100L);
            return h;
        });
        when(stockRepository.findByTenantIdAndProductoIdAndBodegaId(anyLong(), anyLong(), anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(stockPorClave.get(clave(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)))));
        when(stockRepository.save(any(StockProductoBodega.class))).thenAnswer(inv -> {
            StockProductoBodega s = inv.getArgument(0);
            stockPorClave.put(clave(s.getTenantId(), s.getProductoId(), s.getBodegaId()), s);
            return s;
        });
        when(bodegaRepository.findByIdAndTenantIdAndActivoTrue(1L, tenantId)).thenReturn(Optional.of(bodegaA));
        when(bodegaRepository.findByIdAndTenantIdAndActivoTrue(2L, tenantId)).thenReturn(Optional.of(bodegaB));
        when(productoRepository.findByIdAndTenantIdAndActivoTrue(10L, tenantId)).thenReturn(Optional.of(producto));
    }

    private String clave(Long t, Long p, Long b) {
        return t + "-" + p + "-" + b;
    }

    private MovimientoInventarioService.MovimientoRequest request(
            TipoMovimiento tipo, Long origen, Long destino, BigDecimal cantidad) {
        return new MovimientoInventarioService.MovimientoRequest(tipo, origen, destino, null,
                List.of(new MovimientoInventarioService.MovimientoItemRequest(10L, cantidad)));
    }

    @Test
    void entradaSumaStockEnBodegaDestino() {
        service.crear(tenantId, 5L, request(TipoMovimiento.ENTRADA, null, 1L, new BigDecimal("10")));

        assertEquals(new BigDecimal("10"), stockService.stockDisponible(tenantId, 10L, 1L));
    }

    @Test
    void entradaSinBodegaDestinoLanzaExcepcion() {
        assertThrows(IllegalArgumentException.class,
                () -> service.crear(tenantId, 5L, request(TipoMovimiento.ENTRADA, null, null, BigDecimal.TEN)));
    }

    @Test
    void salidaConStockInsuficienteLanzaExcepcion() {
        assertThrows(IllegalArgumentException.class,
                () -> service.crear(tenantId, 5L, request(TipoMovimiento.SALIDA, 1L, null, BigDecimal.TEN)));
    }

    @Test
    void salidaDescuentaStockDeBodegaOrigenCuandoHayDisponible() {
        service.crear(tenantId, 5L, request(TipoMovimiento.ENTRADA, null, 1L, new BigDecimal("10")));

        service.crear(tenantId, 5L, request(TipoMovimiento.SALIDA, 1L, null, new BigDecimal("4")));

        assertEquals(new BigDecimal("6"), stockService.stockDisponible(tenantId, 10L, 1L));
    }

    @Test
    void trasladoMueveStockEntreBodegas() {
        service.crear(tenantId, 5L, request(TipoMovimiento.ENTRADA, null, 1L, new BigDecimal("10")));

        service.crear(tenantId, 5L, request(TipoMovimiento.TRASLADO, 1L, 2L, new BigDecimal("3")));

        assertEquals(new BigDecimal("7"), stockService.stockDisponible(tenantId, 10L, 1L));
        assertEquals(new BigDecimal("3"), stockService.stockDisponible(tenantId, 10L, 2L));
    }

    @Test
    void trasladoConMismaBodegaOrigenYDestinoLanzaExcepcion() {
        assertThrows(IllegalArgumentException.class,
                () -> service.crear(tenantId, 5L, request(TipoMovimiento.TRASLADO, 1L, 1L, BigDecimal.ONE)));
    }

    @Test
    void ajusteSumaLaCantidadIndicadaEnLaBodega() {
        service.crear(tenantId, 5L, request(TipoMovimiento.AJUSTE, 1L, null, new BigDecimal("15")));

        assertEquals(new BigDecimal("15"), stockService.stockDisponible(tenantId, 10L, 1L));
    }
}
