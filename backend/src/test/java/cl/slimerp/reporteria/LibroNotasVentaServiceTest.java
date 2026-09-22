package cl.slimerp.reporteria;

import cl.slimerp.catalogo.Cliente;
import cl.slimerp.catalogo.ClienteRepository;
import cl.slimerp.cotizaciones.Cotizacion;
import cl.slimerp.cotizaciones.CotizacionRepository;
import cl.slimerp.notasventa.EstadoNotaVenta;
import cl.slimerp.notasventa.NotaVenta;
import cl.slimerp.notasventa.NotaVentaDocumento;
import cl.slimerp.notasventa.NotaVentaDocumentoRepository;
import cl.slimerp.notasventa.NotaVentaRepository;
import cl.slimerp.notasventa.OrigenNotaVenta;
import cl.slimerp.tenant.Usuario;
import cl.slimerp.tenant.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LibroNotasVentaServiceTest {

    private NotaVentaRepository notaVentaRepository;
    private NotaVentaDocumentoRepository documentoRepository;
    private ClienteRepository clienteRepository;
    private UsuarioRepository usuarioRepository;
    private CotizacionRepository cotizacionRepository;
    private LibroNotasVentaService service;

    private final Long tenantId = 1L;
    private final LocalDate desde = LocalDate.of(2026, 9, 1);
    private final LocalDate hasta = LocalDate.of(2026, 9, 30);

    private final Cliente cliente = Cliente.builder().id(5L).tenantId(1L).nombre("Empresa ABC SpA")
            .rut("76.111.222-3").activo(true).build();
    private final Usuario vendedor = Usuario.builder().id(7L).tenantId(1L).nombre("Vendedor Demo").build();

    @BeforeEach
    void setUp() {
        notaVentaRepository = mock(NotaVentaRepository.class);
        documentoRepository = mock(NotaVentaDocumentoRepository.class);
        clienteRepository = mock(ClienteRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        cotizacionRepository = mock(CotizacionRepository.class);
        service = new LibroNotasVentaService(notaVentaRepository, documentoRepository, clienteRepository,
                usuarioRepository, cotizacionRepository);

        when(clienteRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of(cliente));
        when(usuarioRepository.findAllById(any())).thenReturn(List.of(vendedor));
        when(documentoRepository.findByTenantIdAndNotaVentaIdIn(eq(tenantId), any())).thenReturn(List.of());
    }

    private NotaVenta nota(Long id, EstadoNotaVenta estado, String neto, String iva, String total) {
        return NotaVenta.builder().id(id).tenantId(tenantId).folio(id.intValue()).clienteId(5L).vendedorId(7L)
                .estado(estado).fechaEmision(LocalDate.of(2026, 9, 10))
                .montoNeto(new BigDecimal(neto)).montoIva(new BigDecimal(iva)).montoTotal(new BigDecimal(total))
                .build();
    }

    @Test
    void conservaTodosLosEstadosIncluidasLasCanceladas() {
        when(notaVentaRepository.findByTenantIdAndFechaEmisionBetweenOrderByFolioAsc(tenantId, desde, hasta))
                .thenReturn(List.of(
                        nota(1L, EstadoNotaVenta.CONFIRMADA, "1000", "190", "1190"),
                        nota(2L, EstadoNotaVenta.ENTREGADA, "2000", "380", "2380"),
                        nota(3L, EstadoNotaVenta.FACTURADA, "3000", "570", "3570"),
                        nota(4L, EstadoNotaVenta.CANCELADA, "4000", "760", "4760")));

        var libro = service.generar(tenantId, desde, hasta, null);

        assertEquals(4, libro.filas().size());
        assertEquals(4, libro.resumen().cantidad());
        assertEquals(new BigDecimal("10000"), libro.resumen().montoNeto());
        assertEquals(new BigDecimal("1900"), libro.resumen().montoIva());
        assertEquals(new BigDecimal("11900"), libro.resumen().montoTotal());
    }

    @Test
    void filtraPorEstadoCuandoSeIndica() {
        when(notaVentaRepository.findByTenantIdAndFechaEmisionBetweenOrderByFolioAsc(tenantId, desde, hasta))
                .thenReturn(List.of(
                        nota(1L, EstadoNotaVenta.CONFIRMADA, "1000", "190", "1190"),
                        nota(2L, EstadoNotaVenta.CANCELADA, "2000", "380", "2380")));

        var libro = service.generar(tenantId, desde, hasta, EstadoNotaVenta.CANCELADA);

        assertEquals(1, libro.filas().size());
        assertEquals(EstadoNotaVenta.CANCELADA, libro.filas().get(0).estado());
        assertEquals(EstadoNotaVenta.CANCELADA, libro.estado());
    }

    @Test
    void resuelveNumeroClienteVendedorYOrigenDeCadaFila() {
        when(notaVentaRepository.findByTenantIdAndFechaEmisionBetweenOrderByFolioAsc(tenantId, desde, hasta))
                .thenReturn(List.of(nota(7L, EstadoNotaVenta.CONFIRMADA, "1000", "190", "1190")));

        var fila = service.generar(tenantId, desde, hasta, null).filas().get(0);

        assertEquals("NV-000007", fila.numero());
        assertEquals("Empresa ABC SpA", fila.clienteNombre());
        assertEquals("76.111.222-3", fila.clienteRut());
        assertEquals("Vendedor Demo", fila.vendedor());
        assertEquals("Venta directa", fila.origen());
        assertEquals("—", fila.documentosRelacionados());
    }

    @Test
    void muestraLaCotizacionDeOrigenCuandoLaNotaVieneDeUnaCotizacion() {
        NotaVenta nota = nota(1L, EstadoNotaVenta.CONFIRMADA, "1000", "190", "1190");
        nota.setOrigen(OrigenNotaVenta.COTIZACION);
        nota.setCotizacionId(300L);
        when(notaVentaRepository.findByTenantIdAndFechaEmisionBetweenOrderByFolioAsc(tenantId, desde, hasta))
                .thenReturn(List.of(nota));
        when(cotizacionRepository.findAllById(List.of(300L))).thenReturn(List.of(
                Cotizacion.builder().id(300L).folio(12).build()));

        var fila = service.generar(tenantId, desde, hasta, null).filas().get(0);

        assertEquals("Cotización COT-000012", fila.origen());
    }

    @Test
    void listaLosDocumentosRelacionadosDeCadaNota() {
        when(notaVentaRepository.findByTenantIdAndFechaEmisionBetweenOrderByFolioAsc(tenantId, desde, hasta))
                .thenReturn(List.of(nota(1L, EstadoNotaVenta.ENTREGADA, "1000", "190", "1190")));
        when(documentoRepository.findByTenantIdAndNotaVentaIdIn(eq(tenantId), any())).thenReturn(List.of(
                NotaVentaDocumento.builder().id(1L).tenantId(tenantId).notaVentaId(1L)
                        .tipoDocumento("GUIA").documentoId(77L).numero("GUI-000001").build()));

        var fila = service.generar(tenantId, desde, hasta, null).filas().get(0);

        assertEquals("GUI-000001", fila.documentosRelacionados());
    }

    @Test
    void unClienteODeUnUsuarioInexistenteNoRompeElLibro() {
        when(notaVentaRepository.findByTenantIdAndFechaEmisionBetweenOrderByFolioAsc(tenantId, desde, hasta))
                .thenReturn(List.of(nota(1L, EstadoNotaVenta.CONFIRMADA, "1000", "190", "1190")));
        when(clienteRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of());
        when(usuarioRepository.findAllById(any())).thenReturn(List.of());

        var fila = service.generar(tenantId, desde, hasta, null).filas().get(0);

        assertEquals("—", fila.clienteNombre());
        assertNull(fila.clienteRut());
        assertEquals("—", fila.vendedor());
    }

    @Test
    void unPeriodoSinNotasDevuelveResumenEnCeroSinConsultarClientes() {
        when(notaVentaRepository.findByTenantIdAndFechaEmisionBetweenOrderByFolioAsc(tenantId, desde, hasta))
                .thenReturn(List.of());

        var libro = service.generar(tenantId, desde, hasta, null);

        assertTrue(libro.filas().isEmpty());
        assertEquals(0, libro.resumen().cantidad());
        assertEquals(BigDecimal.ZERO, libro.resumen().montoTotal());
        verifyNoInteractions(clienteRepository);
    }

    @Test
    void rechazaUnRangoDeFechasInvertido() {
        assertThrows(IllegalArgumentException.class, () -> service.generar(tenantId, hasta, desde, null));
        verifyNoInteractions(notaVentaRepository);
    }
}