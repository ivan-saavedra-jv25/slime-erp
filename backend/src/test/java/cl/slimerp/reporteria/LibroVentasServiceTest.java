package cl.slimerp.reporteria;

import cl.slimerp.catalogo.Cliente;
import cl.slimerp.catalogo.ClienteRepository;
import cl.slimerp.ventas.CodigoSiiVenta;
import cl.slimerp.ventas.TipoDocumentoVenta;
import cl.slimerp.ventas.Venta;
import cl.slimerp.ventas.VentaRepository;
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

class LibroVentasServiceTest {

    private VentaRepository ventaRepository;
    private ClienteRepository clienteRepository;
    private LibroVentasService service;

    private final Long tenantId = 1L;
    private final LocalDate desde = LocalDate.of(2026, 9, 1);
    private final LocalDate hasta = LocalDate.of(2026, 9, 30);

    private final Cliente clienteUno = Cliente.builder().id(1L).tenantId(1L).nombre("Cliente Uno").rut("11.111.111-1").activo(true).build();
    private final Cliente clienteDos = Cliente.builder().id(2L).tenantId(1L).nombre("Cliente Dos").rut("22.222.222-2").activo(true).build();

    @BeforeEach
    void setUp() {
        ventaRepository = mock(VentaRepository.class);
        clienteRepository = mock(ClienteRepository.class);
        service = new LibroVentasService(ventaRepository, clienteRepository);
    }

    private Venta venta(Long id, Long clienteId, TipoDocumentoVenta tipo, boolean exento,
                         BigDecimal neto, BigDecimal iva, BigDecimal total) {
        return Venta.builder()
                .id(id).tenantId(tenantId).clienteId(clienteId).formaPagoId(1L).bodegaId(1L)
                .tipoDocumento(tipo).exento(exento)
                .folio(id.intValue())
                .codigoSii(CodigoSiiVenta.codigo(tipo, exento))
                .fecha(LocalDateTime.of(2026, 9, 10, 12, 0))
                .montoNeto(neto).montoIva(iva).montoTotal(total)
                .build();
    }

    @Test
    void agrupaYSubtotalizaPorTipoDeDocumentoEnElOrdenFacturaBoletaVoucher() {
        List<Venta> ventas = List.of(
                venta(1L, 1L, TipoDocumentoVenta.FACTURA, false, new BigDecimal("1000"), new BigDecimal("190"), new BigDecimal("1190")),
                venta(2L, 1L, TipoDocumentoVenta.FACTURA, true, new BigDecimal("500"), BigDecimal.ZERO, new BigDecimal("500")),
                venta(3L, 2L, TipoDocumentoVenta.BOLETA, false, new BigDecimal("841"), new BigDecimal("159"), new BigDecimal("1000")),
                venta(4L, 2L, TipoDocumentoVenta.BOLETA, true, new BigDecimal("300"), BigDecimal.ZERO, new BigDecimal("300")),
                venta(5L, 1L, TipoDocumentoVenta.VOUCHER, false, new BigDecimal("200"), BigDecimal.ZERO, new BigDecimal("200"))
        );
        when(ventaRepository.findByTenantIdAndActivoTrueAndFechaBetweenOrderByFechaAsc(eq(tenantId), any(), any()))
                .thenReturn(ventas);
        when(clienteRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of(clienteUno, clienteDos));

        var libro = service.generar(tenantId, desde, hasta, null, null, 0, 100);

        assertEquals(5, libro.subtotales().stream().mapToInt(LibroVentasService.LibroVentasSubtotal::cantidad).sum());
        assertEquals(List.of("Factura", "Factura Exenta", "Boleta", "Boleta Exenta", "Voucher"),
                libro.subtotales().stream().map(LibroVentasService.LibroVentasSubtotal::tipoDocumento).toList());

        var subtotalFactura = libro.subtotales().get(0);
        assertEquals(1, subtotalFactura.cantidad());
        assertEquals(new BigDecimal("1000"), subtotalFactura.montoNetoAfecto());
        assertEquals(BigDecimal.ZERO.setScale(2), subtotalFactura.montoNetoExento());
        assertEquals(new BigDecimal("190"), subtotalFactura.montoIva());
        assertEquals(new BigDecimal("1190"), subtotalFactura.montoTotal());
    }

