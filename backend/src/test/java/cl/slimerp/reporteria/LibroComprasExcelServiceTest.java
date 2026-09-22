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

class LibroComprasExcelServiceTest {

    private final LibroComprasExcelService service = new LibroComprasExcelService();

    private LibroComprasService.LibroComprasResponse libroDeEjemplo() {
        List<LibroComprasService.LibroComprasFila> filas = List.of(
                new LibroComprasService.LibroComprasFila(1L, LocalDateTime.of(2026, 9, 1, 10, 30), "FAC-1",
                        "Proveedor Uno", "11.111.111-1", 2,
                        new BigDecimal("1000.00"), new BigDecimal("190.00"), new BigDecimal("1190.00"), "En deuda"),
                new LibroComprasService.LibroComprasFila(2L, LocalDateTime.of(2026, 9, 2, 12, 0), null,
                        "Proveedor Dos", null, 1,
                        new BigDecimal("500.00"), new BigDecimal("95.00"), new BigDecimal("595.00"), "Pagado")
        );
        LibroComprasService.LibroComprasResumen resumen = new LibroComprasService.LibroComprasResumen(
                2, new BigDecimal("1500.00"), new BigDecimal("285.00"), new BigDecimal("1785.00"));
        List<LibroComprasService.LibroComprasPuntoEvolucion> evolucion = List.of(
                new LibroComprasService.LibroComprasPuntoEvolucion("01/09", new BigDecimal("1190.00")),
                new LibroComprasService.LibroComprasPuntoEvolucion("02/09", new BigDecimal("595.00")));
        return new LibroComprasService.LibroComprasResponse(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), filas, resumen, evolucion);
    }

    @Test
    void generaUnExcelReleiblesConElResumenYElDetalleCorrectos() throws IOException {
        byte[] excel = service.generar(libroDeEjemplo());

        assertTrue(excel.length > 0);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            Sheet hoja = workbook.getSheet("Libro de Compras");
            assertNotNull(hoja);

            Row filaTitulo = hoja.getRow(0);
            assertTrue(filaTitulo.getCell(0).getStringCellValue().contains("01-09-2026"));

            // Fila 1 blank, fila 2 encabezado resumen, fila 3 resumen.
            Row encabezadoResumen = hoja.getRow(2);
            assertEquals("Cantidad", encabezadoResumen.getCell(0).getStringCellValue());

            Row filaResumen = hoja.getRow(3);
            assertEquals(2.0, filaResumen.getCell(0).getNumericCellValue(), 0.001);
            assertEquals(1785.00, filaResumen.getCell(3).getNumericCellValue(), 0.001);

            // Fila 4 blank, fila 5 encabezado detalle, filas 6+ detalle.
            Row encabezadoDetalle = hoja.getRow(5);
            assertEquals("Proveedor", encabezadoDetalle.getCell(3).getStringCellValue());
            assertEquals("Estado", encabezadoDetalle.getCell(9).getStringCellValue());

            Row detalle1 = hoja.getRow(6);
            assertEquals(1.0, detalle1.getCell(0).getNumericCellValue(), 0.001);
            assertEquals("FAC-1", detalle1.getCell(2).getStringCellValue());
            assertEquals("Proveedor Uno", detalle1.getCell(3).getStringCellValue());
            assertEquals(1190.00, detalle1.getCell(8).getNumericCellValue(), 0.001);
            assertEquals("En deuda", detalle1.getCell(9).getStringCellValue());

            Row detalle2 = hoja.getRow(7);
            assertEquals("", detalle2.getCell(2).getStringCellValue());
            assertEquals("", detalle2.getCell(4).getStringCellValue());
        }
    }

    @Test
    void unLibroSinComprasGeneraUnExcelValidoSinFilasDeDetalle() throws IOException {
        LibroComprasService.LibroComprasResponse libroVacio = new LibroComprasService.LibroComprasResponse(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), List.of(),
                new LibroComprasService.LibroComprasResumen(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                List.of());

        byte[] excel = service.generar(libroVacio);

        assertTrue(excel.length > 0);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            Sheet hoja = workbook.getSheet("Libro de Compras");
            assertNotNull(hoja);
            Row filaResumen = hoja.getRow(3);
            assertEquals(0.0, filaResumen.getCell(0).getNumericCellValue(), 0.001);
            assertNull(hoja.getRow(6));
        }
    }
}
