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

// Vuelca un LibroCotizacionesResponse a un .xlsx, igual que los libros de
// Ventas y Compras: título con el período, resumen y detalle completo.
@Service
public class LibroCotizacionesExcelService {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final String[] ENCABEZADO_RESUMEN = {"Cantidad", "Neto", "IVA", "Total"};
    private static final String[] ENCABEZADO_DETALLE =
            {"Número", "Fecha", "Cliente", "RUT", "Estado", "Neto", "IVA", "Total", "Usuario",
                    "Documentos relacionados"};

    public byte[] generar(LibroCotizacionesService.LibroCotizacionesResponse libro) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet hoja = workbook.createSheet("Libro de Cotizaciones");
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
            throw new IllegalStateException("No se pudo generar el Excel del Libro de Cotizaciones", e);
        }
    }

    private int escribirTitulo(Sheet hoja, int fila, LibroCotizacionesService.LibroCotizacionesResponse libro,
                                CellStyle negrita) {
        Row row = hoja.createRow(fila);
        Cell cell = row.createCell(0);
        String titulo = "Libro de Cotizaciones — " + libro.desde().format(FORMATO_FECHA)
                + " a " + libro.hasta().format(FORMATO_FECHA);
        if (libro.estado() != null) {
            titulo += " (" + libro.estado() + ")";
        }
        cell.setCellValue(titulo);
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

    private int escribirResumen(Sheet hoja, int fila, LibroCotizacionesService.LibroCotizacionesResponse libro) {
        LibroCotizacionesService.LibroCotizacionesResumen r = libro.resumen();
        Row row = hoja.createRow(fila);
        row.createCell(0).setCellValue(r.cantidad());
        row.createCell(1).setCellValue(r.montoNeto().doubleValue());
        row.createCell(2).setCellValue(r.montoIva().doubleValue());
        row.createCell(3).setCellValue(r.montoTotal().doubleValue());
        return fila + 1;
    }

    private void escribirDetalle(Sheet hoja, int filaInicial,
                                  LibroCotizacionesService.LibroCotizacionesResponse libro) {
        int fila = filaInicial;
        for (LibroCotizacionesService.LibroCotizacionesFila f : libro.filas()) {
            Row row = hoja.createRow(fila++);
            row.createCell(0).setCellValue(f.numero());
            row.createCell(1).setCellValue(f.fecha().format(FORMATO_FECHA));
            row.createCell(2).setCellValue(f.clienteNombre());
            row.createCell(3).setCellValue(f.clienteRut() != null ? f.clienteRut() : "");
            row.createCell(4).setCellValue(f.estado().name());
            row.createCell(5).setCellValue(f.montoNeto().doubleValue());
            row.createCell(6).setCellValue(f.montoIva().doubleValue());
            row.createCell(7).setCellValue(f.montoTotal().doubleValue());
            row.createCell(8).setCellValue(f.usuario());
            row.createCell(9).setCellValue(f.documentosRelacionados());
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
