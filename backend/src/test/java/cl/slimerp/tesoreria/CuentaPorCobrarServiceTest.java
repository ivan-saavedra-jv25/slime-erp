package cl.slimerp.tesoreria;

import cl.slimerp.config.TenantContext;
import cl.slimerp.ventas.Venta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CuentaPorCobrarServiceTest {

    private CuentaPorCobrarRepository repository;
    private CuentaPorCobrarService service;

    private final Long tenantId = 1L;

    @BeforeEach
    void setUp() {
        repository = mock(CuentaPorCobrarRepository.class);
        service = new CuentaPorCobrarService(repository);
        TenantContext.setTenantId(tenantId);

        when(repository.save(any(CuentaPorCobrar.class))).thenAnswer(inv -> {
            CuentaPorCobrar c = inv.getArgument(0);
            if (c.getId() == null) c.setId(100L);
            return c;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Venta venta(Long id, BigDecimal total) {
        Venta v = new Venta();
        v.setId(id);
        v.setTenantId(tenantId);
        v.setClienteId(5L);
        v.setMontoTotal(total);
        return v;
    }

    @Test
    void creaLaCuentaConElSaldoIgualAlTotalDeLaVenta() {
        when(repository.findByTenantIdAndVentaId(tenantId, 1L)).thenReturn(Optional.empty());

        service.crearParaVenta(venta(1L, new BigDecimal("5000")));

        verify(repository).save(argThat(c ->
                c.getVentaId().equals(1L) && c.getClienteId().equals(5L)
                        && c.getMontoTotal().compareTo(new BigDecimal("5000")) == 0
                        && c.getSaldoPendiente().compareTo(new BigDecimal("5000")) == 0
                        && c.getEstado() == EstadoCuentaPorCobrar.DEUDA));
    }

    @Test
    void esIdempotenteSiYaExisteUnaCuentaParaLaVenta() {
        CuentaPorCobrar existente = CuentaPorCobrar.builder().id(1L).tenantId(tenantId).ventaId(1L).build();
        when(repository.findByTenantIdAndVentaId(tenantId, 1L)).thenReturn(Optional.of(existente));

        service.crearParaVenta(venta(1L, new BigDecimal("5000")));

        verify(repository, never()).save(any());
    }

    @Test
    void resumenSumaLosMontosYCuentaLosEstadosIgnorandoLasAnuladas() {
        when(repository.findByTenantIdOrderByFechaGeneracionDesc(tenantId)).thenReturn(List.of(
                CuentaPorCobrar.builder().id(1L).montoTotal(new BigDecimal("1000")).montoPagado(BigDecimal.ZERO)
                        .saldoPendiente(new BigDecimal("1000")).estado(EstadoCuentaPorCobrar.DEUDA).build(),
                CuentaPorCobrar.builder().id(2L).montoTotal(new BigDecimal("2000")).montoPagado(new BigDecimal("2000"))
                        .saldoPendiente(BigDecimal.ZERO).estado(EstadoCuentaPorCobrar.PAGADO).build(),
                CuentaPorCobrar.builder().id(3L).montoTotal(new BigDecimal("500")).montoPagado(BigDecimal.ZERO)
                        .saldoPendiente(new BigDecimal("500")).estado(EstadoCuentaPorCobrar.ANULADO).build()));

        ResumenTesoreria resumen = service.resumen();

        assertEquals(new BigDecimal("3000"), resumen.totalPorCobrar());
        assertEquals(new BigDecimal("2000"), resumen.totalCobrado());
        assertEquals(new BigDecimal("1000"), resumen.saldoPendiente());
        assertEquals(1, resumen.cuentasEnDeuda());
        assertEquals(1, resumen.cuentasPagadas());
        assertEquals(0, resumen.cuentasParciales());
    }

    @Test
    void anularRechazaUnaCuentaYaAnulada() {
        CuentaPorCobrar cuenta = CuentaPorCobrar.builder().id(1L).tenantId(tenantId).estado(EstadoCuentaPorCobrar.ANULADO).build();
        when(repository.findByIdAndTenantId(1L, tenantId)).thenReturn(Optional.of(cuenta));

        assertThrows(IllegalArgumentException.class, () -> service.anular(1L, "motivo", 9L));
    }

    @Test
    void anularRechazaUnaCuentaYaPagada() {
        CuentaPorCobrar cuenta = CuentaPorCobrar.builder().id(1L).tenantId(tenantId).estado(EstadoCuentaPorCobrar.PAGADO).build();
        when(repository.findByIdAndTenantId(1L, tenantId)).thenReturn(Optional.of(cuenta));

        assertThrows(IllegalArgumentException.class, () -> service.anular(1L, "motivo", 9L));
    }

    @Test
    void anularMarcaLaCuentaConElMotivoYElUsuario() {
        CuentaPorCobrar cuenta = CuentaPorCobrar.builder().id(1L).tenantId(tenantId).estado(EstadoCuentaPorCobrar.DEUDA).build();
        when(repository.findByIdAndTenantId(1L, tenantId)).thenReturn(Optional.of(cuenta));

        CuentaPorCobrar anulada = service.anular(1L, "Error de digitación", 9L);

        assertEquals(EstadoCuentaPorCobrar.ANULADO, anulada.getEstado());
        assertEquals("Error de digitación", anulada.getMotivoAnulacion());
        assertEquals(9L, anulada.getUsuarioAnuloId());
        assertNotNull(anulada.getFechaAnulacion());
    }
}