    @Test
    void separaElNetoEnAfectoOExentoSegunElFlagExentoDeCadaVenta() {
        List<Venta> ventas = List.of(
                venta(1L, 1L, TipoDocumentoVenta.FACTURA, false, new BigDecimal("1000"), new BigDecimal("190"), new BigDecimal("1190")),
                venta(2L, 1L, TipoDocumentoVenta.FACTURA, true, new BigDecimal("500"), BigDecimal.ZERO, new BigDecimal("500"))
        );
        when(ventaRepository.findByTenantIdAndActivoTrueAndFechaBetweenOrderByFechaAsc(eq(tenantId), any(), any()))
                .thenReturn(ventas);
        when(clienteRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of(clienteUno));

        var libro = service.generar(tenantId, desde, hasta, null, null, 0, 100);

        var filaAfecta = libro.filas().stream().filter(f -> f.ventaId().equals(1L)).findFirst().orElseThrow();
        assertEquals(new BigDecimal("1000"), filaAfecta.montoNetoAfecto());
        assertEquals(BigDecimal.ZERO.setScale(2), filaAfecta.montoNetoExento());

        var filaExenta = libro.filas().stream().filter(f -> f.ventaId().equals(2L)).findFirst().orElseThrow();
        assertEquals(BigDecimal.ZERO.setScale(2), filaExenta.montoNetoAfecto());
        assertEquals(new BigDecimal("500"), filaExenta.montoNetoExento());

        // El subtotal "Factura" agrupa ambas filas (misma etiqueta la Factura Exenta
        // tendría su propio subtotal si hubiera más de una venta de ese tipo real).
        var subtotalFactura = libro.subtotales().stream()
                .filter(s -> s.tipoDocumento().equals("Factura")).findFirst().orElseThrow();
        assertEquals(new BigDecimal("1000"), subtotalFactura.montoNetoAfecto());
        assertEquals(BigDecimal.ZERO.setScale(2), subtotalFactura.montoNetoExento());

        var subtotalFacturaExenta = libro.subtotales().stream()
                .filter(s -> s.tipoDocumento().equals("Factura Exenta")).findFirst().orElseThrow();
        assertEquals(BigDecimal.ZERO.setScale(2), subtotalFacturaExenta.montoNetoAfecto());
        assertEquals(new BigDecimal("500"), subtotalFacturaExenta.montoNetoExento());
    }

    @Test
    void losTiposSinVentasEnElPeriodoNoAparecenEnLosSubtotales() {
        List<Venta> ventas = List.of(
                venta(1L, 1L, TipoDocumentoVenta.FACTURA, false, new BigDecimal("1000"), new BigDecimal("190"), new BigDecimal("1190")),
                venta(2L, 1L, TipoDocumentoVenta.VOUCHER, false, new BigDecimal("200"), BigDecimal.ZERO, new BigDecimal("200"))
        );
        when(ventaRepository.findByTenantIdAndActivoTrueAndFechaBetweenOrderByFechaAsc(eq(tenantId), any(), any()))
                .thenReturn(ventas);
        when(clienteRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of(clienteUno));

        var libro = service.generar(tenantId, desde, hasta, null, null, 0, 100);

        assertEquals(2, libro.subtotales().size());
        assertEquals(List.of("Factura", "Voucher"),
                libro.subtotales().stream().map(LibroVentasService.LibroVentasSubtotal::tipoDocumento).toList());
    }

