package cl.slimerp.notasventa;

import cl.slimerp.catalogo.Cliente;
import cl.slimerp.catalogo.ClienteRepository;
import cl.slimerp.catalogo.FormaPago;
import cl.slimerp.catalogo.FormaPagoRepository;
import cl.slimerp.cotizaciones.Cotizacion;
import cl.slimerp.cotizaciones.CotizacionRepository;
import cl.slimerp.cotizaciones.NumeroCotizacion;
import cl.slimerp.tenant.Tenant;
import cl.slimerp.tenant.TenantRepository;
import cl.slimerp.tenant.Usuario;
import cl.slimerp.tenant.UsuarioRepository;
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
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

// Misma anatomía que CotizacionPdfService (OpenPDF), adaptada al documento
// comercial: número NV-…, origen (cotización o venta directa), estado de
// entrega y el bloque de totales de cinco líneas que pide la especificación.
@Service
public class NotaVentaPdfService {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final NumberFormat FORMATO_MONTO = NumberFormat.getIntegerInstance(new Locale("es", "CL"));

    private final TenantRepository tenantRepository;
    private final ClienteRepository clienteRepository;
    private final FormaPagoRepository formaPagoRepository;
    private final UsuarioRepository usuarioRepository;
    private final CotizacionRepository cotizacionRepository;

    public NotaVentaPdfService(TenantRepository tenantRepository, ClienteRepository clienteRepository,
                               FormaPagoRepository formaPagoRepository, UsuarioRepository usuarioRepository,
                               CotizacionRepository cotizacionRepository) {
        this.tenantRepository = tenantRepository;
        this.clienteRepository = clienteRepository;
        this.formaPagoRepository = formaPagoRepository;
        this.usuarioRepository = usuarioRepository;
        this.cotizacionRepository = cotizacionRepository;
    }

