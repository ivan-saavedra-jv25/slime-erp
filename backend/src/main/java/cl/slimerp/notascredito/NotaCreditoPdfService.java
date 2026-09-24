package cl.slimerp.notascredito;

import cl.slimerp.catalogo.Cliente;
import cl.slimerp.catalogo.ClienteRepository;
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

// Misma anatomía que NotaVentaPdfService (OpenPDF), adaptada al documento de
// corrección: además de cliente y detalle, lleva un bloque propio con el
// documento asociado y la razón de la asociación, que es lo que distingue a una
// nota de crédito de cualquier otro documento del ERP.
@Service
public class NotaCreditoPdfService {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final NumberFormat FORMATO_MONTO = NumberFormat.getIntegerInstance(new Locale("es", "CL"));

    private final TenantRepository tenantRepository;
    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;

    public NotaCreditoPdfService(TenantRepository tenantRepository, ClienteRepository clienteRepository,
                                 UsuarioRepository usuarioRepository) {
        this.tenantRepository = tenantRepository;
        this.clienteRepository = clienteRepository;
        this.usuarioRepository = usuarioRepository;
    }

    public byte[] generar(NotaCredito nota) {
        Tenant tenant = tenantRepository.findById(nota.getTenantId())
                .orElseThrow(() -> new IllegalStateException("Tenant no encontrado: " + nota.getTenantId()));
        Cliente cliente = clienteRepository.findById(nota.getClienteId()).orElse(null);
        Usuario emisor = usuarioRepository.findById(nota.getUsuarioId()).orElse(null);

        try {
            Document document = new Document();
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            PdfWriter.getInstance(document, salida);
            document.open();

            Font fuenteTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font fuenteSubtitulo = FontFactory.getFont(FontFactory.HELVETICA, 11);
            Font fuenteNegrita = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
            Font fuenteAnulada = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);

            document.add(new Paragraph("NOTA DE CRÉDITO " + NumeroNotaCredito.formatear(nota.getFolio()),
                    fuenteTitulo));
            // Una nota anulada no debe poder confundirse con un documento vigente.
            if (nota.getEstado() == EstadoNotaCredito.ANULADA) {
                document.add(new Paragraph("ANULADA", fuenteAnulada));
            }
            document.add(new Paragraph(tenant.getNombre() + " - RUT " + tenant.getRut(), fuenteSubtitulo));
            document.add(new Paragraph("Fecha: " + nota.getFecha().format(FORMATO_FECHA), fuenteSubtitulo));
            document.add(new Paragraph("Estado: " + nota.getEstado(), fuenteSubtitulo));
            document.add(new Paragraph("Tipo de corrección: " + etiquetaTipo(nota.getTipoCorreccion()),
                    fuenteSubtitulo));
            document.add(new Paragraph(" "));

            document.add(new Paragraph("Documento asociado: " + descripcionDocumento(nota), fuenteNegrita));
            if (nota.getDocAsociadoRazon() != null && !nota.getDocAsociadoRazon().isBlank()) {
                document.add(new Paragraph("Razón: " + nota.getDocAsociadoRazon(), fuenteSubtitulo));
            }
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
            document.add(new Paragraph("Emitida por: " + (emisor != null ? emisor.getNombre() : "—"),
                    fuenteSubtitulo));
            document.add(new Paragraph(" "));

            if (nota.getTipoCorreccion() == TipoCorreccion.CORRIGE_TEXTO) {
                document.add(new Paragraph("Texto de la corrección", fuenteNegrita));
                document.add(new Paragraph(
                        nota.getTextoCorreccion() != null ? nota.getTextoCorreccion() : "—", fuenteSubtitulo));
                document.add(new Paragraph(" "));
            } else {
                document.add(tablaDetalle(nota, fuenteNegrita));
                document.add(new Paragraph(" "));

                document.add(totalAlineado("Subtotal:", formatoMonto(nota.getMontoSubtotal()), fuenteSubtitulo));
                if (nota.getMontoDescuento().signum() > 0) {
                    document.add(totalAlineado("Descuentos:", formatoMonto(nota.getMontoDescuento()),
                            fuenteSubtitulo));
                }
                document.add(totalAlineado("Neto:", formatoMonto(nota.getMontoNeto()), fuenteSubtitulo));
                if (!nota.isExenta()) {
                    document.add(totalAlineado("IVA:", formatoMonto(nota.getMontoIva()), fuenteSubtitulo));
                }
                document.add(totalAlineado("Total:", formatoMonto(nota.getMontoTotal()), fuenteNegrita));
                document.add(new Paragraph(" "));
            }

            if (nota.getMotivo() != null && !nota.getMotivo().isBlank()) {
                document.add(new Paragraph("Motivo: " + nota.getMotivo(), fuenteSubtitulo));
            }
            if (nota.getObservaciones() != null && !nota.getObservaciones().isBlank()) {
                document.add(new Paragraph("Observaciones: " + nota.getObservaciones(), fuenteSubtitulo));
            }
            if (nota.getFechaAnulacion() != null) {
                document.add(new Paragraph(
                        "Anulada el " + nota.getFechaAnulacion().toLocalDate().format(FORMATO_FECHA),
                        fuenteSubtitulo));
            }

            document.close();
            return salida.toByteArray();
        } catch (com.lowagie.text.DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF de la nota de crédito", e);
        }
    }

    private PdfPTable tablaDetalle(NotaCredito nota, Font fuenteNegrita) {
        boolean tieneDescuentoDetalle = nota.getDetalle().stream()
                .anyMatch(d -> d.getDescuento() != null && d.getDescuento().signum() > 0);

        float[] anchos = tieneDescuentoDetalle
                ? new float[] {2f, 4f, 1.5f, 2f, 1.5f, 2f, 1.5f}
                : new float[] {2f, 5f, 1.5f, 2f, 2f, 1.5f};
        String[] encabezados = tieneDescuentoDetalle
                ? new String[] {"Código", "Descripción", "Cantidad", "Precio unit.", "Descuento", "Subtotal",
                        "Recupera"}
                : new String[] {"Código", "Descripción", "Cantidad", "Precio unit.", "Subtotal", "Recupera"};

        PdfPTable tabla = new PdfPTable(anchos);
        tabla.setWidthPercentage(100);
        for (String encabezado : encabezados) {
            PdfPCell celda = new PdfPCell(new Paragraph(encabezado, fuenteNegrita));
            celda.setPadding(5);
            tabla.addCell(celda);
        }
        for (NotaCreditoDetalle linea : nota.getDetalle()) {
            agregarCelda(tabla, linea.getCodigo() != null ? linea.getCodigo() : "—");
            agregarCelda(tabla, linea.getDescripcion());
            agregarCelda(tabla, linea.getCantidad().stripTrailingZeros().toPlainString(), Element.ALIGN_RIGHT);
            agregarCelda(tabla, formatoMonto(linea.getPrecioUnitario()), Element.ALIGN_RIGHT);
            if (tieneDescuentoDetalle) {
                agregarCelda(tabla, formatoMonto(linea.getDescuento()), Element.ALIGN_RIGHT);
            }
            agregarCelda(tabla, formatoMonto(linea.getSubtotal()), Element.ALIGN_RIGHT);
            agregarCelda(tabla, linea.isRecuperaInventario() ? "Sí" : "No", Element.ALIGN_CENTER);
        }
        return tabla;
    }

    private String descripcionDocumento(NotaCredito nota) {
        String base = nota.getDocAsociadoTipo() + " N.º " + nota.getDocAsociadoFolio();
        return nota.getDocAsociadoFecha() == null
                ? base
                : base + " del " + nota.getDocAsociadoFecha().format(FORMATO_FECHA);
    }

    private String etiquetaTipo(TipoCorreccion tipo) {
        return switch (tipo) {
            case CORRIGE_DOCUMENTO -> "Corrige documento";
            case CORRIGE_MONTO -> "Corrige monto";
            case CORRIGE_TEXTO -> "Corrige texto";
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
