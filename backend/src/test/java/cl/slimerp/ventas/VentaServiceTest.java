package cl.slimerp.ventas;

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

class VentaServiceTest {

    private VentaRepository ventaRepository;
    private ClienteRepository clienteRepository;
    private ProductoRepository productoRepository;
    private BodegaRepository bodegaRepository;
    private FormaPagoRepository formaPagoRepository;
    private StockProductoBodegaRepository stockRepository;
    private StockService stockService;
    private VentaService service;

    private final Map<String, StockProductoBodega> stockPorClave = new HashMap<>();

    private final Long tenantId = 1L;
    private final Bodega bodega = Bodega.builder().id(1L).tenantId(1L).nombre("Principal").principal(true).activo(true).build();
    private final Cliente cliente = Cliente.builder().id(1L).tenantId(1L).nombre("Cliente Uno").activo(true).build();
    private final FormaPago formaPagoContado = FormaPago.builder().id(1L).tenantId(1L).nombre("Efectivo")
            .categoria(CategoriaFormaPago.CONTADO).activo(true).build();
    private final Producto producto = Producto.builder().id(10L).tenantId(1L).nombre("Producto X")
            .precioVenta(new BigDecimal("1000")).activo(true).build();

    @BeforeEach
    void setUp() {
        ventaRepository = mock(VentaRepository.class);
        clienteRepository = mock(ClienteRepository.class);
        productoRepository = mock(ProductoRepository.class);
        bodegaRepository = mock(BodegaRepository.class);
        formaPagoRepository = mock(FormaPagoRepository.class);
        stockRepository = mock(StockProductoBodegaRepository.class);
        MovimientoInventarioRepository movimientoRepository = mock(MovimientoInventarioRepository.class);
        stockPorClave.clear();

        stockService = new StockService(stockRepository, bodegaRepository, movimientoRepository);
        service = new VentaService(ventaRepository, clienteRepository, productoRepository, bodegaRepository,
                formaPagoRepository, stockService);

        TenantContext.setTenantId(tenantId);

        when(ventaRepository.save(any(Venta.class))).thenAnswer(inv -> {
            Venta v = inv.getArgument(0);
            if (v.getId() == null) v.setId(100L);
            return v;
        });
        when(stockRepository.findByTenantIdAndProductoIdAndBodegaId(anyLong(), anyLong(), anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(stockPorClave.get(clave(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)))));
        when(stockRepository.save(any(StockProductoBodega.class))).thenAnswer(inv -> {
            StockProductoBodega s = inv.getArgument(0);
            stockPorClave.put(clave(s.getTenantId(), s.getProductoId(), s.getBodegaId()), s);
            return s;
        });

        when(clienteRepository.findByIdAndTenantIdAndActivoTrue(1L, tenantId)).thenReturn(Optional.of(cliente));
        when(formaPagoRepository.findByIdAndTenantIdAndActivoTrue(1L, tenantId)).thenReturn(Optional.of(formaPagoContado));
        when(bodegaRepository.findByIdAndTenantIdAndActivoTrue(1L, tenantId)).thenReturn(Optional.of(bodega));
        when(bodegaRepository.findByTenantIdAndPrincipalTrueAndActivoTrue(tenantId)).thenReturn(Optional.of(bodega));
        when(productoRepository.findByIdAndTenantIdAndActivoTrue(10L, tenantId)).thenReturn(Optional.of(producto));

        darStock(10L, 1L, new BigDecimal("50"));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void darStock(Long productoId, Long bodegaId, BigDecimal cantidad) {
        stockRepository.save(StockProductoBodega.builder()
                .tenantId(tenantId).productoId(productoId).bodegaId(bodegaId).cantidad(cantidad).build());
    }

    private String clave(Long t, Long p, Long b) {
        return t + "-" + p + "-" + b;
    }

    private VentaRequest request(TipoDocumentoVenta tipo, boolean exento, BigDecimal descuento, BigDecimal precioUnitario, BigDecimal cantidad) {
        return new VentaRequest(1L, 1L, 1L, tipo, exento, null, descuento,
                List.of(new VentaRequest.Item(10L, cantidad, precioUnitario)));
    }

    @Test
    void facturaGuardaMontosEnNetoConIvaCalculado() {
        Venta venta = service.crear(request(TipoDocumentoVenta.FACTURA, false, null, new BigDecimal("1000"), new BigDecimal("2")));

        assertEquals(new BigDecimal("2000.00"), venta.getMontoNeto());
        assertEquals(new BigDecimal("380.00"), venta.getMontoIva());
        assertEquals(new BigDecimal("2380.00"), venta.getMontoTotal());
    }

    @Test
    void boletaGuardaMontosDesglosadosDesdeElBruto() {
        Venta venta = service.crear(request(TipoDocumentoVenta.BOLETA, false, null, new BigDecimal("1190"), BigDecimal.ONE));

        assertEquals(new BigDecimal("1190.00"), venta.getMontoTotal());
        assertEquals(venta.getMontoTotal(), venta.getMontoNeto().add(venta.getMontoIva()));
    }

    @Test
    void voucherNoCalculaIvaYGuardaElFlagExento() {
        Venta venta = service.crear(request(TipoDocumentoVenta.VOUCHER, true, null, new BigDecimal("500"), BigDecimal.ONE));

        assertEquals(BigDecimal.ZERO.setScale(2), venta.getMontoIva());
        assertEquals(new BigDecimal("500.00"), venta.getMontoTotal());
        assertTrue(venta.isExento());
    }

    @Test
    void elDescuentoSeAplicaAntesDeCalcularElIva() {
        Venta venta = service.crear(request(TipoDocumentoVenta.FACTURA, false, new BigDecimal("200"), new BigDecimal("1000"), new BigDecimal("2")));

        assertEquals(new BigDecimal("1800.00"), venta.getMontoNeto());
        assertEquals(new BigDecimal("342.00"), venta.getMontoIva());
    }

    @Test
    void descuentaElStockDeLaBodegaAlConfirmar() {
        service.crear(request(TipoDocumentoVenta.FACTURA, false, null, new BigDecimal("1000"), new BigDecimal("5")));

        assertEquals(new BigDecimal("45"), stockService.stockDisponible(tenantId, 10L, 1L));
    }

    @Test
    void rechazaLaVentaSiElStockEsInsuficiente() {
        var req = request(TipoDocumentoVenta.FACTURA, false, null, new BigDecimal("1000"), new BigDecimal("999"));

        assertThrows(IllegalArgumentException.class, () -> service.crear(req));
    }

    @Test
    void usaLaBodegaPrincipalCuandoNoSeIndicaUna() {
        var req = new VentaRequest(1L, 1L, null, TipoDocumentoVenta.FACTURA, false, null, null,
                List.of(new VentaRequest.Item(10L, BigDecimal.ONE, new BigDecimal("1000"))));

        Venta venta = service.crear(req);

        assertEquals(bodega.getId(), venta.getBodegaId());
    }
}