    @Test
    void elTotalGeneralSumaTodasLasFilasSinImportarElTipo() {
        List<Venta> ventas = List.of(
                venta(1L, 1L, TipoDocumentoVenta.FACTURA, false, new BigDecimal("1000"), new BigDecimal("190"), new BigDecimal("1190")),
                venta(2L, 2L, TipoDocumentoVenta.BOLETA, false, new BigDecimal("500"), new BigDecimal("95"), new BigDecimal("595"))
        );
        when(ventaRepository.findByTenantIdAndActivoTrueAndFechaBetweenOrderByFechaAsc(eq(tenantId), any(), any()))
                .thenReturn(ventas);
        when(clienteRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of(clienteUno, clienteDos));

        var libro = service.generar(tenantId, desde, hasta, null, null, 0, 100);

        assertEquals("Total", libro.totalGeneral().tipoDocumento());
        assertEquals(2, libro.totalGeneral().cantidad());
        assertEquals(new BigDecimal("1500"), libro.totalGeneral().montoNetoAfecto());
        assertEquals(BigDecimal.ZERO.setScale(2), libro.totalGeneral().montoNetoExento());
        assertEquals(new BigDecimal("285"), libro.totalGeneral().montoIva());
        assertEquals(new BigDecimal("1785"), libro.totalGeneral().montoTotal());
    }

    @Test
    void unRangoSinVentasDevuelveListasVaciasYTotalGeneralEnCero() {
        when(ventaRepository.findByTenantIdAndActivoTrueAndFechaBetweenOrderByFechaAsc(eq(tenantId), any(), any()))
                .thenReturn(List.of());

        var libro = service.generar(tenantId, desde, hasta, null, null, 0, 100);

        assertTrue(libro.filas().isEmpty());
        assertTrue(libro.subtotales().isEmpty());
        assertEquals(0, libro.totalGeneral().cantidad());
        assertEquals(BigDecimal.ZERO, libro.totalGeneral().montoNetoAfecto());
        assertEquals(BigDecimal.ZERO, libro.totalGeneral().montoNetoExento());
        verify(clienteRepository, never()).findByTenantIdAndIdIn(any(), any());
    }

    @Test
    void resuelveNombreYRutDelClienteYUsaGuionSiNoExiste() {
        List<Venta> ventas = List.of(
                venta(1L, 1L, TipoDocumentoVenta.FACTURA, false, new BigDecimal("1000"), new BigDecimal("190"), new BigDecimal("1190")),
                venta(2L, 99L, TipoDocumentoVenta.BOLETA, false, new BigDecimal("500"), new BigDecimal("95"), new BigDecimal("595"))
        );
        when(ventaRepository.findByTenantIdAndActivoTrueAndFechaBetweenOrderByFechaAsc(eq(tenantId), any(), any()))
                .thenReturn(ventas);
        // clienteId 99 no existe (desactivado o eliminado): el repo simplemente no lo devuelve.
        when(clienteRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of(clienteUno));

        var libro = service.generar(tenantId, desde, hasta, null, null, 0, 100);

        var filaClienteUno = libro.filas().stream().filter(f -> f.ventaId().equals(1L)).findFirst().orElseThrow();
        assertEquals("11.111.111-1", filaClienteUno.clienteRut());
        assertEquals("Cliente Uno", filaClienteUno.clienteNombre());

        var filaClienteInexistente = libro.filas().stream().filter(f -> f.ventaId().equals(2L)).findFirst().orElseThrow();
        assertNull(filaClienteInexistente.clienteRut());
        assertEquals("—", filaClienteInexistente.clienteNombre());
    }

    @Test
    void elRangoDeFechasSeConvierteAInicioYFinDelDiaAlConsultarElRepositorio() {
        when(ventaRepository.findByTenantIdAndActivoTrueAndFechaBetweenOrderByFechaAsc(any(), any(), any()))
                .thenReturn(List.of());

        service.generar(tenantId, desde, hasta, null, null, 0, 100);

        verify(ventaRepository).findByTenantIdAndActivoTrueAndFechaBetweenOrderByFechaAsc(
                tenantId, desde.atStartOfDay(), hasta.atTime(LocalTime.MAX));
    }

