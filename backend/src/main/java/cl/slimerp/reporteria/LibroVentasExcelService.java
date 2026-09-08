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
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

// Vuelca un LibroVentasResponse a un .xlsx: título con el período, subtotales por
// tipo de documento + total general, y el detalle completo de ventas.
@Service
public class LibroVentasExcelService {

    private static final DateTimeFormatter FORMATO_FECHA_CORTA = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter FORMATO_FECHA_HORA = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    private static final String[] ENCABEZADO_SUBTOTALES =
            {"Tipo", "Cantidad", "Neto Afecto", "Neto Exento", "IVA", "Total"};
    private static final String[] ENCABEZADO_DETALLE =
            {"N°", "Fecha", "Tipo documento", "RUT", "Cliente", "Neto Afecto", "Neto Exento", "IVA", "Total"};

    public byte[] generar(LibroVentasService.LibroVentasResponse libro) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet hoja = workbook.createSheet("Libro de Ventas");
            CellStyle negrita = estiloNegrita(workbook);

            int fila = 0;
            fila = escribirTitulo(hoja, fila, libro, negrita);
            fila++; // fila en blanco
            fila = escribirEncabezado(hoja, fila, ENCABEZADO_SUBTOTALES, negrita);
            fila = escribirSubtotales(hoja, fila, libro, negrita);
            fila++; // fila en blanco
            fila = escribirEncabezado(hoja, fila, ENCABEZADO_DETALLE, negrita);
            escribirDetalle(hoja, fila, libro);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el Excel del Libro de Ventas", e);
        }
    }

    private int escribirTitulo(Sheet hoja, int fila, LibroVentasService.LibroVentasResponse libro, CellStyle negrita) {
        Row row = hoja.createRow(fila);
        Cell cell = row.createCell(0);
        cell.setCellValue("Libro de Ventas — " + libro.desde().format(FORMATO_FECHA_CORTA)
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

    private int escribirSubtotales(Sheet hoja, int fila, LibroVentasService.LibroVentasResponse libro, CellStyle negrita) {
        for (LibroVentasService.LibroVentasSubtotal s : libro.subtotales()) {
            escribirFilaSubtotal(hoja, fila++, s, null);
        }
        escribirFilaSubtotal(hoja, fila++, libro.totalGeneral(), negrita);
        return fila;
    }

    private void escribirFilaSubtotal(Sheet hoja, int fila, LibroVentasService.LibroVentasSubtotal s, CellStyle estilo) {
        Row row = hoja.createRow(fila);
        celda(row, 0, s.tipoDocumento(), estilo);
        celda(row, 1, s.cantidad(), estilo);
        celda(row, 2, s.montoNetoAfecto(), estilo);
        celda(row, 3, s.montoNetoExento(), estilo);
        celda(row, 4, s.montoIva(), estilo);
        celda(row, 5, s.montoTotal(), estilo);
    }

    private void escribirDetalle(Sheet hoja, int filaInicial, LibroVentasService.LibroVentasResponse libro) {
        int fila = filaInicial;
        for (LibroVentasService.LibroVentasFila f : libro.filas()) {
            Row row = hoja.createRow(fila++);
            row.createCell(0).setCellValue(f.ventaId());
            row.createCell(1).setCellValue(f.fecha().format(FORMATO_FECHA_HORA));
            row.createCell(2).setCellValue(f.tipoDocumento());
            row.createCell(3).setCellValue(f.clienteRut() != null ? f.clienteRut() : "");
            row.createCell(4).setCellValue(f.clienteNombre());
            row.createCell(5).setCellValue(f.montoNetoAfecto().doubleValue());
            row.createCell(6).setCellValue(f.montoNetoExento().doubleValue());
            row.createCell(7).setCellValue(f.montoIva().doubleValue());
            row.createCell(8).setCellValue(f.montoTotal().doubleValue());
        }
    }

    private void celda(Row row, int col, String valor, CellStyle estilo) {
        Cell cell = row.createCell(col);
        cell.setCellValue(valor);
        if (estilo != null) cell.setCellStyle(estilo);
    }

    private void celda(Row row, int col, int valor, CellStyle estilo) {
        Cell cell = row.createCell(col);
        cell.setCellValue(valor);
        if (estilo != null) cell.setCellStyle(estilo);
    }

    private void celda(Row row, int col, BigDecimal valor, CellStyle estilo) {
        Cell cell = row.createCell(col);
        cell.setCellValue(valor.doubleValue());
        if (estilo != null) cell.setCellStyle(estilo);
    }

    private CellStyle estiloNegrita(Workbook workbook) {
        Font fuente = workbook.createFont();
        fuente.setBold(true);
        CellStyle estilo = workbook.createCellStyle();
        estilo.setFont(fuente);
        return estilo;
    }
}
