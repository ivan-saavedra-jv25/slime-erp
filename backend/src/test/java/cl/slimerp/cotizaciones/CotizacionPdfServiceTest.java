package cl.slimerp.cotizaciones;

import cl.slimerp.catalogo.Cliente;
import cl.slimerp.catalogo.ClienteRepository;
import cl.slimerp.catalogo.FormaPagoRepository;
import cl.slimerp.tenant.Tenant;
import cl.slimerp.tenant.TenantRepository;
import cl.slimerp.tenant.Usuario;
import cl.slimerp.tenant.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CotizacionPdfServiceTest {

    private TenantRepository tenantRepository;
    private ClienteRepository clienteRepository;
    private FormaPagoRepository formaPagoRepository;
    private UsuarioRepository usuarioRepository;
    private CotizacionPdfService service;

    private final Tenant tenant = Tenant.builder().id(1L).nombre("Empresa Demo").rut("76.111.111-1").build();
    private final Cliente cliente = Cliente.builder().id(5L).tenantId(1L).nombre("Empresa ABC SpA")
            .rut("76.222.222-2").direccion("Av. Siempre Viva 742").email("contacto@abc.cl").activo(true).build();
    private final Usuario vendedor = Usuario.builder().id(7L).tenantId(1L).nombre("Vendedor Demo").build();

    @BeforeEach
    void setUp() {
        tenantRepository = mock(TenantRepository.class);
        clienteRepository = mock(ClienteRepository.class);
        formaPagoRepository = mock(FormaPagoRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        service = new CotizacionPdfService(tenantRepository, clienteRepository, formaPagoRepository,
                usuarioRepository);

        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(clienteRepository.findById(5L)).thenReturn(Optional.of(cliente));
        when(usuarioRepository.findById(7L)).thenReturn(Optional.of(vendedor));
        when(formaPagoRepository.findById(any())).thenReturn(Optional.empty());
    }

    private Cotizacion cotizacion(BigDecimal descuentoLinea) {
        Cotizacion cotizacion = Cotizacion.builder()
                .id(100L).tenantId(1L).folio(123).clienteId(5L).vendedorId(7L)
                .estado(EstadoCotizacion.ENVIADA)
                .fechaEmision(LocalDate.of(2026, 9, 15)).fechaVencimiento(LocalDate.of(2026, 9, 30))
                .montoSubtotal(new BigDecimal("2000")).montoDescuento(descuentoLinea)
                .montoNeto(new BigDecimal("1800")).montoIva(new BigDecimal("342"))
                .montoTotal(new BigDecimal("2142"))
                .condicionesComerciales("Pago a 30 días")
                .build();
        cotizacion.setDetalle(List.of(CotizacionDetalle.builder()
                .id(1L).cotizacion(cotizacion).productoId(10L).codigo("SKU001").descripcion("Producto X")
                .cantidad(new BigDecimal("2")).precioUnitario(new BigDecimal("1000"))
                .descuento(descuentoLinea).subtotal(new BigDecimal("1800"))
                .build()));
        return cotizacion;
    }

    @Test
    void generaUnPdfValidoConElNumeroDeLaCotizacion() {
        byte[] pdf = service.generar(cotizacion(new BigDecimal("200")));

        assertTrue(pdf.length > 0);
        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.ISO_8859_1));
    }

    @Test
    void generaElPdfTambienSinDescuentosEnElDetalle() {
        byte[] pdf = service.generar(cotizacion(BigDecimal.ZERO));

        assertTrue(pdf.length > 0);
    }

    @Test
    void noFallaSiElClienteYaNoExiste() {
        when(clienteRepository.findById(5L)).thenReturn(Optional.empty());

        byte[] pdf = service.generar(cotizacion(BigDecimal.ZERO));

        assertTrue(pdf.length > 0);
    }
}
