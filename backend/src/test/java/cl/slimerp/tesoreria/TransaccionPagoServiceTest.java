package cl.slimerp.tesoreria;

import cl.slimerp.catalogo.Cliente;
import cl.slimerp.catalogo.ClienteRepository;
import cl.slimerp.common.PaginaResponse;
import cl.slimerp.config.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TransaccionPagoServiceTest {

    private TransaccionPagoRepository transaccionPagoRepository;
    private CuentaPorCobrarRepository cuentaPorCobrarRepository;
    private ClienteRepository clienteRepository;
    private TransaccionPagoService service;

    private final Long tenantId = 1L;
    private CuentaPorCobrar cuenta;

    @BeforeEach
    void setUp() {
        transaccionPagoRepository = mock(TransaccionPagoRepository.class);
        cuentaPorCobrarRepository = mock(CuentaPorCobrarRepository.class);
        clienteRepository = mock(ClienteRepository.class);
        service = new TransaccionPagoService(transaccionPagoRepository, cuentaPorCobrarRepository, clienteRepository);
        TenantContext.setTenantId(tenantId);

        cuenta = CuentaPorCobrar.builder()
                .id(1L).tenantId(tenantId).ventaId(50L).clienteId(5L)
                .montoTotal(new BigDecimal("1000")).montoPagado(BigDecimal.ZERO)
                .saldoPendiente(new BigDecimal("1000")).estado(EstadoCuentaPorCobrar.DEUDA)
                .build();
        when(cuentaPorCobrarRepository.findByIdAndTenantId(1L, tenantId)).thenReturn(Optional.of(cuenta));
        when(cuentaPorCobrarRepository.save(any(CuentaPorCobrar.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transaccionPagoRepository.save(any(TransaccionPago.class))).thenAnswer(inv -> {
            TransaccionPago t = inv.getArgument(0);
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
                null, null, null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void registrarUnPagoParcialDejaLaCuentaEnEstadoParcial() {
        service.registrarPago(1L, requestEfectivo(new BigDecimal("400")), 9L);

        assertEquals(new BigDecimal("400"), cuenta.getMontoPagado());
        assertEquals(new BigDecimal("600"), cuenta.getSaldoPendiente());
        assertEquals(EstadoCuentaPorCobrar.PARCIAL, cuenta.getEstado());
    }

    @Test
    void registrarElPagoCompletoDejaLaCuentaEnEstadoPagado() {
        service.registrarPago(1L, requestEfectivo(new BigDecimal("1000")), 9L);

        assertEquals(BigDecimal.ZERO, cuenta.getSaldoPendiente());
        assertEquals(EstadoCuentaPorCobrar.PAGADO, cuenta.getEstado());
    }

    @Test
    void rechazaUnPagoQueExcedeElSaldoPendiente() {
        assertThrows(IllegalArgumentException.class,
                () -> service.registrarPago(1L, requestEfectivo(new BigDecimal("1500")), 9L));
    }

    @Test
    void rechazaUnPagoSobreUnaCuentaYaPagada() {
        cuenta.setEstado(EstadoCuentaPorCobrar.PAGADO);

        assertThrows(IllegalArgumentException.class,
                () -> service.registrarPago(1L, requestEfectivo(new BigDecimal("100")), 9L));
    }

    @Test
    void rechazaUnaTransferenciaSinLosBancosRequeridos() {
        var request = new TransaccionPagoRequest(new BigDecimal("100"), MedioPago.TRANSFERENCIA, null,
                null, null, null, null, null, null, null, null, null, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> service.registrarPago(1L, request, 9L));
    }

    @Test
    void aceptaUnaTransferenciaConLosBancosRequeridos() {
        var request = new TransaccionPagoRequest(new BigDecimal("100"), MedioPago.TRANSFERENCIA, null,
                "Banco A", "Banco B", "OP-123", null, null, null, null, null, null, null, null, null);

        TransaccionPago pago = service.registrarPago(1L, request, 9L);

        assertEquals(MedioPago.TRANSFERENCIA, pago.getMedioPago());
    }

    @Test
    void anularRecomputaLaCuentaSumandoSoloLosPagosConfirmados() {
        cuenta.setMontoPagado(new BigDecimal("400"));
        cuenta.setSaldoPendiente(new BigDecimal("600"));
        cuenta.setEstado(EstadoCuentaPorCobrar.PARCIAL);

        TransaccionPago pago = TransaccionPago.builder()
                .id(200L).tenantId(tenantId).cuentaPorCobrarId(1L).ventaId(50L).clienteId(5L)
                .monto(new BigDecimal("400")).medioPago(MedioPago.EFECTIVO).estado(EstadoTransaccion.CONFIRMADA)
                .build();
        when(transaccionPagoRepository.findByIdAndTenantId(200L, tenantId)).thenReturn(Optional.of(pago));
        when(transaccionPagoRepository.findByTenantIdAndCuentaPorCobrarIdAndEstado(
                tenantId, 1L, EstadoTransaccion.CONFIRMADA)).thenReturn(List.of());

        service.anular(200L, "Pago duplicado", 9L);

        assertEquals(EstadoTransaccion.ANULADA, pago.getEstado());
        assertEquals(BigDecimal.ZERO, cuenta.getMontoPagado());
        assertEquals(new BigDecimal("1000"), cuenta.getSaldoPendiente());
        assertEquals(EstadoCuentaPorCobrar.DEUDA, cuenta.getEstado());
    }

    private TransaccionPago pagoDe(Long id, Long clienteId) {
        return TransaccionPago.builder()
                .id(id).tenantId(tenantId).cuentaPorCobrarId(1L).ventaId(10L + id).clienteId(clienteId)
                .monto(new BigDecimal("100")).medioPago(MedioPago.EFECTIVO).estado(EstadoTransaccion.CONFIRMADA)
                .build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void buscarFiltraPorNombreDeClienteResolviendolosEnMemoria() {
        Cliente clienteCinco = Cliente.builder().id(5L).tenantId(tenantId).nombre("Cliente Cinco").rut("11.111.111-1").build();
        Cliente clienteSeis = Cliente.builder().id(6L).tenantId(tenantId).nombre("Otro Cliente").rut("22.222.222-2").build();
        TransaccionPago pago1 = pagoDe(1L, 5L);
        TransaccionPago pago2 = pagoDe(2L, 6L);

        when(transaccionPagoRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(pago1, pago2));
        when(clienteRepository.findByTenantIdAndIdIn(tenantId, List.of(5L, 6L)))
                .thenReturn(List.of(clienteCinco, clienteSeis));

        PaginaResponse<TransaccionPago> resultado = service.buscar("cinco", null, null, null, null, 0, 10);

        assertEquals(1, resultado.total());
        assertEquals(1L, resultado.contenido().get(0).getId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void buscarDevuelveSoloLaPaginaPedida() {
        List<TransaccionPago> pagos = List.of(pagoDe(1L, 5L), pagoDe(2L, 5L), pagoDe(3L, 5L));
        when(transaccionPagoRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(pagos);
        when(clienteRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of());

        PaginaResponse<TransaccionPago> resultado = service.buscar(null, null, null, null, null, 1, 2);

        assertEquals(3, resultado.total());
        assertEquals(1, resultado.contenido().size());
        assertEquals(3L, resultado.contenido().get(0).getId());
    }
}
