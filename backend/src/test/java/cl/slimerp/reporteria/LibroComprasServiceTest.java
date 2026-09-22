package cl.slimerp.reporteria;

import cl.slimerp.catalogo.Proveedor;
import cl.slimerp.catalogo.ProveedorRepository;
import cl.slimerp.compras.Compra;
import cl.slimerp.compras.CompraDetalle;
import cl.slimerp.compras.CompraRepository;
import cl.slimerp.tesoreria.CuentaPorPagar;
import cl.slimerp.tesoreria.CuentaPorPagarRepository;
import cl.slimerp.tesoreria.EstadoCuentaPorPagar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LibroComprasServiceTest {

    private CompraRepository compraRepository;
    private ProveedorRepository proveedorRepository;
    private CuentaPorPagarRepository cuentaPorPagarRepository;
    private LibroComprasService service;

    private final Long tenantId = 1L;
    private final LocalDate desde = LocalDate.of(2026, 9, 1);
    private final LocalDate hasta = LocalDate.of(2026, 9, 30);

    private final Proveedor proveedorUno = Proveedor.builder().id(1L).tenantId(1L).nombre("Proveedor Uno").rut("11.111.111-1").activo(true).build();
    private final Proveedor proveedorDos = Proveedor.builder().id(2L).tenantId(1L).nombre("Proveedor Dos").rut("22.222.222-2").activo(true).build();

    @BeforeEach
    void setUp() {
        compraRepository = mock(CompraRepository.class);
        proveedorRepository = mock(ProveedorRepository.class);
        cuentaPorPagarRepository = mock(CuentaPorPagarRepository.class);
        service = new LibroComprasService(compraRepository, proveedorRepository, cuentaPorPagarRepository);
    }

    private Compra compra(Long id, Long proveedorId, String numeroDocumento, BigDecimal total, int cantidadItems, LocalDate fecha) {
        List<CompraDetalle> detalle = new java.util.ArrayList<>();
        for (int i = 0; i < cantidadItems; i++) {
            detalle.add(CompraDetalle.builder().id((long) i).productoId(10L)
                    .cantidad(BigDecimal.ONE).precioUnitario(total).subtotal(total).build());
        }
        return Compra.builder().id(id).tenantId(tenantId).proveedorId(proveedorId)
                .bodegaId(1L).numeroDocumento(numeroDocumento)
                .fecha(fecha.atTime(10, 0)).total(total)
                .montoNeto(total).montoIva(total.multiply(new BigDecimal("0.19")))
                .detalle(detalle).build();
    }

    private CuentaPorPagar cuenta(Long compraId, EstadoCuentaPorPagar estado) {
        return CuentaPorPagar.builder().id(compraId + 1000).tenantId(tenantId).compraId(compraId).estado(estado).build();
    }

    @Test
    void armaFilasConProveedorEstadoYMontosCorrectos() {
        Compra c1 = compra(1L, 1L, "FAC-1", new BigDecimal("1000"), 2, LocalDate.of(2026, 9, 5));
        when(compraRepository.findByTenantIdAndActivoTrueAndFechaBetweenOrderByFechaAsc(eq(tenantId), any(), any()))
                .thenReturn(List.of(c1));
        when(proveedorRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of(proveedorUno));
        when(cuentaPorPagarRepository.findByTenantIdAndCompraIdIn(eq(tenantId), any()))
                .thenReturn(List.of(cuenta(1L, EstadoCuentaPorPagar.DEUDA)));

        LibroComprasService.LibroComprasResponse libro = service.generar(tenantId, desde, hasta);

        assertEquals(1, libro.filas().size());
        var fila = libro.filas().get(0);
        assertEquals("FAC-1", fila.numeroDocumento());
        assertEquals("Proveedor Uno", fila.proveedorNombre());
        assertEquals("11.111.111-1", fila.proveedorRut());
        assertEquals(2, fila.cantidadItems());
        assertEquals(new BigDecimal("1000"), fila.montoNeto());
        assertEquals(new BigDecimal("190.00"), fila.montoIva());
        assertEquals(new BigDecimal("1190.00"), fila.montoTotal());
        assertEquals("En deuda", fila.estadoPago());
    }

    @Test
    void mapeaTodosLosEstadosDePagoConLasMismasEtiquetasQueElFrontend() {
        Compra c1 = compra(1L, 1L, null, new BigDecimal("100"), 1, LocalDate.of(2026, 9, 1));
        Compra c2 = compra(2L, 1L, null, new BigDecimal("100"), 1, LocalDate.of(2026, 9, 2));
        Compra c3 = compra(3L, 1L, null, new BigDecimal("100"), 1, LocalDate.of(2026, 9, 3));
        Compra c4 = compra(4L, 1L, null, new BigDecimal("100"), 1, LocalDate.of(2026, 9, 4));
        when(compraRepository.findByTenantIdAndActivoTrueAndFechaBetweenOrderByFechaAsc(eq(tenantId), any(), any()))
                .thenReturn(List.of(c1, c2, c3, c4));
        when(proveedorRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of(proveedorUno));
        when(cuentaPorPagarRepository.findByTenantIdAndCompraIdIn(eq(tenantId), any())).thenReturn(List.of(
                cuenta(1L, EstadoCuentaPorPagar.DEUDA),
                cuenta(2L, EstadoCuentaPorPagar.PARCIAL),
                cuenta(3L, EstadoCuentaPorPagar.PAGADO),
                cuenta(4L, EstadoCuentaPorPagar.ANULADO)
        ));

        var libro = service.generar(tenantId, desde, hasta);

        assertEquals("En deuda", libro.filas().get(0).estadoPago());
        assertEquals("Parcial", libro.filas().get(1).estadoPago());
        assertEquals("Pagado", libro.filas().get(2).estadoPago());
        assertEquals("Anulado", libro.filas().get(3).estadoPago());
    }

    @Test
    void unaCompraSinCuentaPorPagarAsociadaResuelveEstadoGuion() {
        Compra c1 = compra(1L, 1L, null, new BigDecimal("100"), 1, LocalDate.of(2026, 9, 1));
        when(compraRepository.findByTenantIdAndActivoTrueAndFechaBetweenOrderByFechaAsc(eq(tenantId), any(), any()))
                .thenReturn(List.of(c1));
        when(proveedorRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of(proveedorUno));
        when(cuentaPorPagarRepository.findByTenantIdAndCompraIdIn(eq(tenantId), any())).thenReturn(List.of());

        var libro = service.generar(tenantId, desde, hasta);

        assertEquals("—", libro.filas().get(0).estadoPago());
    }

    @Test
    void unProveedorInexistenteResuelveGuionSinLanzarExcepcion() {
        Compra c1 = compra(1L, 99L, null, new BigDecimal("100"), 1, LocalDate.of(2026, 9, 1));
        when(compraRepository.findByTenantIdAndActivoTrueAndFechaBetweenOrderByFechaAsc(eq(tenantId), any(), any()))
                .thenReturn(List.of(c1));
        when(proveedorRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of());
        when(cuentaPorPagarRepository.findByTenantIdAndCompraIdIn(eq(tenantId), any())).thenReturn(List.of());

        var libro = service.generar(tenantId, desde, hasta);

        assertEquals("—", libro.filas().get(0).proveedorNombre());
        assertNull(libro.filas().get(0).proveedorRut());
    }

    @Test
    void elResumenSumaCantidadYMontosDeTodasLasFilas() {
        Compra c1 = compra(1L, 1L, null, new BigDecimal("1000"), 1, LocalDate.of(2026, 9, 1));
        Compra c2 = compra(2L, 2L, null, new BigDecimal("500"), 1, LocalDate.of(2026, 9, 2));
        when(compraRepository.findByTenantIdAndActivoTrueAndFechaBetweenOrderByFechaAsc(eq(tenantId), any(), any()))
                .thenReturn(List.of(c1, c2));
        when(proveedorRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of(proveedorUno, proveedorDos));
        when(cuentaPorPagarRepository.findByTenantIdAndCompraIdIn(eq(tenantId), any())).thenReturn(List.of());

        var libro = service.generar(tenantId, desde, hasta);

        assertEquals(2, libro.resumen().cantidadCompras());
        assertEquals(new BigDecimal("1500"), libro.resumen().montoNeto());
        assertEquals(new BigDecimal("285.00"), libro.resumen().montoIva());
        assertEquals(new BigDecimal("1785.00"), libro.resumen().montoTotal());
    }

    @Test
    void laEvolucionIncluyeTodosLosDiasDelRangoConCerosEnLosDiasSinCompras() {
        Compra c1 = compra(1L, 1L, null, new BigDecimal("100"), 1, LocalDate.of(2026, 9, 2));
        LocalDate desdeCorto = LocalDate.of(2026, 9, 1);
        LocalDate hastaCorto = LocalDate.of(2026, 9, 3);
        when(compraRepository.findByTenantIdAndActivoTrueAndFechaBetweenOrderByFechaAsc(eq(tenantId), any(), any()))
                .thenReturn(List.of(c1));
        when(proveedorRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of(proveedorUno));
        when(cuentaPorPagarRepository.findByTenantIdAndCompraIdIn(eq(tenantId), any())).thenReturn(List.of());

        var libro = service.generar(tenantId, desdeCorto, hastaCorto);

        assertEquals(3, libro.evolucion().size());
        assertEquals(BigDecimal.ZERO.setScale(2), libro.evolucion().get(0).total().setScale(2));
        assertEquals(new BigDecimal("119.00"), libro.evolucion().get(1).total());
        assertEquals(BigDecimal.ZERO.setScale(2), libro.evolucion().get(2).total().setScale(2));
    }

    @Test
    void unRangoSinComprasDevuelveListasVaciasYResumenEnCero() {
        when(compraRepository.findByTenantIdAndActivoTrueAndFechaBetweenOrderByFechaAsc(eq(tenantId), any(), any()))
                .thenReturn(List.of());

        var libro = service.generar(tenantId, desde, hasta);

        assertTrue(libro.filas().isEmpty());
        assertEquals(0, libro.resumen().cantidadCompras());
        assertEquals(BigDecimal.ZERO, libro.resumen().montoNeto());
        verify(proveedorRepository, never()).findByTenantIdAndIdIn(any(), any());
        verify(cuentaPorPagarRepository, never()).findByTenantIdAndCompraIdIn(any(), any());
    }

    @Test
    void lanzaExcepcionSiDesdeEsPosteriorAHasta() {
        assertThrows(IllegalArgumentException.class, () -> service.generar(tenantId, hasta, desde));
        verifyNoInteractions(compraRepository);
    }
}
