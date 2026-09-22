package cl.slimerp.tesoreria;

import cl.slimerp.common.PaginaResponse;
import cl.slimerp.config.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TransaccionPagoCompraServiceTest {

    private TransaccionPagoCompraRepository transaccionPagoCompraRepository;
    private CuentaPorPagarRepository cuentaPorPagarRepository;
    private TransaccionPagoCompraService service;

    private final Long tenantId = 1L;
    private CuentaPorPagar cuenta;

    @BeforeEach
    void setUp() {
        transaccionPagoCompraRepository = mock(TransaccionPagoCompraRepository.class);
        cuentaPorPagarRepository = mock(CuentaPorPagarRepository.class);
        service = new TransaccionPagoCompraService(transaccionPagoCompraRepository, cuentaPorPagarRepository);
        TenantContext.setTenantId(tenantId);

        cuenta = CuentaPorPagar.builder()
                .id(1L).tenantId(tenantId).compraId(50L).proveedorId(7L)
                .descripcion("Compra C-50 — Proveedor Uno")
                .montoTotal(new BigDecimal("1000")).montoPagado(BigDecimal.ZERO)
                .saldoPendiente(new BigDecimal("1000")).estado(EstadoCuentaPorPagar.DEUDA)
                .build();
        when(cuentaPorPagarRepository.findByIdAndTenantId(1L, tenantId)).thenReturn(Optional.of(cuenta));
        when(cuentaPorPagarRepository.save(any(CuentaPorPagar.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transaccionPagoCompraRepository.save(any(TransaccionPagoCompra.class))).thenAnswer(inv -> {
            TransaccionPagoCompra t = inv.getArgument(0);
            if (t.getId() == null) t.setId(200L);
            return t;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private TransaccionPagoRequest requestEfectivo(BigDecimal monto) {
        return new TransaccionPagoRequest(monto, MedioPago.EFECTIVO, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void registrarUnPagoParcialDejaLaCuentaEnEstadoParcial() {
        service.registrarPago(1L, requestEfectivo(new BigDecimal("400")), 9L);

        assertEquals(new BigDecimal("400"), cuenta.getMontoPagado());
        assertEquals(new BigDecimal("600"), cuenta.getSaldoPendiente());
        assertEquals(EstadoCuentaPorPagar.PARCIAL, cuenta.getEstado());
    }

    @Test
    void registrarElPagoCompletoDejaLaCuentaEnEstadoPagado() {
        service.registrarPago(1L, requestEfectivo(new BigDecimal("1000")), 9L);

        assertEquals(BigDecimal.ZERO, cuenta.getSaldoPendiente());
        assertEquals(EstadoCuentaPorPagar.PAGADO, cuenta.getEstado());
    }

    @Test
    void rechazaUnPagoQueExcedeElSaldoPendiente() {
        assertThrows(IllegalArgumentException.class,
                () -> service.registrarPago(1L, requestEfectivo(new BigDecimal("1500")), 9L));
    }

    @Test
    void rechazaUnPagoSobreUnaCuentaYaPagada() {
        cuenta.setEstado(EstadoCuentaPorPagar.PAGADO);

        assertThrows(IllegalArgumentException.class,
                () -> service.registrarPago(1L, requestEfectivo(new BigDecimal("100")), 9L));
    }

    @Test
    void rechazaUnaTransferenciaSinLosBancosRequeridos() {
        var request = new TransaccionPagoRequest(new BigDecimal("100"), MedioPago.TRANSFERENCIA, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> service.registrarPago(1L, request, 9L));
    }

    @Test
    void registrarUnPagoUsaLaFechaPersonalizadaEnElPagoYEnLaCuenta() {
        LocalDateTime fechaPago = LocalDateTime.of(2026, 3, 15, 9, 30);
        var request = new TransaccionPagoRequest(new BigDecimal("500"), MedioPago.EFECTIVO, null,
                null, null, null, null, null, null, null, null, null, null, null, null, fechaPago);

        TransaccionPagoCompra pago = service.registrarPago(1L, request, 9L);

        assertEquals(fechaPago, pago.getFecha());
        assertEquals(fechaPago, cuenta.getFechaUltimoPago());
    }

    @Test
    void elPagoRegistradoCopiaElOrigenDesdeLaCuenta() {
        TransaccionPagoCompra pago = service.registrarPago(1L, requestEfectivo(new BigDecimal("100")), 9L);

        assertEquals(50L, pago.getCompraId());
        assertNull(pago.getGastoId());
        assertEquals(1L, pago.getCuentaPorPagarId());
    }

    @Test
    void anularRecomputaLaCuentaSumandoSoloLosPagosConfirmados() {
        cuenta.setMontoPagado(new BigDecimal("400"));
        cuenta.setSaldoPendiente(new BigDecimal("600"));
        cuenta.setEstado(EstadoCuentaPorPagar.PARCIAL);

        TransaccionPagoCompra pago = TransaccionPagoCompra.builder()
                .id(200L).tenantId(tenantId).cuentaPorPagarId(1L).compraId(50L)
                .monto(new BigDecimal("400")).medioPago(MedioPago.EFECTIVO).estado(EstadoTransaccion.CONFIRMADA)
                .build();
        when(transaccionPagoCompraRepository.findByIdAndTenantId(200L, tenantId)).thenReturn(Optional.of(pago));
        when(transaccionPagoCompraRepository.findByTenantIdAndCuentaPorPagarIdAndEstado(
                tenantId, 1L, EstadoTransaccion.CONFIRMADA)).thenReturn(List.of());

        service.anular(200L, "Pago duplicado", 9L);

        assertEquals(EstadoTransaccion.ANULADA, pago.getEstado());
        assertEquals(BigDecimal.ZERO, cuenta.getMontoPagado());
        assertEquals(new BigDecimal("1000"), cuenta.getSaldoPendiente());
        assertEquals(EstadoCuentaPorPagar.DEUDA, cuenta.getEstado());
    }

    @Test
    void buscarRechazaPaginaNegativa() {
        assertThrows(IllegalArgumentException.class,
                () -> service.buscar(null, null, null, null, -1, 10));
    }

    @Test
    @SuppressWarnings("unchecked")
    void buscarEnvuelveLaPaginaDevueltaPorElRepositorio() {
        TransaccionPagoCompra pago = TransaccionPagoCompra.builder()
                .id(1L).tenantId(tenantId).cuentaPorPagarId(1L).compraId(50L)
                .monto(BigDecimal.TEN).medioPago(MedioPago.EFECTIVO).estado(EstadoTransaccion.CONFIRMADA).build();
        Pageable pageable = PageRequest.of(0, 10);
        when(transaccionPagoCompraRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pago), pageable, 1));

        PaginaResponse<TransaccionPagoCompra> resultado = service.buscar(null, null, null, null, 0, 10);

        assertEquals(1, resultado.total());
        assertEquals(1, resultado.contenido().size());
    }
}
