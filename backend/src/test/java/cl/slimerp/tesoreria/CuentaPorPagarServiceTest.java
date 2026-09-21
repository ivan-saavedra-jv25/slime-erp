package cl.slimerp.tesoreria;

import cl.slimerp.compras.Compra;
import cl.slimerp.config.TenantContext;
import cl.slimerp.gastos.Gasto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CuentaPorPagarServiceTest {

    private CuentaPorPagarRepository repository;
    private CuentaPorPagarService service;

    private final Long tenantId = 1L;

    @BeforeEach
    void setUp() {
        repository = mock(CuentaPorPagarRepository.class);
        service = new CuentaPorPagarService(repository);
        TenantContext.setTenantId(tenantId);

        when(repository.save(any(CuentaPorPagar.class))).thenAnswer(inv -> {
            CuentaPorPagar c = inv.getArgument(0);
            if (c.getId() == null) c.setId(100L);
            return c;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Compra compra(Long id, BigDecimal total, Long proveedorId) {
        return Compra.builder().id(id).tenantId(tenantId).proveedorId(proveedorId).bodegaId(1L)
                .total(total).build();
    }

    private Gasto gasto(Long id, BigDecimal monto, Long categoriaGastoId) {
        return Gasto.builder().id(id).tenantId(tenantId).categoriaGastoId(categoriaGastoId)
                .monto(monto).descripcion("Arriendo oficina").fecha(LocalDate.now()).build();
    }

    @Test
    void crearParaCompraGeneraLaCuentaConElSaldoIgualAlTotal() {
        when(repository.findByTenantIdAndCompraId(tenantId, 1L)).thenReturn(Optional.empty());

        service.crearParaCompra(compra(1L, new BigDecimal("50000"), 7L), "Proveedor Uno");

        verify(repository).save(argThat(c ->
                c.getCompraId().equals(1L) && c.getGastoId() == null
                        && c.getProveedorId().equals(7L)
                        && c.getDescripcion().equals("Compra C-1 — Proveedor Uno")
                        && c.getMontoTotal().compareTo(new BigDecimal("50000")) == 0
                        && c.getSaldoPendiente().compareTo(new BigDecimal("50000")) == 0
                        && c.getEstado() == EstadoCuentaPorPagar.DEUDA));
    }

    @Test
    void crearParaCompraEsIdempotenteSiYaExisteUnaCuentaParaEsaCompra() {
        CuentaPorPagar existente = CuentaPorPagar.builder().id(1L).tenantId(tenantId).compraId(1L).build();
        when(repository.findByTenantIdAndCompraId(tenantId, 1L)).thenReturn(Optional.of(existente));

        service.crearParaCompra(compra(1L, new BigDecimal("50000"), 7L), "Proveedor Uno");

        verify(repository, never()).save(any());
    }

    @Test
    void crearParaGastoGeneraLaCuentaConElSaldoIgualAlMonto() {
        when(repository.findByTenantIdAndGastoId(tenantId, 1L)).thenReturn(Optional.empty());

        service.crearParaGasto(gasto(1L, new BigDecimal("350000"), 3L), "Arriendo");

        verify(repository).save(argThat(c ->
                c.getGastoId().equals(1L) && c.getCompraId() == null
                        && c.getCategoriaGastoId().equals(3L)
                        && c.getDescripcion().equals("Arriendo: Arriendo oficina")
                        && c.getMontoTotal().compareTo(new BigDecimal("350000")) == 0
                        && c.getSaldoPendiente().compareTo(new BigDecimal("350000")) == 0
                        && c.getEstado() == EstadoCuentaPorPagar.DEUDA));
    }

    @Test
    void crearParaGastoEsIdempotenteSiYaExisteUnaCuentaParaEseGasto() {
        CuentaPorPagar existente = CuentaPorPagar.builder().id(1L).tenantId(tenantId).gastoId(1L).build();
        when(repository.findByTenantIdAndGastoId(tenantId, 1L)).thenReturn(Optional.of(existente));

        service.crearParaGasto(gasto(1L, new BigDecimal("350000"), 3L), "Arriendo");

        verify(repository, never()).save(any());
    }

    @Test
    void crearParaGastoTruncaLaDescripcionCuandoSuperaLos255Caracteres() {
        when(repository.findByTenantIdAndGastoId(tenantId, 1L)).thenReturn(Optional.empty());

        // Peor caso real de producción: categoria_gasto.nombre (VARCHAR(100)) al máximo
        // y gasto.descripcion (VARCHAR(255)) al máximo -> compuesto de 357 caracteres.
        String nombreCategoriaMax = "A".repeat(100);
        String descripcionGastoMax = "B".repeat(255);
        Gasto gasto = Gasto.builder().id(1L).tenantId(tenantId).categoriaGastoId(3L)
                .monto(new BigDecimal("350000")).descripcion(descripcionGastoMax).fecha(LocalDate.now()).build();

        service.crearParaGasto(gasto, nombreCategoriaMax);

        // Los primeros 252 caracteres del compuesto original + "..."
        String compuestoOriginal = nombreCategoriaMax + ": " + descripcionGastoMax;
        String truncadaEsperada = compuestoOriginal.substring(0, 252) + "...";

        verify(repository).save(argThat(c ->
                c.getGastoId().equals(1L)
                        && c.getDescripcion().length() == 255
                        && c.getDescripcion().equals(truncadaEsperada)
                        && c.getDescripcion().endsWith("...")
                        && c.getMontoTotal().compareTo(new BigDecimal("350000")) == 0
                        && c.getSaldoPendiente().compareTo(new BigDecimal("350000")) == 0
                        && c.getEstado() == EstadoCuentaPorPagar.DEUDA));
    }

    @Test
    void resumenSumaLosMontosYCuentaLosEstadosIgnorandoLasAnuladas() {
        when(repository.findByTenantIdOrderByFechaGeneracionDesc(tenantId)).thenReturn(List.of(
                CuentaPorPagar.builder().id(1L).montoTotal(new BigDecimal("1000")).montoPagado(BigDecimal.ZERO)
                        .saldoPendiente(new BigDecimal("1000")).estado(EstadoCuentaPorPagar.DEUDA).build(),
                CuentaPorPagar.builder().id(2L).montoTotal(new BigDecimal("2000")).montoPagado(new BigDecimal("2000"))
                        .saldoPendiente(BigDecimal.ZERO).estado(EstadoCuentaPorPagar.PAGADO).build(),
                CuentaPorPagar.builder().id(3L).montoTotal(new BigDecimal("500")).montoPagado(BigDecimal.ZERO)
                        .saldoPendiente(new BigDecimal("500")).estado(EstadoCuentaPorPagar.ANULADO).build()));

        ResumenCuentasPorPagar resumen = service.resumen();

        assertEquals(new BigDecimal("3000"), resumen.totalPorPagar());
        assertEquals(new BigDecimal("2000"), resumen.totalPagado());
        assertEquals(new BigDecimal("1000"), resumen.saldoPendiente());
        assertEquals(1, resumen.cuentasEnDeuda());
        assertEquals(1, resumen.cuentasPagadas());
        assertEquals(0, resumen.cuentasParciales());
    }

    @Test
    void anularRechazaUnaCuentaYaAnulada() {
        CuentaPorPagar cuenta = CuentaPorPagar.builder().id(1L).tenantId(tenantId).estado(EstadoCuentaPorPagar.ANULADO).build();
        when(repository.findByIdAndTenantId(1L, tenantId)).thenReturn(Optional.of(cuenta));

        assertThrows(IllegalArgumentException.class, () -> service.anular(1L, "motivo", 9L));
    }

    @Test
    void anularRechazaUnaCuentaYaPagada() {
        CuentaPorPagar cuenta = CuentaPorPagar.builder().id(1L).tenantId(tenantId).estado(EstadoCuentaPorPagar.PAGADO).build();
        when(repository.findByIdAndTenantId(1L, tenantId)).thenReturn(Optional.of(cuenta));

        assertThrows(IllegalArgumentException.class, () -> service.anular(1L, "motivo", 9L));
    }

    @Test
    void anularMarcaLaCuentaConElMotivoYElUsuario() {
        CuentaPorPagar cuenta = CuentaPorPagar.builder().id(1L).tenantId(tenantId).estado(EstadoCuentaPorPagar.DEUDA).build();
        when(repository.findByIdAndTenantId(1L, tenantId)).thenReturn(Optional.of(cuenta));

        CuentaPorPagar anulada = service.anular(1L, "Error de digitación", 9L);

        assertEquals(EstadoCuentaPorPagar.ANULADO, anulada.getEstado());
        assertEquals("Error de digitación", anulada.getMotivoAnulacion());
        assertEquals(9L, anulada.getUsuarioAnuloId());
        assertNotNull(anulada.getFechaAnulacion());
    }
}