    @Test
    void lanzaExcepcionSiDesdeEsPosteriorAHasta() {
        assertThrows(IllegalArgumentException.class, () -> service.generar(tenantId, hasta, desde, null, null, 0, 100));
        verifyNoInteractions(ventaRepository);
    }

    @Test
    void filtraPorTipoDocumentoCuandoSeEspecifica() {
        List<Venta> ventas = List.of(
                venta(1L, 1L, TipoDocumentoVenta.FACTURA, false, new BigDecimal("1000"), new BigDecimal("190"), new BigDecimal("1190")),
                venta(2L, 1L, TipoDocumentoVenta.BOLETA, false, new BigDecimal("500"), new BigDecimal("95"), new BigDecimal("595")),
                venta(3L, 1L, TipoDocumentoVenta.VOUCHER, false, new BigDecimal("200"), BigDecimal.ZERO, new BigDecimal("200"))
        );
        when(ventaRepository.findByTenantIdAndActivoTrueAndFechaBetweenOrderByFechaAsc(eq(tenantId), any(), any()))
                .thenReturn(ventas);
        when(clienteRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of(clienteUno));

        var libro = service.generar(tenantId, desde, hasta, "Factura", null, 0, 100);

        assertEquals("Factura", libro.tipoDocumento());
        assertEquals(1, libro.filas().size());
        assertEquals(1L, libro.filas().get(0).ventaId());
        assertEquals(1, libro.subtotales().size());
        assertEquals("Factura", libro.subtotales().get(0).tipoDocumento());
        assertEquals(1, libro.totalGeneral().cantidad());
        assertEquals(new BigDecimal("1000"), libro.totalGeneral().montoNetoAfecto());
    }

    @Test
    void sinFiltroDeTipoDocumentoElResultadoTraeTodosLosTiposYTipoDocumentoNull() {
        when(ventaRepository.findByTenantIdAndActivoTrueAndFechaBetweenOrderByFechaAsc(eq(tenantId), any(), any()))
                .thenReturn(List.of(venta(1L, 1L, TipoDocumentoVenta.FACTURA, false, new BigDecimal("1000"), new BigDecimal("190"), new BigDecimal("1190"))));
        when(clienteRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of(clienteUno));

        var libro = service.generar(tenantId, desde, hasta, null, null, 0, 100);

        assertNull(libro.tipoDocumento());
        assertEquals(1, libro.filas().size());
    }

    @Test
    void lanzaExcepcionSiElTipoDocumentoNoEsUnoDeLosCincoReconocidos() {
        assertThrows(IllegalArgumentException.class,
                () -> service.generar(tenantId, desde, hasta, "Nota de Crédito", null, 0, 100));
        verifyNoInteractions(ventaRepository);
    }

    @Test
    void buscaPorNombreORutDelCliente() {
        List<Venta> ventas = List.of(
                venta(1L, 1L, TipoDocumentoVenta.FACTURA, false, new BigDecimal("1000"), new BigDecimal("190"), new BigDecimal("1190")),
                venta(2L, 2L, TipoDocumentoVenta.BOLETA, false, new BigDecimal("500"), new BigDecimal("95"), new BigDecimal("595"))
        );
        when(ventaRepository.findByTenantIdAndActivoTrueAndFechaBetweenOrderByFechaAsc(eq(tenantId), any(), any()))
                .thenReturn(ventas);
        when(clienteRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of(clienteUno, clienteDos));

        var libroPorNombre = service.generar(tenantId, desde, hasta, null, "uno", 0, 100);
        assertEquals(1, libroPorNombre.filas().size());
        assertEquals(1L, libroPorNombre.filas().get(0).ventaId());

        var libroPorRut = service.generar(tenantId, desde, hasta, null, "22.222", 0, 100);
        assertEquals(1, libroPorRut.filas().size());
        assertEquals(2L, libroPorRut.filas().get(0).ventaId());
    }

