package cl.slimerp.ventas;

import cl.slimerp.catalogo.CategoriaFormaPago;
import cl.slimerp.catalogo.Cliente;
import cl.slimerp.catalogo.ClienteRepository;
import cl.slimerp.catalogo.FormaPago;
import cl.slimerp.catalogo.FormaPagoRepository;
import cl.slimerp.catalogo.Producto;
import cl.slimerp.catalogo.ProductoRepository;
import cl.slimerp.tenant.Tenant;
import cl.slimerp.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VentaPdfServiceTest {

    private TenantRepository tenantRepository;
    private ClienteRepository clienteRepository;
    private ProductoRepository productoRepository;
    private FormaPagoRepository formaPagoRepository;
    private VentaPdfService service;

    private final Long tenantId = 1L;
    private final Tenant tenant = Tenant.builder().id(1L).nombre("Empresa Demo").rut("76.111.111-1").build();
    private final Cliente cliente = Cliente.builder().id(1L).tenantId(1L).nombre("Cliente Uno").rut("11.111.111-1")
            .activo(true).build();
    private final FormaPago formaPago = FormaPago.builder().id(1L).tenantId(1L).nombre("Efectivo")
            .categoria(CategoriaFormaPago.CONTADO).activo(true).build();
    private final Producto producto = Producto.builder().id(10L).tenantId(1L).nombre("Producto X").sku("SKU-X")
            .precioVenta(new BigDecimal("1000")).activo(true).build();

    @BeforeEach
    void setUp() {
        tenantRepository = mock(TenantRepository.class);
        clienteRepository = mock(ClienteRepository.class);
        productoRepository = mock(ProductoRepository.class);
        formaPagoRepository = mock(FormaPagoRepository.class);
        service = new VentaPdfService(tenantRepository, clienteRepository, productoRepository, formaPagoRepository);

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(clienteRepository.findByIdAndTenantIdAndActivoTrue(1L, tenantId)).thenReturn(Optional.of(cliente));
        when(formaPagoRepository.findByIdAndTenantIdAndActivoTrue(1L, tenantId)).thenReturn(Optional.of(formaPago));
        when(productoRepository.findByIdAndTenantIdAndActivoTrue(10L, tenantId)).thenReturn(Optional.of(producto));
    }

    private Venta ventaDeEjemplo() {
        Venta venta = Venta.builder()
                .id(99L)
                .tenantId(tenantId)
                .clienteId(1L)
                .formaPagoId(1L)
                .bodegaId(1L)
                .tipoDocumento(TipoDocumentoVenta.BOLETA)
                .folio(12)
                .codigoSii(39)
                .montoNeto(new BigDecimal("1000"))
                .montoIva(new BigDecimal("190"))
                .montoTotal(new BigDecimal("1190"))
                .build();
        VentaDetalle detalle = VentaDetalle.builder()
                .venta(venta)
                .productoId(10L)
                .cantidad(new BigDecimal("1"))
                .precioUnitario(new BigDecimal("1190"))
                .subtotal(new BigDecimal("1190"))
                .build();
        venta.setDetalle(List.of(detalle));
        return venta;
    }

    @Test
    void generaUnPdfNoVacioConCabeceraPdf() {
        byte[] pdf = service.generar(ventaDeEjemplo());

        assertTrue(pdf.length > 0);
        assertEquals('%', (char) pdf[0]);
        assertEquals('P', (char) pdf[1]);
        assertEquals('D', (char) pdf[2]);
        assertEquals('F', (char) pdf[3]);
    }

    @Test
    void funcionaAunSiElClienteOLaFormaDePagoYaNoExisten() {
        when(clienteRepository.findByIdAndTenantIdAndActivoTrue(1L, tenantId)).thenReturn(Optional.empty());
        when(formaPagoRepository.findByIdAndTenantIdAndActivoTrue(1L, tenantId)).thenReturn(Optional.empty());

        byte[] pdf = service.generar(ventaDeEjemplo());

        assertTrue(pdf.length > 0);
    }
}
