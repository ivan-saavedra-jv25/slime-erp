package cl.slimerp.reporteria;

import cl.slimerp.catalogo.Cliente;
import cl.slimerp.catalogo.ClienteRepository;
import cl.slimerp.cotizaciones.Cotizacion;
import cl.slimerp.cotizaciones.CotizacionDocumento;
import cl.slimerp.cotizaciones.CotizacionDocumentoRepository;
import cl.slimerp.cotizaciones.CotizacionRepository;
import cl.slimerp.cotizaciones.EstadoCotizacion;
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

class LibroCotizacionesServiceTest {

    private CotizacionRepository cotizacionRepository;
    private CotizacionDocumentoRepository documentoRepository;
    private ClienteRepository clienteRepository;
    private UsuarioRepository usuarioRepository;
    private LibroCotizacionesService service;

    private final Long tenantId = 1L;
    private final LocalDate desde = LocalDate.of(2026, 9, 1);
    private final LocalDate hasta = LocalDate.of(2026, 9, 30);

    private final Cliente cliente = Cliente.builder().id(5L).tenantId(1L).nombre("Empresa ABC SpA")
            .rut("76.111.222-3").activo(true).build();
    private final Usuario vendedor = Usuario.builder().id(7L).tenantId(1L).nombre("Vendedor Demo").build();

    @BeforeEach
    void setUp() {
        cotizacionRepository = mock(CotizacionRepository.class);
        documentoRepository = mock(CotizacionDocumentoRepository.class);
        clienteRepository = mock(ClienteRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        service = new LibroCotizacionesService(cotizacionRepository, documentoRepository, clienteRepository,
                usuarioRepository);

        when(clienteRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of(cliente));
        when(usuarioRepository.findAllById(any())).thenReturn(List.of(vendedor));
        when(documentoRepository.findByTenantIdAndCotizacionIdIn(eq(tenantId), any())).thenReturn(List.of());
    }

    private Cotizacion cotizacion(Long id, EstadoCotizacion estado, String neto, String iva, String total) {
        return Cotizacion.builder().id(id).tenantId(tenantId).folio(id.intValue()).clienteId(5L).vendedorId(7L)
                .estado(estado).fechaEmision(LocalDate.of(2026, 9, 10)).fechaVencimiento(hasta)
                .montoNeto(new BigDecimal(neto)).montoIva(new BigDecimal(iva)).montoTotal(new BigDecimal(total))
                .build();
    }

    @Test
    void conservaTodosLosEstadosIncluidasRechazadasVencidasYCanceladas() {
        when(cotizacionRepository.findByTenantIdAndFechaEmisionBetweenOrderByFolioAsc(tenantId, desde, hasta))
                .thenReturn(List.of(
                        cotizacion(1L, EstadoCotizacion.ACEPTADA, "1000", "190", "1190"),
                        cotizacion(2L, EstadoCotizacion.RECHAZADA, "2000", "380", "2380"),
                        cotizacion(3L, EstadoCotizacion.VENCIDA, "3000", "570", "3570"),
                        cotizacion(4L, EstadoCotizacion.CANCELADA, "4000", "760", "4760")));

        var libro = service.generar(tenantId, desde, hasta, null);

        assertEquals(4, libro.filas().size());
        assertEquals(4, libro.resumen().cantidad());
        assertEquals(new BigDecimal("10000"), libro.resumen().montoNeto());
        assertEquals(new BigDecimal("1900"), libro.resumen().montoIva());
        assertEquals(new BigDecimal("11900"), libro.resumen().montoTotal());
    }

    @Test
    void filtraPorEstadoCuandoSeIndica() {
        when(cotizacionRepository.findByTenantIdAndFechaEmisionBetweenOrderByFolioAsc(tenantId, desde, hasta))
                .thenReturn(List.of(
                        cotizacion(1L, EstadoCotizacion.ACEPTADA, "1000", "190", "1190"),
                        cotizacion(2L, EstadoCotizacion.RECHAZADA, "2000", "380", "2380")));

        var libro = service.generar(tenantId, desde, hasta, EstadoCotizacion.ACEPTADA);

        assertEquals(1, libro.filas().size());
        assertEquals(EstadoCotizacion.ACEPTADA, libro.filas().get(0).estado());
        assertEquals(EstadoCotizacion.ACEPTADA, libro.estado());
    }

    @Test
    void resuelveNumeroClienteYUsuarioDeCadaFila() {
        when(cotizacionRepository.findByTenantIdAndFechaEmisionBetweenOrderByFolioAsc(tenantId, desde, hasta))
                .thenReturn(List.of(cotizacion(7L, EstadoCotizacion.ENVIADA, "1000", "190", "1190")));

        var fila = service.generar(tenantId, desde, hasta, null).filas().get(0);

        assertEquals("COT-000007", fila.numero());
        assertEquals("Empresa ABC SpA", fila.clienteNombre());
        assertEquals("76.111.222-3", fila.clienteRut());
        assertEquals("Vendedor Demo", fila.usuario());
        assertEquals("—", fila.documentosRelacionados());
    }

    @Test
    void listaLosDocumentosRelacionadosDeCadaCotizacion() {
        when(cotizacionRepository.findByTenantIdAndFechaEmisionBetweenOrderByFolioAsc(tenantId, desde, hasta))
                .thenReturn(List.of(cotizacion(1L, EstadoCotizacion.ACEPTADA, "1000", "190", "1190")));
        when(documentoRepository.findByTenantIdAndCotizacionIdIn(eq(tenantId), any())).thenReturn(List.of(
                CotizacionDocumento.builder().id(1L).tenantId(tenantId).cotizacionId(1L)
                        .tipoDocumento("NOTA_VENTA").documentoId(55L).numero("NV-000010").build()));

        var fila = service.generar(tenantId, desde, hasta, null).filas().get(0);

        assertEquals("NV-000010", fila.documentosRelacionados());
    }

    @Test
    void unClienteODeUnUsuarioInexistenteNoRompeElLibro() {
        when(cotizacionRepository.findByTenantIdAndFechaEmisionBetweenOrderByFolioAsc(tenantId, desde, hasta))
                .thenReturn(List.of(cotizacion(1L, EstadoCotizacion.ENVIADA, "1000", "190", "1190")));
        when(clienteRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of());
        when(usuarioRepository.findAllById(any())).thenReturn(List.of());

        var fila = service.generar(tenantId, desde, hasta, null).filas().get(0);

        assertEquals("—", fila.clienteNombre());
        assertNull(fila.clienteRut());
        assertEquals("—", fila.usuario());
    }

    @Test
    void unPeriodoSinCotizacionesDevuelveResumenEnCeroSinConsultarClientes() {
        when(cotizacionRepository.findByTenantIdAndFechaEmisionBetweenOrderByFolioAsc(tenantId, desde, hasta))
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
        verifyNoInteractions(cotizacionRepository);
    }
}
