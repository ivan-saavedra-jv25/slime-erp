package cl.slimerp.flujocaja;

import cl.slimerp.catalogo.Cliente;
import cl.slimerp.catalogo.ClienteRepository;
import cl.slimerp.config.TenantContext;
import cl.slimerp.gastos.CategoriaGasto;
import cl.slimerp.gastos.CategoriaGastoRepository;
import cl.slimerp.tesoreria.CuentaPorPagar;
import cl.slimerp.tesoreria.CuentaPorPagarRepository;
import cl.slimerp.tesoreria.EstadoTransaccion;
import cl.slimerp.tesoreria.TransaccionPago;
import cl.slimerp.tesoreria.TransaccionPagoCompra;
import cl.slimerp.tesoreria.TransaccionPagoCompraRepository;
import cl.slimerp.tesoreria.TransaccionPagoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FlujoCajaServiceTest {

    private static final Long TENANT = 1L;

    private TransaccionPagoRepository pagoRepository;
    private TransaccionPagoCompraRepository pagoCompraRepository;
    private CuentaPorPagarRepository cuentaPorPagarRepository;
    private CategoriaGastoRepository categoriaGastoRepository;
    private ClienteRepository clienteRepository;
    private FlujoCajaService service;

    @BeforeEach
    void setUp() {
        pagoRepository = mock(TransaccionPagoRepository.class);
        pagoCompraRepository = mock(TransaccionPagoCompraRepository.class);
        cuentaPorPagarRepository = mock(CuentaPorPagarRepository.class);
        categoriaGastoRepository = mock(CategoriaGastoRepository.class);
        clienteRepository = mock(ClienteRepository.class);
        service = new FlujoCajaService(pagoRepository, pagoCompraRepository,
                cuentaPorPagarRepository, categoriaGastoRepository, clienteRepository);
        TenantContext.setTenantId(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void agrupaCobrosYComprasYGastosPorMesYSumaGastosDeMismaCategoria() {
        when(pagoRepository.findByTenantIdAndEstadoOrderByFechaAsc(TENANT, EstadoTransaccion.CONFIRMADA))
                .thenReturn(List.of(
                        cobro(1L, 101L, 501L, LocalDateTime.of(2026, 1, 5, 10, 0), "100000"),
                        cobro(2L, 102L, 502L, LocalDateTime.of(2026, 2, 3, 10, 0), "200000")));
        when(pagoCompraRepository.findByTenantIdAndEstadoOrderByFechaAsc(TENANT, EstadoTransaccion.CONFIRMADA))
                .thenReturn(List.of(
                        pagoCompra(11L, 201L, compraId(201L), LocalDateTime.of(2026, 1, 8, 10, 0), "50000"),
                        pagoCompra(12L, 202L, null, LocalDateTime.of(2026, 1, 10, 10, 0), "12000"),
                        pagoCompra(13L, 203L, null, LocalDateTime.of(2026, 1, 15, 10, 0), "8000")));
        when(cuentaPorPagarRepository.findByTenantIdAndIdIn(eq(TENANT), any()))
                .thenReturn(List.of(
                        cuenta(201L, 301L, null, null, "Compra proveedor A"),
                        cuenta(202L, null, 701L, 901L, "Arriendo oficina"),
                        cuenta(203L, null, 701L, 901L, "Bodega")));
        when(categoriaGastoRepository.findByTenantIdAndIdIn(eq(TENANT), any()))
                .thenReturn(List.of(
                        categoria(901L, "Arriendo"),
                        categoria(902L, "Servicios básicos")));
        when(clienteRepository.findByTenantIdAndIdIn(eq(TENANT), any()))
                .thenReturn(List.of(cliente(501L, "Ana"), cliente(502L, "Bruno")));

        ResumenAnio resumen = service.resumenAnio(2026);

        MesResumen enero = resumen.meses().get(0);
        assertEquals("2026-01", enero.mes());
        assertMoney("100000", enero.ingresos());
        assertMoney("50000", enero.compras());
        assertMoney("20000", enero.gastos()); // 12000 + 8000 sumados de la misma categoría
        assertMoney("30000", enero.resultado());
        assertMoney("30000", enero.saldo());
        assertEquals(1, enero.gastosPorCategoria().size());
        assertEquals("Arriendo", enero.gastosPorCategoria().get(0).categoria());
        assertMoney("20000", enero.gastosPorCategoria().get(0).total());
        assertEquals(901L, enero.gastosPorCategoria().get(0).categoriaGastoId());

        MesResumen febrero = resumen.meses().get(1);
        assertMoney("200000", febrero.ingresos());
        assertMoney("0", febrero.compras());
        assertMoney("0", febrero.gastos());
        assertMoney("230000", febrero.saldo());
    }

    @Test
    void excluyeTransaccionesAnuladas() {
        when(pagoRepository.findByTenantIdAndEstadoOrderByFechaAsc(TENANT, EstadoTransaccion.CONFIRMADA))
                .thenReturn(List.of());
        when(pagoCompraRepository.findByTenantIdAndEstadoOrderByFechaAsc(TENANT, EstadoTransaccion.CONFIRMADA))
                .thenReturn(List.of());
        when(clienteRepository.findByTenantIdAndIdIn(eq(TENANT), any())).thenReturn(List.of());

        // El servicio solo consulta pagos CONFIRMADA; las ANULADA quedan fuera.
        ResumenAnio resumen = service.resumenAnio(2026);

        verify(pagoRepository).findByTenantIdAndEstadoOrderByFechaAsc(TENANT, EstadoTransaccion.CONFIRMADA);
        verify(pagoCompraRepository).findByTenantIdAndEstadoOrderByFechaAsc(TENANT, EstadoTransaccion.CONFIRMADA);

        assertEquals(12, resumen.meses().size());
        assertTrue(resumen.meses().stream().allMatch(m -> m.ingresos().signum() == 0
                && m.compras().signum() == 0 && m.gastos().signum() == 0
                && m.saldo().signum() == 0));
    }

    @Test
    void saldoAcumuladoIncluyeMovimientosDeAniosAnteriores() {
        when(pagoRepository.findByTenantIdAndEstadoOrderByFechaAsc(TENANT, EstadoTransaccion.CONFIRMADA))
                .thenReturn(List.of(
                        cobro(1L, 101L, 501L, LocalDateTime.of(2025, 12, 20, 10, 0), "50000"),
                        cobro(2L, 102L, 502L, LocalDateTime.of(2026, 5, 10, 10, 0), "70000")));
        when(pagoCompraRepository.findByTenantIdAndEstadoOrderByFechaAsc(TENANT, EstadoTransaccion.CONFIRMADA))
                .thenReturn(List.of());
        when(clienteRepository.findByTenantIdAndIdIn(eq(TENANT), any()))
                .thenReturn(List.of(cliente(501L, "Ana"), cliente(502L, "Bruno")));

        ResumenAnio resumen = service.resumenAnio(2026);

        // Enero arranca con el saldo traído desde 2025 y crece en el mes del cobro.
        assertMoney("50000", resumen.meses().get(0).saldo());
        assertMoney("50000", resumen.meses().get(3).saldo());
        assertMoney("120000", resumen.meses().get(4).saldo());
    }

    @Test
    void detalleMesAgrupaGastosPorCategoriaConSusLineasYDescribeCobros() {
        when(pagoRepository.findByTenantIdAndEstadoOrderByFechaAsc(TENANT, EstadoTransaccion.CONFIRMADA))
                .thenReturn(List.of(
                        cobro(1L, 101L, 501L, LocalDateTime.of(2026, 3, 2, 10, 0), "150000")));
        when(pagoCompraRepository.findByTenantIdAndEstadoOrderByFechaAsc(TENANT, EstadoTransaccion.CONFIRMADA))
                .thenReturn(List.of(
                        pagoCompra(12L, 202L, null, LocalDateTime.of(2026, 3, 10, 10, 0), "12000"),
                        pagoCompra(13L, 203L, null, LocalDateTime.of(2026, 3, 15, 10, 0), "8000")));
        when(cuentaPorPagarRepository.findByTenantIdAndIdIn(eq(TENANT), any()))
                .thenReturn(List.of(
                        cuenta(202L, null, 701L, 901L, "Arriendo mes marzo"),
                        cuenta(203L, null, 701L, 901L, "Bodega")));
        when(categoriaGastoRepository.findByTenantIdAndIdIn(eq(TENANT), any()))
                .thenReturn(List.of(categoria(901L, "Arriendo")));
        when(clienteRepository.findByTenantIdAndIdIn(eq(TENANT), any()))
                .thenReturn(List.of(cliente(501L, "Ana")));

        DetalleMes detalle = service.detalleMes("2026-03");

        assertEquals("2026-03", detalle.mes());
        assertMoney("150000", detalle.ingresos());
        assertMoney("0", detalle.compras());
        assertMoney("20000", detalle.gastos());
        assertMoney("130000", detalle.resultado());
        assertMoney("130000", detalle.saldo());

        assertEquals(1, detalle.ingresosDetalle().size());
        assertTrue(detalle.ingresosDetalle().get(0).descripcion().contains("Ana"));
        assertTrue(detalle.ingresosDetalle().get(0).descripcion().contains("Venta V-101"));

        assertEquals(1, detalle.gastosPorCategoria().size());
        GastoCategoriaDetalle cat = detalle.gastosPorCategoria().get(0);
        assertEquals("Arriendo", cat.categoria());
        assertMoney("20000", cat.total());
        assertEquals(2, cat.lineas().size());
    }

    @Test
    void gastoSinCategoriaSeAgrupaEnSinCategoria() {
        when(pagoRepository.findByTenantIdAndEstadoOrderByFechaAsc(TENANT, EstadoTransaccion.CONFIRMADA))
                .thenReturn(List.of());
        when(pagoCompraRepository.findByTenantIdAndEstadoOrderByFechaAsc(TENANT, EstadoTransaccion.CONFIRMADA))
                .thenReturn(List.of(
                        pagoCompra(12L, 202L, null, LocalDateTime.of(2026, 4, 10, 10, 0), "5000")));
        when(cuentaPorPagarRepository.findByTenantIdAndIdIn(eq(TENANT), any()))
                .thenReturn(List.of(cuenta(202L, null, 701L, null, "Gasto sin categoría")));
        when(categoriaGastoRepository.findByTenantIdAndIdIn(eq(TENANT), any())).thenReturn(List.of());
        when(clienteRepository.findByTenantIdAndIdIn(eq(TENANT), any())).thenReturn(List.of());

        MesResumen abril = service.resumenAnio(2026).meses().get(3);
        assertMoney("5000", abril.gastos());
        assertEquals(1, abril.gastosPorCategoria().size());
        assertEquals("Sin categoría", abril.gastosPorCategoria().get(0).categoria());
        assertNull(abril.gastosPorCategoria().get(0).categoriaGastoId());

        DetalleMes detalle = service.detalleMes("2026-04");
        assertEquals("Sin categoría", detalle.gastosPorCategoria().get(0).categoria());
        assertNull(detalle.gastosPorCategoria().get(0).categoriaGastoId());
    }

    @Test
    void mesInvalidoLanzaExcepcion() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.detalleMes("2026/01"));
        assertTrue(ex.getMessage().contains("yyyy-MM"));
    }

    private TransaccionPago cobro(Long id, Long ventaId, Long clienteId, LocalDateTime fecha, String monto) {
        return TransaccionPago.builder()
                .id(id).tenantId(TENANT).cuentaPorCobrarId(id)
                .ventaId(ventaId).clienteId(clienteId).fecha(fecha)
                .monto(new BigDecimal(monto)).estado(EstadoTransaccion.CONFIRMADA).build();
    }

    private TransaccionPagoCompra pagoCompra(Long id, Long cuentaPorPagarId, Long compraId,
                                             LocalDateTime fecha, String monto) {
        return TransaccionPagoCompra.builder()
                .id(id).tenantId(TENANT).cuentaPorPagarId(cuentaPorPagarId)
                .compraId(compraId).fecha(fecha)
                .monto(new BigDecimal(monto)).estado(EstadoTransaccion.CONFIRMADA).build();
    }

    private Long compraId(Long cuentaId) {
        // CxP de compra: compraId = cuentaId + 100
        return cuentaId + 100;
    }

    private CuentaPorPagar cuenta(Long id, Long compraId, Long gastoId, Long categoriaGastoId, String descripcion) {
        return CuentaPorPagar.builder()
                .id(id).tenantId(TENANT).compraId(compraId).gastoId(gastoId)
                .categoriaGastoId(categoriaGastoId).descripcion(descripcion).build();
    }

    private CategoriaGasto categoria(Long id, String nombre) {
        return CategoriaGasto.builder().id(id).tenantId(TENANT).nombre(nombre).build();
    }

    private Cliente cliente(Long id, String nombre) {
        return Cliente.builder().id(id).tenantId(TENANT).nombre(nombre).build();
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "Se esperaba " + expected + " pero fue " + actual);
    }
}