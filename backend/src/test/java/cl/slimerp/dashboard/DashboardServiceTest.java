package cl.slimerp.dashboard;

import cl.slimerp.catalogo.Cliente;
import cl.slimerp.catalogo.ClienteRepository;
import cl.slimerp.catalogo.Producto;
import cl.slimerp.catalogo.ProductoRepository;
import cl.slimerp.compras.Compra;
import cl.slimerp.compras.CompraRepository;
import cl.slimerp.inventario.StockProductoBodega;
import cl.slimerp.inventario.StockProductoBodegaRepository;
import cl.slimerp.tesoreria.CuentaPorCobrarService;
import cl.slimerp.tesoreria.ResumenTesoreria;
import cl.slimerp.ventas.TipoDocumentoVenta;
import cl.slimerp.ventas.Venta;
import cl.slimerp.ventas.VentaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    private VentaRepository ventaRepository;
    private CompraRepository compraRepository;
    private ClienteRepository clienteRepository;
    private ProductoRepository productoRepository;
    private StockProductoBodegaRepository stockRepository;
    private CuentaPorCobrarService cuentaPorCobrarService;
    private DashboardService service;

    private final Long tenantId = 1L;

    @BeforeEach
    void setUp() {
        ventaRepository = mock(VentaRepository.class);
        compraRepository = mock(CompraRepository.class);
        clienteRepository = mock(ClienteRepository.class);
        productoRepository = mock(ProductoRepository.class);
        stockRepository = mock(StockProductoBodegaRepository.class);
        cuentaPorCobrarService = mock(CuentaPorCobrarService.class);
        service = new DashboardService(ventaRepository, compraRepository, clienteRepository, productoRepository,
                stockRepository, cuentaPorCobrarService);

        when(ventaRepository.findByTenantIdAndActivoTrueAndFechaBetween(anyLong(), any(), any())).thenReturn(List.of());
        when(compraRepository.findByTenantIdAndActivoTrueAndFechaBetween(anyLong(), any(), any())).thenReturn(List.of());
        when(stockRepository.findByTenantId(tenantId)).thenReturn(List.of());
        when(productoRepository.findByTenantIdAndActivoTrue(tenantId)).thenReturn(List.of());
        when(clienteRepository.findByTenantIdAndActivoTrue(tenantId)).thenReturn(List.of());
        when(ventaRepository.findTop8ByTenantIdAndActivoTrueOrderByFechaDesc(tenantId)).thenReturn(List.of());
        when(cuentaPorCobrarService.resumen()).thenReturn(
                new ResumenTesoreria(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0));
    }

    private Venta venta(BigDecimal total, LocalDateTime fecha) {
        return Venta.builder().id(1L).tenantId(tenantId).clienteId(9L).montoTotal(total).fecha(fecha)
                .tipoDocumento(TipoDocumentoVenta.FACTURA).build();
    }

    @Test
    void sumaLasVentasActivasDelPeriodoComoVentasPeriodo() {
        // El mismo stub aplica tanto a la consulta del mes actual como a la del
        // mes anterior (ambas usan el mismo método con distintas fechas), asi
        // que el total del período anterior también queda en 1500: no importa
        // para esta aserción, que solo verifica la suma del período actual.
        when(ventaRepository.findByTenantIdAndActivoTrueAndFechaBetween(anyLong(), any(), any()))
                .thenReturn(List.of(venta(new BigDecimal("1000"), LocalDateTime.now()),
                        venta(new BigDecimal("500"), LocalDateTime.now())));

        DashboardResponse resp = service.resumen(tenantId);

        assertEquals(new BigDecimal("1500"), resp.kpis().ventasPeriodo());
    }

    @Test
    void variacionEsNulaSiElPeriodoAnteriorNoTuvoVentas() {
        DashboardResponse resp = service.resumen(tenantId);

        assertNull(resp.kpis().variacionPct());
        assertEquals(BigDecimal.ZERO, resp.kpis().ventasPeriodo());
    }

    @Test
    void productosSinStockYConStockBajoSeCuentanPorSeparado() {
        Producto sinStock = Producto.builder().id(1L).tenantId(tenantId).nombre("A").activo(true)
                .stockMinimo(new BigDecimal("5")).build();
        Producto stockBajo = Producto.builder().id(2L).tenantId(tenantId).nombre("B").activo(true)
                .stockMinimo(new BigDecimal("5")).build();
        Producto stockOk = Producto.builder().id(3L).tenantId(tenantId).nombre("C").activo(true)
                .stockMinimo(new BigDecimal("5")).build();
        when(productoRepository.findByTenantIdAndActivoTrue(tenantId)).thenReturn(List.of(sinStock, stockBajo, stockOk));
        when(stockRepository.findByTenantId(tenantId)).thenReturn(List.of(
                StockProductoBodega.builder().tenantId(tenantId).productoId(2L).bodegaId(1L).cantidad(new BigDecimal("3")).build(),
                StockProductoBodega.builder().tenantId(tenantId).productoId(3L).bodegaId(1L).cantidad(new BigDecimal("50")).build()));

        DashboardResponse resp = service.resumen(tenantId);

        assertEquals(1, resp.kpis().productosSinStock());
        assertEquals(1, resp.kpis().productosStockBajo());
        assertTrue(resp.alertas().stream().anyMatch(a -> a.tipo() == TipoAlerta.SIN_STOCK));
        assertTrue(resp.alertas().stream().anyMatch(a -> a.tipo() == TipoAlerta.STOCK_BAJO));
    }

    @Test
    void noGeneraAlertaDeStockBajoSinUmbralConfigurado() {
        Producto sinUmbral = Producto.builder().id(1L).tenantId(tenantId).nombre("A").activo(true)
                .stockMinimo(BigDecimal.ZERO).build();
        when(productoRepository.findByTenantIdAndActivoTrue(tenantId)).thenReturn(List.of(sinUmbral));
        when(stockRepository.findByTenantId(tenantId)).thenReturn(List.of(
                StockProductoBodega.builder().tenantId(tenantId).productoId(1L).bodegaId(1L).cantidad(new BigDecimal("1")).build()));

        DashboardResponse resp = service.resumen(tenantId);

        assertEquals(0, resp.kpis().productosStockBajo());
    }

    @Test
    void incluyeUnaAlertaDeCuentasPorCobrarPendientesCuandoHay() {
        when(cuentaPorCobrarService.resumen()).thenReturn(
                new ResumenTesoreria(new BigDecimal("1000"), BigDecimal.ZERO, new BigDecimal("1000"), 2, 1, 0));

        DashboardResponse resp = service.resumen(tenantId);

        assertEquals(3, resp.kpis().cuentasPorCobrarPendientes());
        assertEquals(new BigDecimal("1000"), resp.kpis().cuentasPorCobrarSaldo());
        assertTrue(resp.alertas().stream().anyMatch(a -> a.tipo() == TipoAlerta.CUENTAS_PENDIENTES));
    }

    @Test
    void resuelveElNombreDelClienteEnLasUltimasVentasYUsaUnFallbackSiNoExiste() {
        when(clienteRepository.findByTenantIdAndActivoTrue(tenantId))
                .thenReturn(List.of(Cliente.builder().id(9L).tenantId(tenantId).nombre("Cliente Uno").activo(true).build()));
        Venta ventaConCliente = venta(new BigDecimal("100"), LocalDateTime.now());
        Venta ventaSinCliente = Venta.builder().id(2L).tenantId(tenantId).clienteId(99L)
                .montoTotal(new BigDecimal("50")).fecha(LocalDateTime.now()).tipoDocumento(TipoDocumentoVenta.BOLETA).build();
        when(ventaRepository.findTop8ByTenantIdAndActivoTrueOrderByFechaDesc(tenantId))
                .thenReturn(List.of(ventaConCliente, ventaSinCliente));

        DashboardResponse resp = service.resumen(tenantId);

        assertEquals("Cliente Uno", resp.ultimasVentas().get(0).clienteNombre());
        assertEquals("Cliente #99", resp.ultimasVentas().get(1).clienteNombre());
    }

    @Test
    void ventasEvolucionRellenaConCeroLosDiasSinVentas() {
        List<PuntoVenta> puntos = service.ventasEvolucion(tenantId, "7d");

        assertEquals(7, puntos.size());
        assertTrue(puntos.stream().allMatch(p -> p.monto().compareTo(BigDecimal.ZERO) == 0));
    }

    @Test
    void ventasEvolucionSumaLosMontosEnElDiaCorrespondiente() {
        LocalDateTime hoy = LocalDate.now().atTime(10, 0);
        when(ventaRepository.findByTenantIdAndActivoTrueAndFechaBetween(anyLong(), any(), any()))
                .thenReturn(List.of(venta(new BigDecimal("200"), hoy), venta(new BigDecimal("300"), hoy)));

        List<PuntoVenta> puntos = service.ventasEvolucion(tenantId, "hoy");

        assertEquals(1, puntos.size());
        assertEquals(new BigDecimal("500"), puntos.get(0).monto());
    }
}