    public byte[] generar(NotaVenta notaVenta) {
        Tenant tenant = tenantRepository.findById(notaVenta.getTenantId())
                .orElseThrow(() -> new IllegalStateException("Tenant no encontrado: " + notaVenta.getTenantId()));
        Cliente cliente = clienteRepository.findById(notaVenta.getClienteId()).orElse(null);
        Usuario vendedor = usuarioRepository.findById(notaVenta.getVendedorId()).orElse(null);
        FormaPago formaPago = notaVenta.getFormaPagoId() == null ? null
                : formaPagoRepository.findById(notaVenta.getFormaPagoId()).orElse(null);
        Cotizacion cotizacion = notaVenta.getCotizacionId() == null ? null
                : cotizacionRepository.findById(notaVenta.getCotizacionId()).orElse(null);

        try {
            Document document = new Document();
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            PdfWriter.getInstance(document, salida);
            document.open();

            Font fuenteTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font fuenteSubtitulo = FontFactory.getFont(FontFactory.HELVETICA, 11);
            Font fuenteNegrita = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);

            document.add(new Paragraph("NOTA DE VENTA " + NumeroNotaVenta.formatear(notaVenta.getFolio()),
                    fuenteTitulo));
            document.add(new Paragraph(tenant.getNombre() + " - RUT " + tenant.getRut(), fuenteSubtitulo));
            document.add(new Paragraph("Fecha: " + notaVenta.getFechaEmision().format(FORMATO_FECHA),
                    fuenteSubtitulo));
            document.add(new Paragraph("Estado: " + notaVenta.getEstado(), fuenteSubtitulo));
            document.add(new Paragraph("Origen: " + origenDe(notaVenta, cotizacion), fuenteSubtitulo));
            document.add(new Paragraph(" "));

            document.add(new Paragraph("Cliente: " + (cliente != null ? cliente.getNombre() : "—"), fuenteNegrita));
            if (cliente != null && cliente.getRut() != null && !cliente.getRut().isBlank()) {
                document.add(new Paragraph("RUT: " + cliente.getRut(), fuenteSubtitulo));
            }
            if (cliente != null && cliente.getDireccion() != null && !cliente.getDireccion().isBlank()) {
                document.add(new Paragraph("Dirección: " + cliente.getDireccion(), fuenteSubtitulo));
            }
            if (cliente != null && cliente.getEmail() != null && !cliente.getEmail().isBlank()) {
                document.add(new Paragraph("Correo: " + cliente.getEmail(), fuenteSubtitulo));
            }
            document.add(new Paragraph("Vendedor: " + (vendedor != null ? vendedor.getNombre() : "—"),
                    fuenteSubtitulo));
            document.add(new Paragraph(" "));

            boolean tieneDescuentoDetalle = notaVenta.getDetalle().stream()
                    .anyMatch(d -> d.getDescuento() != null && d.getDescuento().signum() > 0);

            float[] anchos = tieneDescuentoDetalle
                    ? new float[] {2f, 4f, 1.5f, 2f, 1.5f, 2f}
                    : new float[] {2f, 5f, 1.5f, 2f, 2f};
            String[] encabezados = tieneDescuentoDetalle
                    ? new String[] {"Código", "Descripción", "Cantidad", "Precio unit.", "Descuento", "Subtotal"}
                    : new String[] {"Código", "Descripción", "Cantidad", "Precio unit.", "Subtotal"};

            PdfPTable tabla = new PdfPTable(anchos);
            tabla.setWidthPercentage(100);
            for (String encabezado : encabezados) {
                PdfPCell celda = new PdfPCell(new Paragraph(encabezado, fuenteNegrita));
                celda.setPadding(5);
                tabla.addCell(celda);
            }
            for (NotaVentaDetalle linea : notaVenta.getDetalle()) {
                agregarCelda(tabla, linea.getCodigo() != null ? linea.getCodigo() : "—");
                agregarCelda(tabla, linea.getDescripcion());
                agregarCelda(tabla, linea.getCantidad().stripTrailingZeros().toPlainString(), Element.ALIGN_RIGHT);
                agregarCelda(tabla, formatoMonto(linea.getPrecioUnitario()), Element.ALIGN_RIGHT);
                if (tieneDescuentoDetalle) {
                    agregarCelda(tabla, formatoMonto(linea.getDescuento()), Element.ALIGN_RIGHT);
                }
                agregarCelda(tabla, formatoMonto(linea.getSubtotal()), Element.ALIGN_RIGHT);
            }
            document.add(tabla);
            document.add(new Paragraph(" "));

            document.add(totalAlineado("Subtotal:", formatoMonto(notaVenta.getMontoSubtotal()), fuenteSubtitulo));
            if (notaVenta.getMontoDescuento().signum() > 0) {
                document.add(totalAlineado("Descuento:", formatoMonto(notaVenta.getMontoDescuento()),
                        fuenteSubtitulo));
            }
            document.add(totalAlineado("Neto:", formatoMonto(notaVenta.getMontoNeto()), fuenteSubtitulo));
            if (!notaVenta.isExenta()) {
                document.add(totalAlineado("IVA:", formatoMonto(notaVenta.getMontoIva()), fuenteSubtitulo));
            }
            document.add(totalAlineado("Total:", formatoMonto(notaVenta.getMontoTotal()), fuenteNegrita));
            document.add(new Paragraph(" "));

            if (formaPago != null) {
                document.add(new Paragraph("Forma de pago: " + formaPago.getNombre(), fuenteSubtitulo));
            }
            if (notaVenta.getFechaEntregaEstimada() != null) {
                document.add(new Paragraph(
                        "Entrega estimada: " + notaVenta.getFechaEntregaEstimada().format(FORMATO_FECHA),
                        fuenteSubtitulo));
            }
            if (notaVenta.getDireccionEntrega() != null && !notaVenta.getDireccionEntrega().isBlank()) {
                document.add(new Paragraph("Dirección de entrega: " + notaVenta.getDireccionEntrega(),
                        fuenteSubtitulo));
            }
            if (notaVenta.getCondicionesVenta() != null && !notaVenta.getCondicionesVenta().isBlank()) {
                document.add(new Paragraph("Condiciones de venta: " + notaVenta.getCondicionesVenta(),
                        fuenteSubtitulo));
            }
            if (notaVenta.getObservaciones() != null && !notaVenta.getObservaciones().isBlank()) {
                document.add(new Paragraph("Observaciones: " + notaVenta.getObservaciones(), fuenteSubtitulo));
            }
            if (notaVenta.getMotivo() != null && !notaVenta.getMotivo().isBlank()) {
                document.add(new Paragraph("Motivo de cancelación: " + notaVenta.getMotivo(), fuenteSubtitulo));
            }

            document.close();
            return salida.toByteArray();
        } catch (com.lowagie.text.DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF de la nota de venta", e);
        }
    }

    private String origenDe(NotaVenta notaVenta, Cotizacion cotizacion) {
        if (notaVenta.getOrigen() == OrigenNotaVenta.COTIZACION) {
            return "Cotización " + (cotizacion != null
                    ? NumeroCotizacion.formatear(cotizacion.getFolio())
                    : "#" + notaVenta.getCotizacionId());
        }
        return "Venta directa";
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