    @Test
    void buscaPorNumeroDeVenta() {
        List<Venta> ventas = List.of(
                venta(11L, 1L, TipoDocumentoVenta.FACTURA, false, new BigDecimal("1000"), new BigDecimal("190"), new BigDecimal("1190")),
                venta(25L, 1L, TipoDocumentoVenta.BOLETA, false, new BigDecimal("500"), new BigDecimal("95"), new BigDecimal("595"))
        );
        when(ventaRepository.findByTenantIdAndActivoTrueAndFechaBetweenOrderByFechaAsc(eq(tenantId), any(), any()))
                .thenReturn(ventas);
        when(clienteRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of(clienteUno));

        var libro = service.generar(tenantId, desde, hasta, null, "25", 0, 100);

        assertEquals(1, libro.filas().size());
        assertEquals(25L, libro.filas().get(0).ventaId());
    }

    @Test
    void unaBusquedaSinCoincidenciasDevuelveListaVaciaYSubtotalesVacios() {
        List<Venta> ventas = List.of(
                venta(1L, 1L, TipoDocumentoVenta.FACTURA, false, new BigDecimal("1000"), new BigDecimal("190"), new BigDecimal("1190"))
        );
        when(ventaRepository.findByTenantIdAndActivoTrueAndFechaBetweenOrderByFechaAsc(eq(tenantId), any(), any()))
                .thenReturn(ventas);
        when(clienteRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of(clienteUno));

        var libro = service.generar(tenantId, desde, hasta, null, "no existe nadie así", 0, 100);

        assertTrue(libro.filas().isEmpty());
        assertTrue(libro.subtotales().isEmpty());
        assertEquals(0, libro.totalFilas());
    }

    @Test
    void paginaLasFilasPeroLosSubtotalesReflejanTodoElResultadoFiltrado() {
        List<Venta> ventas = List.of(
                venta(1L, 1L, TipoDocumentoVenta.FACTURA, false, new BigDecimal("1000"), new BigDecimal("190"), new BigDecimal("1190")),
                venta(2L, 1L, TipoDocumentoVenta.FACTURA, false, new BigDecimal("2000"), new BigDecimal("380"), new BigDecimal("2380")),
                venta(3L, 1L, TipoDocumentoVenta.FACTURA, false, new BigDecimal("3000"), new BigDecimal("570"), new BigDecimal("3570"))
        );
        when(ventaRepository.findByTenantIdAndActivoTrueAndFechaBetweenOrderByFechaAsc(eq(tenantId), any(), any()))
                .thenReturn(ventas);
        when(clienteRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of(clienteUno));

        var libro = service.generar(tenantId, desde, hasta, null, null, 0, 2);

        assertEquals(2, libro.filas().size());
        assertEquals(1L, libro.filas().get(0).ventaId());
        assertEquals(2L, libro.filas().get(1).ventaId());
        assertEquals(3, libro.totalFilas());
        assertEquals(0, libro.pagina());
        assertEquals(2, libro.tamano());
        // El subtotal/total suma las 3 ventas, no solo las 2 de la página visible.
        assertEquals(3, libro.totalGeneral().cantidad());
        assertEquals(new BigDecimal("6000"), libro.totalGeneral().montoNetoAfecto());
    }

    @Test
    void laSegundaPaginaTraeElRestoDeLasFilas() {
        List<Venta> ventas = List.of(
                venta(1L, 1L, TipoDocumentoVenta.FACTURA, false, new BigDecimal("1000"), new BigDecimal("190"), new BigDecimal("1190")),
                venta(2L, 1L, TipoDocumentoVenta.FACTURA, false, new BigDecimal("2000"), new BigDecimal("380"), new BigDecimal("2380")),
                venta(3L, 1L, TipoDocumentoVenta.FACTURA, false, new BigDecimal("3000"), new BigDecimal("570"), new BigDecimal("3570"))
        );
        when(ventaRepository.findByTenantIdAndActivoTrueAndFechaBetweenOrderByFechaAsc(eq(tenantId), any(), any()))
                .thenReturn(ventas);
        when(clienteRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of(clienteUno));

        var libro = service.generar(tenantId, desde, hasta, null, null, 1, 2);

        assertEquals(1, libro.filas().size());
        assertEquals(3L, libro.filas().get(0).ventaId());
    }

