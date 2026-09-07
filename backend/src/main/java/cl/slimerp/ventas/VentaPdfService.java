package cl.slimerp.ventas;

import cl.slimerp.catalogo.Cliente;
import cl.slimerp.catalogo.ClienteRepository;
import cl.slimerp.catalogo.FormaPago;
import cl.slimerp.catalogo.FormaPagoRepository;
import cl.slimerp.catalogo.Producto;
import cl.slimerp.catalogo.ProductoRepository;
import cl.slimerp.tenant.Tenant;
import cl.slimerp.tenant.TenantRepository;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.text.NumberFormat;
import java.util.Locale;

@Service
public class VentaPdfService {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    private static final NumberFormat FORMATO_MONTO = NumberFormat.getIntegerInstance(new Locale("es", "CL"));

    private final TenantRepository tenantRepository;
    private final ClienteRepository clienteRepository;
    private final ProductoRepository productoRepository;
    private final FormaPagoRepository formaPagoRepository;

    public VentaPdfService(TenantRepository tenantRepository, ClienteRepository clienteRepository,
                            ProductoRepository productoRepository, FormaPagoRepository formaPagoRepository) {
        this.tenantRepository = tenantRepository;
        this.clienteRepository = clienteRepository;
        this.productoRepository = productoRepository;
        this.formaPagoRepository = formaPagoRepository;
    }

    public byte[] generar(Venta venta) {
        Tenant tenant = tenantRepository.findById(venta.getTenantId())
                .orElseThrow(() -> new IllegalStateException("Tenant no encontrado: " + venta.getTenantId()));
        Cliente cliente = clienteRepository.findByIdAndTenantIdAndActivoTrue(venta.getClienteId(), venta.getTenantId())
                .orElse(null);
        FormaPago formaPago = formaPagoRepository
                .findByIdAndTenantIdAndActivoTrue(venta.getFormaPagoId(), venta.getTenantId())
                .orElse(null);

        try {
            Document document = new Document();
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            PdfWriter.getInstance(document, salida);
            document.open();

            Font fuenteTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font fuenteSubtitulo = FontFactory.getFont(FontFactory.HELVETICA, 11);
            Font fuenteNegrita = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);

            document.add(new Paragraph(etiquetaDocumento(venta) + " N° " + venta.getId(), fuenteTitulo));
            document.add(new Paragraph(tenant.getNombre() + " - RUT " + tenant.getRut(), fuenteSubtitulo));
            document.add(new Paragraph("Fecha: " + venta.getFecha().format(FORMATO_FECHA), fuenteSubtitulo));
            document.add(new Paragraph(" "));

            document.add(new Paragraph("Cliente: " + (cliente != null ? cliente.getNombre() : "—"), fuenteNegrita));
            if (cliente != null && cliente.getRut() != null && !cliente.getRut().isBlank()) {
                document.add(new Paragraph("RUT: " + cliente.getRut(), fuenteSubtitulo));
            }
            document.add(new Paragraph(" "));

            PdfPTable tabla = new PdfPTable(new float[] {2f, 5f, 1.5f, 2f, 2f});
            tabla.setWidthPercentage(100);
            for (String encabezado : new String[] {"SKU", "Producto", "Cantidad", "Precio unit.", "Subtotal"}) {
                PdfPCell celda = new PdfPCell(new Paragraph(encabezado, fuenteNegrita));
                celda.setPadding(5);
                tabla.addCell(celda);
            }
            for (VentaDetalle item : venta.getDetalle()) {
                Producto producto = productoRepository
                        .findByIdAndTenantIdAndActivoTrue(item.getProductoId(), venta.getTenantId())
                        .orElse(null);
                agregarCelda(tabla, producto != null && producto.getSku() != null ? producto.getSku() : "—");
                agregarCelda(tabla, producto != null ? producto.getNombre() : "Producto #" + item.getProductoId());
                agregarCelda(tabla, item.getCantidad().stripTrailingZeros().toPlainString(), Element.ALIGN_RIGHT);
                agregarCelda(tabla, formatoMonto(item.getPrecioUnitario()), Element.ALIGN_RIGHT);
                agregarCelda(tabla, formatoMonto(item.getSubtotal()), Element.ALIGN_RIGHT);
            }
            document.add(tabla);
            document.add(new Paragraph(" "));

            if (venta.getDescuento().signum() > 0) {
                document.add(totalAlineado("Descuento:", formatoMonto(venta.getDescuento()), fuenteSubtitulo));
            }
            document.add(totalAlineado("Neto:", formatoMonto(venta.getMontoNeto()), fuenteSubtitulo));
            document.add(totalAlineado("IVA:", formatoMonto(venta.getMontoIva()), fuenteSubtitulo));
            document.add(totalAlineado("Total:", formatoMonto(venta.getMontoTotal()), fuenteNegrita));
            document.add(new Paragraph(" "));

            document.add(new Paragraph("Forma de pago: " + (formaPago != null ? formaPago.getNombre() : "—"),
                    fuenteSubtitulo));
            if (venta.getObservacion() != null && !venta.getObservacion().isBlank()) {
                document.add(new Paragraph("Observación: " + venta.getObservacion(), fuenteSubtitulo));
            }

            document.close();
            return salida.toByteArray();
        } catch (com.lowagie.text.DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF de la venta", e);
        }
    }

    private String etiquetaDocumento(Venta venta) {
        return switch (venta.getTipoDocumento()) {
            case BOLETA -> "Boleta";
            case FACTURA -> "Factura";
            case VOUCHER -> "Voucher";
        };
    }

    private void agregarCelda(PdfPTable tabla, String texto) {
        agregarCelda(tabla, texto, Element.ALIGN_LEFT);
    }

    private void agregarCelda(PdfPTable tabla, String texto, int alineacion) {
        PdfPCell celda = new PdfPCell(new Paragraph(texto));
        celda.setPadding(5);
        celda.setHorizontalAlignment(alineacion);
        tabla.addCell(celda);
    }

    private Paragraph totalAlineado(String etiqueta, String valor, Font fuente) {
        Paragraph parrafo = new Paragraph(etiqueta + " " + valor, fuente);
        parrafo.setAlignment(Element.ALIGN_RIGHT);
        return parrafo;
    }

    private String formatoMonto(BigDecimal monto) {
        return "$" + FORMATO_MONTO.format(monto);
    }
}
