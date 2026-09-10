package cl.slimerp.inventario;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

// Genera el detalle (encabezado + ítems) de un movimiento de inventario en XLSX
// o PDF, para descarga individual desde el historial de movimientos.
@Service
public class MovimientoDetalleExportService {

    private static final String[] ENCABEZADO_ITEMS = {"SKU", "Producto", "Cantidad"};

    public byte[] generarXlsx(MovimientoInventarioController.MovimientoHistorialResponse movimiento) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet hoja = workbook.createSheet("Movimiento " + movimiento.id());

            int filaIndex = 0;
            filaIndex = agregarDato(hoja, filaIndex, "Movimiento", "#" + movimiento.id());
            filaIndex = agregarDato(hoja, filaIndex, "Fecha", movimiento.fecha());
            filaIndex = agregarDato(hoja, filaIndex, "Tipo", movimiento.tipo());
            filaIndex = agregarDato(hoja, filaIndex, "Bodega origen", movimiento.bodegaOrigenNombre());
            filaIndex = agregarDato(hoja, filaIndex, "Bodega destino", movimiento.bodegaDestinoNombre());
            filaIndex = agregarDato(hoja, filaIndex, "Responsable", movimiento.usuarioNombre());
            if (movimiento.observacion() != null && !movimiento.observacion().isBlank()) {
                filaIndex = agregarDato(hoja, filaIndex, "Observación", movimiento.observacion());
            }
            filaIndex++;

            Row encabezado = hoja.createRow(filaIndex++);
            for (int i = 0; i < ENCABEZADO_ITEMS.length; i++) {
                encabezado.createCell(i).setCellValue(ENCABEZADO_ITEMS[i]);
            }

            for (MovimientoInventarioController.ItemDetalle item : movimiento.items()) {
                Row fila = hoja.createRow(filaIndex++);
                fila.createCell(0).setCellValue(item.productoSku() == null ? "" : item.productoSku());
                fila.createCell(1).setCellValue(item.productoNombre());
                fila.createCell(2).setCellValue(item.cantidad().doubleValue());
            }

            for (int i = 0; i < ENCABEZADO_ITEMS.length; i++) {
                hoja.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el Excel del movimiento", e);
        }
    }

    private int agregarDato(Sheet hoja, int filaIndex, String etiqueta, String valor) {
        Row fila = hoja.createRow(filaIndex);
        fila.createCell(0).setCellValue(etiqueta);
        fila.createCell(1).setCellValue(valor);
        return filaIndex + 1;
    }

    public byte[] generarPdf(MovimientoInventarioController.MovimientoHistorialResponse movimiento) {
        try {
            Document document = new Document();
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            PdfWriter.getInstance(document, salida);
            document.open();

            Font fuenteTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font fuenteSubtitulo = FontFactory.getFont(FontFactory.HELVETICA, 11);
            Font fuenteNegrita = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);

            document.add(new Paragraph("Movimiento de Inventario #" + movimiento.id(), fuenteTitulo));
            document.add(new Paragraph("Fecha: " + movimiento.fecha(), fuenteSubtitulo));
            document.add(new Paragraph("Tipo: " + movimiento.tipo(), fuenteSubtitulo));
            document.add(new Paragraph("Bodega origen: " + movimiento.bodegaOrigenNombre(), fuenteSubtitulo));
            document.add(new Paragraph("Bodega destino: " + movimiento.bodegaDestinoNombre(), fuenteSubtitulo));
            document.add(new Paragraph("Responsable: " + movimiento.usuarioNombre(), fuenteSubtitulo));
            if (movimiento.observacion() != null && !movimiento.observacion().isBlank()) {
                document.add(new Paragraph("Observación: " + movimiento.observacion(), fuenteSubtitulo));
            }
            document.add(new Paragraph(" "));

            PdfPTable tabla = new PdfPTable(new float[] {2f, 5f, 1.5f});
            tabla.setWidthPercentage(100);
            for (String encabezado : ENCABEZADO_ITEMS) {
                PdfPCell celda = new PdfPCell(new Paragraph(encabezado, fuenteNegrita));
                celda.setPadding(5);
                tabla.addCell(celda);
            }
            for (MovimientoInventarioController.ItemDetalle item : movimiento.items()) {
                agregarCelda(tabla, item.productoSku() == null ? "—" : item.productoSku());
                agregarCelda(tabla, item.productoNombre());
                agregarCelda(tabla, item.cantidad().stripTrailingZeros().toPlainString(), Element.ALIGN_RIGHT);
            }
            document.add(tabla);

            document.close();
            return salida.toByteArray();
        } catch (com.lowagie.text.DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF del movimiento", e);
        }
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
}
