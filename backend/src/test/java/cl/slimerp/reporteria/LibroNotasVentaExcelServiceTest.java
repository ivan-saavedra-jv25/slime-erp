package cl.slimerp.reporteria;

import cl.slimerp.notasventa.EstadoNotaVenta;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LibroNotasVentaExcelServiceTest {

    private final LibroNotasVentaExcelService service = new LibroNotasVentaExcelService();

    private LibroNotasVentaService.LibroNotasVentaResponse libroDeEjemplo() {
        List<LibroNotasVentaService.LibroNotasVentaFila> filas = List.of(
                new LibroNotasVentaService.LibroNotasVentaFila(1L, "NV-000001", LocalDate.of(2026, 9, 5),
                        "Empresa ABC SpA", "76.111.222-3", EstadoNotaVenta.CONFIRMADA,
                        new BigDecimal("1000.00"), new BigDecimal("190.00"), new BigDecimal("1190.00"),
                        "Vendedor Demo", "Venta directa", "GUI-000001"),
                new LibroNotasVentaService.LibroNotasVentaFila(2L, "NV-000002", LocalDate.of(2026, 9, 6),
                        "Cliente Sin Rut", null, EstadoNotaVenta.CANCELADA,
                        new BigDecimal("500.00"), new BigDecimal("95.00"), new BigDecimal("595.00"),
                        "Vendedor Demo", "Cotización COT-000012", "—"));
        return new LibroNotasVentaService.LibroNotasVentaResponse(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null, filas,
                new LibroNotasVentaService.LibroNotasVentaResumen(2, new BigDecimal("1500.00"),
                        new BigDecimal("285.00"), new BigDecimal("1785.00")));
    }

    @Test
    void generaUnExcelReleibleConElResumenYElDetalle() throws IOException {
        byte[] excel = service.generar(libroDeEjemplo());

        assertTrue(excel.length > 0);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            Sheet hoja = workbook.getSheet("Libro de Notas de Venta");
            assertNotNull(hoja);

            assertTrue(hoja.getRow(0).getCell(0).getStringCellValue().contains("01-09-2026"));

            Row filaResumen = hoja.getRow(3);
            assertEquals(2.0, filaResumen.getCell(0).getNumericCellValue(), 0.001);
            assertEquals(1785.00, filaResumen.getCell(3).getNumericCellValue(), 0.001);

            Row encabezadoDetalle = hoja.getRow(5);
            assertEquals("Número", encabezadoDetalle.getCell(0).getStringCellValue());
            assertEquals("Documentos relacionados", encabezadoDetalle.getCell(10).getStringCellValue());

            Row detalle1 = hoja.getRow(6);
            assertEquals("NV-000001", detalle1.getCell(0).getStringCellValue());
            assertEquals("CONFIRMADA", detalle1.getCell(4).getStringCellValue());
            assertEquals("GUI-000001", detalle1.getCell(10).getStringCellValue());

            Row detalle2 = hoja.getRow(7);
            assertEquals("", detalle2.getCell(3).getStringCellValue());
        }
    }

    @Test
    void elTituloIncluyeElEstadoCuandoSeFiltro() throws IOException {
        var base = libroDeEjemplo();
        var filtrado = new LibroNotasVentaService.LibroNotasVentaResponse(
                base.desde(), base.hasta(), EstadoNotaVenta.CONFIRMADA, base.filas(), base.resumen());

        byte[] excel = service.generar(filtrado);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            assertTrue(workbook.getSheet("Libro de Notas de Venta").getRow(0).getCell(0)
                    .getStringCellValue().contains("CONFIRMADA"));
        }
    }

    @Test
    void unLibroVacioGeneraUnExcelValidoSinFilasDeDetalle() throws IOException {
        var vacio = new LibroNotasVentaService.LibroNotasVentaResponse(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null, List.of(),
                new LibroNotasVentaService.LibroNotasVentaResumen(0, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO));

        byte[] excel = service.generar(vacio);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            Sheet hoja = workbook.getSheet("Libro de Notas de Venta");
            assertEquals(0.0, hoja.getRow(3).getCell(0).getNumericCellValue(), 0.001);
            assertNull(hoja.getRow(6));
        }
    }
}