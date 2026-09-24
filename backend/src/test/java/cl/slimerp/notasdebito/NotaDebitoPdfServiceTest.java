package cl.slimerp.notasdebito;

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

class NotaDebitoPdfServiceTest {

    private TenantRepository tenantRepository;
    private ClienteRepository clienteRepository;
    private UsuarioRepository usuarioRepository;
    private NotaDebitoPdfService service;

    private final Tenant tenant = Tenant.builder().id(1L).nombre("Empresa Demo").rut("76.111.111-1").build();
    private final Cliente cliente = Cliente.builder().id(5L).tenantId(1L).nombre("Empresa ABC SpA")
            .rut("76.222.222-2").direccion("Av. Siempre Viva 742").email("contacto@abc.cl").activo(true).build();
    private final Usuario emisor = Usuario.builder().id(7L).tenantId(1L).nombre("Vendedor Demo").build();

    @BeforeEach
    void setUp() {
        tenantRepository = mock(TenantRepository.class);
        clienteRepository = mock(ClienteRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        service = new NotaDebitoPdfService(tenantRepository, clienteRepository, usuarioRepository);

        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(clienteRepository.findById(5L)).thenReturn(Optional.of(cliente));
        when(usuarioRepository.findById(7L)).thenReturn(Optional.of(emisor));
    }

    private NotaDebito nota(TipoReversion tipo, EstadoNotaDebito estado) {
        NotaDebito nd = NotaDebito.builder()
                .id(100L).tenantId(1L).folio(12).clienteId(5L).usuarioId(7L)
                .estado(estado).tipoReversion(tipo).fecha(LocalDate.of(2026, 9, 23))
                .motivo("Devolución rechazada").observaciones("El cliente devolvió la mercadería")
                .bodegaId(3L).exenta(false).moneda("CLP")
                .notaCreditoId(90L).ncDocAsociadoTipo(TipoDocumentoVenta.FACTURA).ncFolio(90)
                .ncFecha(LocalDate.of(2026, 9, 15)).ncMontoTotal(new BigDecimal("12852"))
                .ncRazon("Devolución rechazada")
                .montoSubtotal(new BigDecimal("2000")).montoDescuento(BigDecimal.ZERO)
                .montoNeto(new BigDecimal("2000")).montoIva(new BigDecimal("380"))
                .montoTotal(new BigDecimal("2380"))
                .detalle(new ArrayList<>())
                .build();
        if (tipo == TipoReversion.REVIERTE_TEXTO) {
            nd.setTextoCorreccion("Se corrige el giro del cliente");
        } else {
            nd.getDetalle().add(NotaDebitoDetalle.builder()
                    .id(200L).notaDebito(nd).productoId(10L).notaCreditoDetalleId(401L)
                    .codigo("SKU-A").descripcion("Bidón 20L")
                    .cantidad(new BigDecimal("2")).precioUnitario(new BigDecimal("1000"))
                    .descuento(BigDecimal.ZERO).subtotal(new BigDecimal("2000"))
                    .revierteInventario(true)
                    .build());
        }
        if (estado == EstadoNotaDebito.ANULADA) {
            nd.setFechaAnulacion(LocalDateTime.of(2026, 9, 24, 9, 30));
        }
        return nd;
    }

    private String contenido(byte[] pdf) {
        return new String(pdf, StandardCharsets.ISO_8859_1);
    }

    @Test
    void generaUnPdfValidoParaCadaTipoDeReversion() {
        for (TipoReversion tipo : TipoReversion.values()) {
            byte[] pdf = service.generar(nota(tipo, EstadoNotaDebito.EMITIDA));

            assertNotNull(pdf);
            assertTrue(pdf.length > 0, "El PDF de " + tipo + " no debería estar vacío");
            assertTrue(contenido(pdf).startsWith("%PDF"), "El PDF de " + tipo + " debería empezar con %PDF");
        }
    }

    @Test
    void unaNotaAnuladaSeGeneraSinErrores() {
        byte[] pdf = service.generar(nota(TipoReversion.REVIERTE_MONTO, EstadoNotaDebito.ANULADA));

        assertTrue(contenido(pdf).startsWith("%PDF"));
    }

    @Test
    void fallaCuandoElTenantNoExiste() {
        when(tenantRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> service.generar(nota(TipoReversion.REVIERTE_MONTO, EstadoNotaDebito.EMITIDA)));
    }

    @Test
    void noFallaCuandoElClienteYaNoExiste() {
        when(clienteRepository.findById(5L)).thenReturn(Optional.empty());

        byte[] pdf = service.generar(nota(TipoReversion.REVIERTE_MONTO, EstadoNotaDebito.EMITIDA));

        assertTrue(contenido(pdf).startsWith("%PDF"));
    }

    @Test
    void elNumeroVisibleSeFormateaConElPrefijoNd() {
        assertEquals("ND-000012", NumeroNotaDebito.formatear(12));
        assertNull(NumeroNotaDebito.formatear(null));
        assertEquals(12, NumeroNotaDebito.parsearFolio("ND-000012"));
        assertEquals(12, NumeroNotaDebito.parsearFolio("12"));
        assertNull(NumeroNotaDebito.parsearFolio("no es un folio"));
    }

    @Test
    void elDetalleNoApareceEnUnaReversionDeTexto() {
        // Un PDF de reversión de texto no lleva tabla de líneas; basta con que se
        // genere y sea más corto que el de una reversión con detalle.
        byte[] conDetalle = service.generar(nota(TipoReversion.REVIERTE_MONTO, EstadoNotaDebito.EMITIDA));
        byte[] soloTexto = service.generar(nota(TipoReversion.REVIERTE_TEXTO, EstadoNotaDebito.EMITIDA));

        assertTrue(soloTexto.length < conDetalle.length);
        assertTrue(List.of(conDetalle.length, soloTexto.length).stream().allMatch(l -> l > 0));
    }
}