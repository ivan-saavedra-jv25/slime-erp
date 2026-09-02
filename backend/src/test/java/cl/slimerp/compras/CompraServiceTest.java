package cl.slimerp.compras;

import cl.slimerp.catalogo.*;
import cl.slimerp.config.TenantContext;
import cl.slimerp.inventario.Bodega;
import cl.slimerp.inventario.BodegaRepository;
import cl.slimerp.inventario.MovimientoInventarioRepository;
import cl.slimerp.inventario.StockProductoBodega;
import cl.slimerp.inventario.StockProductoBodegaRepository;
import cl.slimerp.inventario.StockService;
import org.junit.jupiter.api.AfterEach;
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

class CompraServiceTest {

    private CompraRepository compraRepository;
    private ProveedorRepository proveedorRepository;
    private ProductoRepository productoRepository;
    private BodegaRepository bodegaRepository;
    private StockProductoBodegaRepository stockRepository;
    private StockService stockService;
    private CompraService service;

    private final Map<String, StockProductoBodega> stockPorClave = new HashMap<>();

    private final Long tenantId = 1L;
    private final Bodega bodega = Bodega.builder().id(1L).tenantId(1L).nombre("Principal").principal(true).activo(true).build();
    private final Proveedor proveedor = Proveedor.builder().id(1L).tenantId(1L).nombre("Proveedor Uno").activo(true).build();
    private final Producto producto = Producto.builder().id(10L).tenantId(1L).nombre("Producto X")
            .precioCompra(new BigDecimal("500")).activo(true).build();

    @BeforeEach
    void setUp() {
        compraRepository = mock(CompraRepository.class);
        proveedorRepository = mock(ProveedorRepository.class);
        productoRepository = mock(ProductoRepository.class);
        bodegaRepository = mock(BodegaRepository.class);
        stockRepository = mock(StockProductoBodegaRepository.class);
        MovimientoInventarioRepository movimientoRepository = mock(MovimientoInventarioRepository.class);
        stockPorClave.clear();

        stockService = new StockService(stockRepository, bodegaRepository, movimientoRepository);
        service = new CompraService(compraRepository, proveedorRepository, productoRepository, bodegaRepository, stockService);

        TenantContext.setTenantId(tenantId);

        when(compraRepository.save(any(Compra.class))).thenAnswer(inv -> {
            Compra c = inv.getArgument(0);
            if (c.getId() == null) c.setId(100L);
            return c;
        });
        when(stockRepository.findByTenantIdAndProductoIdAndBodegaId(anyLong(), anyLong(), anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(stockPorClave.get(clave(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)))));
        when(stockRepository.save(any(StockProductoBodega.class))).thenAnswer(inv -> {
            StockProductoBodega s = inv.getArgument(0);
            stockPorClave.put(clave(s.getTenantId(), s.getProductoId(), s.getBodegaId()), s);
            return s;
        });

        when(proveedorRepository.findByIdAndTenantIdAndActivoTrue(1L, tenantId)).thenReturn(Optional.of(proveedor));
        when(bodegaRepository.findByIdAndTenantIdAndActivoTrue(1L, tenantId)).thenReturn(Optional.of(bodega));
        when(bodegaRepository.findByTenantIdAndPrincipalTrueAndActivoTrue(tenantId)).thenReturn(Optional.of(bodega));
        when(productoRepository.findByIdAndTenantIdAndActivoTrue(10L, tenantId)).thenReturn(Optional.of(producto));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private String clave(Long t, Long p, Long b) {
        return t + "-" + p + "-" + b;
    }

    private CompraRequest request(BigDecimal precioUnitario, BigDecimal cantidad) {
        return new CompraRequest(1L, 1L, null, List.of(new CompraRequest.Item(10L, cantidad, precioUnitario)));
    }

    @Test
    void calculaElTotalComoLaSumaDeLosSubtotales() {
        Compra compra = service.crear(request(new BigDecimal("500"), new BigDecimal("3")));

        assertEquals(new BigDecimal("1500"), compra.getTotal());
    }

    @Test
    void aumentaElStockDeLaBodegaAlConfirmar() {
        service.crear(request(new BigDecimal("500"), new BigDecimal("10")));

        assertEquals(new BigDecimal("10"), stockService.stockDisponible(tenantId, 10L, 1L));
    }

    @Test
    void usaLaBodegaPrincipalCuandoNoSeIndicaUna() {
        Compra compra = service.crear(request(new BigDecimal("500"), BigDecimal.ONE));

        assertEquals(bodega.getId(), compra.getBodegaId());
    }

    @Test
    void rechazaLaCompraSiElProveedorNoExiste() {
        var req = new CompraRequest(999L, 1L, null, List.of(new CompraRequest.Item(10L, BigDecimal.ONE, new BigDecimal("500"))));

        assertThrows(IllegalArgumentException.class, () -> service.crear(req));
    }

    @Test
    void rechazaLaCompraSiElProductoNoExiste() {
        var req = new CompraRequest(1L, 1L, null, List.of(new CompraRequest.Item(999L, BigDecimal.ONE, new BigDecimal("500"))));

        assertThrows(IllegalArgumentException.class, () -> service.crear(req));
    }
}
