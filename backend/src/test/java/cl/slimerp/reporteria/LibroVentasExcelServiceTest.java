package cl.slimerp.reporteria;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LibroVentasExcelServiceTest {

    private final LibroVentasExcelService service = new LibroVentasExcelService();

    private LibroVentasService.LibroVentasResponse libroDeEjemplo() {
        List<LibroVentasService.LibroVentasFila> filas = List.of(
                new LibroVentasService.LibroVentasFila(1L, LocalDateTime.of(2026, 9, 1, 10, 30), "Factura",
                        "11.111.111-1", "Cliente Uno",
                        new BigDecimal("1000.00"), new BigDecimal("190.00"), new BigDecimal("1190.00")),
                new LibroVentasService.LibroVentasFila(2L, LocalDateTime.of(2026, 9, 2, 12, 0), "Boleta",
                        null, "Cliente Sin Rut",
                        new BigDecimal("500.00"), new BigDecimal("95.00"), new BigDecimal("595.00"))
        );
        List<LibroVentasService.LibroVentasSubtotal> subtotales = List.of(
                new LibroVentasService.LibroVentasSubtotal("Factura", 1, new BigDecimal("1000.00"), new BigDecimal("190.00"), new BigDecimal("1190.00")),
                new LibroVentasService.LibroVentasSubtotal("Boleta", 1, new BigDecimal("500.00"), new BigDecimal("95.00"), new BigDecimal("595.00"))
        );
        LibroVentasService.LibroVentasSubtotal totalGeneral = new LibroVentasService.LibroVentasSubtotal(
                "Total", 2, new BigDecimal("1500.00"), new BigDecimal("285.00"), new BigDecimal("1785.00"));
        return new LibroVentasService.LibroVentasResponse(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), filas, subtotales, totalGeneral);
    }

    @Test
    void generaUnExcelReleiblesConLasFilasYSubtotalesCorrectos() throws IOException {
        byte[] excel = service.generar(libroDeEjemplo());

        assertTrue(excel.length > 0);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            Sheet hoja = workbook.getSheet("Libro de Ventas");
            assertNotNull(hoja);

            // Fila 0: título con el período.
            Row filaTitulo = hoja.getRow(0);
            assertTrue(filaTitulo.getCell(0).getStringCellValue().contains("01-09-2026"));

            // Fila 1: blank. Fila 2: encabezado subtotales. Filas 3-4: subtotales. Fila 5: total.
            Row subtotalFactura = hoja.getRow(3);
            assertEquals("Factura", subtotalFactura.getCell(0).getStringCellValue());
            assertEquals(1190.00, subtotalFactura.getCell(4).getNumericCellValue(), 0.001);

            Row subtotalBoleta = hoja.getRow(4);
            assertEquals("Boleta", subtotalBoleta.getCell(0).getStringCellValue());

            Row filaTotal = hoja.getRow(5);
            assertEquals("Total", filaTotal.getCell(0).getStringCellValue());
            assertEquals(1785.00, filaTotal.getCell(4).getNumericCellValue(), 0.001);

            // Fila 6: blank. Fila 7: encabezado detalle. Filas 8+: detalle.
            Row encabezadoDetalle = hoja.getRow(7);
            assertEquals("Cliente", encabezadoDetalle.getCell(4).getStringCellValue());

            Row detalle1 = hoja.getRow(8);
            assertEquals(1.0, detalle1.getCell(0).getNumericCellValue(), 0.001);
            assertEquals("Factura", detalle1.getCell(2).getStringCellValue());
            assertEquals("11.111.111-1", detalle1.getCell(3).getStringCellValue());
            assertEquals("Cliente Uno", detalle1.getCell(4).getStringCellValue());
            assertEquals(1190.00, detalle1.getCell(7).getNumericCellValue(), 0.001);

            Row detalle2 = hoja.getRow(9);
            assertEquals("", detalle2.getCell(3).getStringCellValue());
            assertEquals("Cliente Sin Rut", detalle2.getCell(4).getStringCellValue());
        }
    }

    @Test
    void unLibroSinVentasGeneraUnExcelValidoSinFilasDeDetalle() throws IOException {
        LibroVentasService.LibroVentasResponse libroVacio = new LibroVentasService.LibroVentasResponse(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), List.of(), List.of(),
                new LibroVentasService.LibroVentasSubtotal("Total", 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        byte[] excel = service.generar(libroVacio);

        assertTrue(excel.length > 0);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            Sheet hoja = workbook.getSheet("Libro de Ventas");
            assertNotNull(hoja);
            // Fila 0 título, fila 1 blank, fila 2 encabezado subtotales, fila 3 total
            // general (sin subtotales por tipo), fila 4 blank, fila 5 encabezado
            // detalle, sin filas de detalle después.
            Row filaTotal = hoja.getRow(3);
            assertEquals("Total", filaTotal.getCell(0).getStringCellValue());
            assertNull(hoja.getRow(6));
        }
    }
}
