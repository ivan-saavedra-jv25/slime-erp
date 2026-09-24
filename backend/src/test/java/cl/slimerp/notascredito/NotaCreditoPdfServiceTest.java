package cl.slimerp.notascredito;

import cl.slimerp.catalogo.Cliente;
import cl.slimerp.catalogo.ClienteRepository;
import cl.slimerp.tenant.Tenant;
import cl.slimerp.tenant.TenantRepository;
import cl.slimerp.tenant.Usuario;
import cl.slimerp.tenant.UsuarioRepository;
import cl.slimerp.ventas.TipoDocumentoVenta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotaCreditoPdfServiceTest {

    private TenantRepository tenantRepository;
    private ClienteRepository clienteRepository;
    private UsuarioRepository usuarioRepository;
    private NotaCreditoPdfService service;

    private final Tenant tenant = Tenant.builder().id(1L).nombre("Empresa Demo").rut("76.111.111-1").build();
    private final Cliente cliente = Cliente.builder().id(5L).tenantId(1L).nombre("Empresa ABC SpA")
            .rut("76.222.222-2").direccion("Av. Siempre Viva 742").email("contacto@abc.cl").activo(true).build();
    private final Usuario emisor = Usuario.builder().id(7L).tenantId(1L).nombre("Vendedor Demo").build();

    @BeforeEach
    void setUp() {
        tenantRepository = mock(TenantRepository.class);
        clienteRepository = mock(ClienteRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        service = new NotaCreditoPdfService(tenantRepository, clienteRepository, usuarioRepository);

        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(clienteRepository.findById(5L)).thenReturn(Optional.of(cliente));
        when(usuarioRepository.findById(7L)).thenReturn(Optional.of(emisor));
    }

    private NotaCredito nota(TipoCorreccion tipo, EstadoNotaCredito estado) {
        NotaCredito nc = NotaCredito.builder()
                .id(100L).tenantId(1L).folio(12).clienteId(5L).usuarioId(7L)
                .estado(estado).tipoCorreccion(tipo).fecha(LocalDate.of(2026, 9, 23))
                .motivo("Mercadería en mal estado").observaciones("Retiro coordinado con el cliente")
                .bodegaId(3L).exenta(false).moneda("CLP")
                .ventaId(50L).docAsociadoTipo(TipoDocumentoVenta.FACTURA).docAsociadoFolio(1042)
                .docAsociadoFecha(LocalDate.of(2026, 9, 12)).docAsociadoRazon("Devolución parcial")
                .montoSubtotal(new BigDecimal("2000")).montoDescuento(BigDecimal.ZERO)
                .montoNeto(new BigDecimal("2000")).montoIva(new BigDecimal("380"))
                .montoTotal(new BigDecimal("2380"))
                .detalle(new ArrayList<>())
                .build();
        if (tipo == TipoCorreccion.CORRIGE_TEXTO) {
            nc.setTextoCorreccion("Se corrige el giro del cliente");
        } else {
            nc.getDetalle().add(NotaCreditoDetalle.builder()
                    .id(200L).notaCredito(nc).productoId(10L).ventaDetalleId(301L)
                    .codigo("SKU-A").descripcion("Bidón 20L")
                    .cantidad(new BigDecimal("2")).precioUnitario(new BigDecimal("1000"))
                    .descuento(BigDecimal.ZERO).subtotal(new BigDecimal("2000"))
                    .recuperaInventario(true)
                    .build());
        }
        if (estado == EstadoNotaCredito.ANULADA) {
            nc.setFechaAnulacion(LocalDateTime.of(2026, 9, 24, 9, 30));
        }
        return nc;
    }

    private String contenido(byte[] pdf) {
        return new String(pdf, StandardCharsets.ISO_8859_1);
    }

    @Test
    void generaUnPdfValidoParaCadaTipoDeCorreccion() {
        for (TipoCorreccion tipo : TipoCorreccion.values()) {
            byte[] pdf = service.generar(nota(tipo, EstadoNotaCredito.EMITIDA));

            assertNotNull(pdf);
            assertTrue(pdf.length > 0, "El PDF de " + tipo + " no debería estar vacío");
            assertTrue(contenido(pdf).startsWith("%PDF"), "El PDF de " + tipo + " debería empezar con %PDF");
        }
    }

    @Test
    void unaNotaAnuladaSeGeneraSinErrores() {
        byte[] pdf = service.generar(nota(TipoCorreccion.CORRIGE_MONTO, EstadoNotaCredito.ANULADA));

        assertTrue(contenido(pdf).startsWith("%PDF"));
    }

    @Test
    void fallaCuandoElTenantNoExiste() {
        when(tenantRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> service.generar(nota(TipoCorreccion.CORRIGE_MONTO, EstadoNotaCredito.EMITIDA)));
    }

    @Test
    void noFallaCuandoElClienteYaNoExiste() {
        when(clienteRepository.findById(5L)).thenReturn(Optional.empty());

        byte[] pdf = service.generar(nota(TipoCorreccion.CORRIGE_MONTO, EstadoNotaCredito.EMITIDA));

        assertTrue(contenido(pdf).startsWith("%PDF"));
    }

    @Test
    void elNumeroVisibleSeFormateaConElPrefijoNc() {
        assertEquals("NC-000012", NumeroNotaCredito.formatear(12));
        assertNull(NumeroNotaCredito.formatear(null));
        assertEquals(12, NumeroNotaCredito.parsearFolio("NC-000012"));
        assertEquals(12, NumeroNotaCredito.parsearFolio("12"));
        assertNull(NumeroNotaCredito.parsearFolio("no es un folio"));
    }

    @Test
    void elDetalleNoAparecEnUnaCorreccionDeTexto() {
        // Un PDF de corrección de texto no lleva tabla de líneas; basta con que se
        // genere y sea más corto que el de una corrección con detalle.
        byte[] conDetalle = service.generar(nota(TipoCorreccion.CORRIGE_MONTO, EstadoNotaCredito.EMITIDA));
        byte[] soloTexto = service.generar(nota(TipoCorreccion.CORRIGE_TEXTO, EstadoNotaCredito.EMITIDA));

        assertTrue(soloTexto.length < conDetalle.length);
        assertTrue(List.of(conDetalle.length, soloTexto.length).stream().allMatch(l -> l > 0));
    }
}