    @Test
    void unaPaginaQueSuperaElTotalDevuelveFilasVacias() {
        when(ventaRepository.findByTenantIdAndActivoTrueAndFechaBetweenOrderByFechaAsc(eq(tenantId), any(), any()))
                .thenReturn(List.of(venta(1L, 1L, TipoDocumentoVenta.FACTURA, false, new BigDecimal("1000"), new BigDecimal("190"), new BigDecimal("1190"))));
        when(clienteRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of(clienteUno));

        var libro = service.generar(tenantId, desde, hasta, null, null, 5, 10);

        assertTrue(libro.filas().isEmpty());
        assertEquals(1, libro.totalFilas());
    }

    @Test
    void lanzaExcepcionSiLaPaginaEsNegativa() {
        assertThrows(IllegalArgumentException.class,
                () -> service.generar(tenantId, desde, hasta, null, null, -1, 10));
        verifyNoInteractions(ventaRepository);
    }

    @Test
    void lanzaExcepcionSiElTamanoEsMenorAUno() {
        assertThrows(IllegalArgumentException.class,
                () -> service.generar(tenantId, desde, hasta, null, null, 0, 0));
        verifyNoInteractions(ventaRepository);
    }

    @Test
    void generarCompletoNoPaginaYDevuelveTodasLasFilasQueCalzanConLosFiltros() {
        List<Venta> ventas = List.of(
                venta(1L, 1L, TipoDocumentoVenta.FACTURA, false, new BigDecimal("1000"), new BigDecimal("190"), new BigDecimal("1190")),
                venta(2L, 1L, TipoDocumentoVenta.FACTURA, false, new BigDecimal("2000"), new BigDecimal("380"), new BigDecimal("2380")),
                venta(3L, 1L, TipoDocumentoVenta.FACTURA, false, new BigDecimal("3000"), new BigDecimal("570"), new BigDecimal("3570"))
        );
        when(ventaRepository.findByTenantIdAndActivoTrueAndFechaBetweenOrderByFechaAsc(eq(tenantId), any(), any()))
                .thenReturn(ventas);
        when(clienteRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of(clienteUno));

        var libro = service.generarCompleto(tenantId, desde, hasta, null, null);

        assertEquals(3, libro.filas().size());
        assertEquals(3, libro.totalFilas());
        assertEquals(3, libro.totalGeneral().cantidad());
    }

    @Test
    void incluyeElFolioYElCodigoSiiDeCadaVentaYNullParaVoucher() {
        List<Venta> ventas = List.of(
                venta(1L, 1L, TipoDocumentoVenta.FACTURA, false, new BigDecimal("1000"), new BigDecimal("190"), new BigDecimal("1190")),
                venta(2L, 1L, TipoDocumentoVenta.VOUCHER, false, new BigDecimal("500"), BigDecimal.ZERO, new BigDecimal("500"))
        );
        when(ventaRepository.findByTenantIdAndActivoTrueAndFechaBetweenOrderByFechaAsc(eq(tenantId), any(), any()))
                .thenReturn(ventas);
        when(clienteRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of(clienteUno));

        var libro = service.generar(tenantId, desde, hasta, null, null, 0, 100);

        var filaFactura = libro.filas().stream().filter(f -> f.ventaId().equals(1L)).findFirst().orElseThrow();
        assertEquals(1, filaFactura.folio());
        assertEquals(33, filaFactura.codigoSii());

        var filaVoucher = libro.filas().stream().filter(f -> f.ventaId().equals(2L)).findFirst().orElseThrow();
        assertEquals(2, filaVoucher.folio());
        assertNull(filaVoucher.codigoSii());
    }
}
