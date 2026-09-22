package cl.slimerp.reporteria;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;

// Vuelca un LibroComprasResponse a un .xlsx: título con el período, resumen
// general (cantidad + Neto/IVA/Total) y el detalle completo de compras.
@Service
public class LibroComprasExcelService {

    private static final DateTimeFormatter FORMATO_FECHA_CORTA = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter FORMATO_FECHA_HORA = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    private static final String[] ENCABEZADO_RESUMEN = {"Cantidad", "Neto", "IVA", "Total"};
    private static final String[] ENCABEZADO_DETALLE =
            {"N°", "Fecha", "N° Documento", "Proveedor", "RUT", "N° Ítems", "Neto", "IVA", "Total", "Estado"};

    public byte[] generar(LibroComprasService.LibroComprasResponse libro) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet hoja = workbook.createSheet("Libro de Compras");
            CellStyle negrita = estiloNegrita(workbook);

            int fila = 0;
            fila = escribirTitulo(hoja, fila, libro, negrita);
            fila++; // fila en blanco
            fila = escribirEncabezado(hoja, fila, ENCABEZADO_RESUMEN, negrita);
            fila = escribirResumen(hoja, fila, libro);
            fila++; // fila en blanco
            fila = escribirEncabezado(hoja, fila, ENCABEZADO_DETALLE, negrita);
            escribirDetalle(hoja, fila, libro);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar el Excel del Libro de Compras", e);
        }
    }

    private int escribirTitulo(Sheet hoja, int fila, LibroComprasService.LibroComprasResponse libro, CellStyle negrita) {
        Row row = hoja.createRow(fila);
        Cell cell = row.createCell(0);
        cell.setCellValue("Libro de Compras — " + libro.desde().format(FORMATO_FECHA_CORTA)
                + " a " + libro.hasta().format(FORMATO_FECHA_CORTA));
        cell.setCellStyle(negrita);
        return fila + 1;
    }

    private int escribirEncabezado(Sheet hoja, int fila, String[] columnas, CellStyle negrita) {
        Row row = hoja.createRow(fila);
        for (int i = 0; i < columnas.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(columnas[i]);
            cell.setCellStyle(negrita);
        }
        return fila + 1;
    }

    private int escribirResumen(Sheet hoja, int fila, LibroComprasService.LibroComprasResponse libro) {
        LibroComprasService.LibroComprasResumen r = libro.resumen();
        Row row = hoja.createRow(fila);
        row.createCell(0).setCellValue(r.cantidadCompras());
        row.createCell(1).setCellValue(r.montoNeto().doubleValue());
        row.createCell(2).setCellValue(r.montoIva().doubleValue());
        row.createCell(3).setCellValue(r.montoTotal().doubleValue());
        return fila + 1;
    }

    private void escribirDetalle(Sheet hoja, int filaInicial, LibroComprasService.LibroComprasResponse libro) {
        int fila = filaInicial;
        for (LibroComprasService.LibroComprasFila f : libro.filas()) {
            Row row = hoja.createRow(fila++);
            row.createCell(0).setCellValue(f.compraId());
            row.createCell(1).setCellValue(f.fecha().format(FORMATO_FECHA_HORA));
            row.createCell(2).setCellValue(f.numeroDocumento() != null ? f.numeroDocumento() : "");
            row.createCell(3).setCellValue(f.proveedorNombre());
            row.createCell(4).setCellValue(f.proveedorRut() != null ? f.proveedorRut() : "");
            row.createCell(5).setCellValue(f.cantidadItems());
            row.createCell(6).setCellValue(f.montoNeto().doubleValue());
            row.createCell(7).setCellValue(f.montoIva().doubleValue());
            row.createCell(8).setCellValue(f.montoTotal().doubleValue());
            row.createCell(9).setCellValue(f.estadoPago());
        }
    }

    private CellStyle estiloNegrita(Workbook workbook) {
        Font fuente = workbook.createFont();
        fuente.setBold(true);
        CellStyle estilo = workbook.createCellStyle();
        estilo.setFont(fuente);
        return estilo;
    }
}
