package cl.slimerp.inventario;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

// Exporta el resultado de InventarioConsultaService a CSV o XLSX, siempre
// con las columnas Producto/Código/Código Barra/Stock, en el mismo orden
// que llegue la lista (el llamador ya la ordena antes de exportar).
@Service
public class InventarioExportService {

    private static final String[] ENCABEZADO = {"Producto", "Código", "Código Barra", "Stock"};

    public byte[] generarCsv(List<InventarioConsultaItem> items) {
        StringBuilder sb = new StringBuilder(String.join(",", ENCABEZADO)).append('\n');
        for (InventarioConsultaItem item : items) {
            sb.append(csvEscape(item.nombre())).append(',')
                    .append(csvEscape(item.sku())).append(',')
                    .append(csvEscape(item.codigoBarra())).append(',')
                    .append(item.stock().stripTrailingZeros().toPlainString())
                    .append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String csvEscape(String valor) {
        if (valor == null) return "";
        if (valor.contains(",") || valor.contains("\"") || valor.contains("\n")) {
            return "\"" + valor.replace("\"", "\"\"") + "\"";
        }
        return valor;
    }

    public byte[] generarXlsx(List<InventarioConsultaItem> items) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet hoja = workbook.createSheet("Inventario");

            Row encabezado = hoja.createRow(0);
            for (int i = 0; i < ENCABEZADO.length; i++) {
                encabezado.createCell(i).setCellValue(ENCABEZADO[i]);
            }

            int filaIndex = 1;
            for (InventarioConsultaItem item : items) {
                Row fila = hoja.createRow(filaIndex++);
                fila.createCell(0).setCellValue(item.nombre());
                fila.createCell(1).setCellValue(item.sku() == null ? "" : item.sku());
                fila.createCell(2).setCellValue(item.codigoBarra() == null ? "" : item.codigoBarra());
                fila.createCell(3).setCellValue(item.stock().doubleValue());
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el Excel de Inventario", e);
        }
    }
}
