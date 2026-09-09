package cl.slimerp.inventario;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InventarioExportServiceTest {

    private final InventarioExportService service = new InventarioExportService();

    private List<InventarioConsultaItem> itemsDeEjemplo() {
        return List.of(
                new InventarioConsultaItem(1L, "Mouse", "SKU-1", "7801234567890", new BigDecimal("8")),
                new InventarioConsultaItem(2L, "Teclado, USB", null, null, BigDecimal.ZERO)
        );
    }

    @Test
    void generaUnCsvConEncabezadoYFilasEscapandoComas() {
        byte[] csv = service.generarCsv(itemsDeEjemplo());
        String texto = new String(csv, StandardCharsets.UTF_8);
        String[] lineas = texto.split("\n");

        assertEquals("Producto,Código,Código Barra,Stock", lineas[0].trim());
        assertEquals("Mouse,SKU-1,7801234567890,8", lineas[1].trim());
        assertTrue(lineas[2].startsWith("\"Teclado, USB\","));
    }

    @Test
    void generaUnXlsxLeeibleConEncabezadoYFilas() throws IOException {
        byte[] xlsx = service.generarXlsx(itemsDeEjemplo());
        assertTrue(xlsx.length > 0);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet hoja = workbook.getSheet("Inventario");
            assertNotNull(hoja);

            Row encabezado = hoja.getRow(0);
            assertEquals("Producto", encabezado.getCell(0).getStringCellValue());
            assertEquals("Stock", encabezado.getCell(3).getStringCellValue());

            Row fila1 = hoja.getRow(1);
            assertEquals("Mouse", fila1.getCell(0).getStringCellValue());
            assertEquals("SKU-1", fila1.getCell(1).getStringCellValue());
            assertEquals(8.0, fila1.getCell(3).getNumericCellValue(), 0.001);

            Row fila2 = hoja.getRow(2);
            assertEquals("", fila2.getCell(1).getStringCellValue());
        }
    }

    @Test
    void unaListaVaciaGeneraArchivosValidosSinFilasDeDatos() throws IOException {
        byte[] csv = service.generarCsv(List.of());
        assertEquals("Producto,Código,Código Barra,Stock", new String(csv, StandardCharsets.UTF_8).trim());

        byte[] xlsx = service.generarXlsx(List.of());
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertNotNull(workbook.getSheet("Inventario"));
            assertNull(workbook.getSheet("Inventario").getRow(1));
        }
    }
}
