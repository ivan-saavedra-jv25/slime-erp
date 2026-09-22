package cl.slimerp.reporteria;

import cl.slimerp.cotizaciones.EstadoCotizacion;
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

class LibroCotizacionesExcelServiceTest {

    private final LibroCotizacionesExcelService service = new LibroCotizacionesExcelService();

    private LibroCotizacionesService.LibroCotizacionesResponse libroDeEjemplo() {
        List<LibroCotizacionesService.LibroCotizacionesFila> filas = List.of(
                new LibroCotizacionesService.LibroCotizacionesFila(1L, "COT-000001", LocalDate.of(2026, 9, 5),
                        "Empresa ABC SpA", "76.111.222-3", EstadoCotizacion.ACEPTADA,
                        new BigDecimal("1000.00"), new BigDecimal("190.00"), new BigDecimal("1190.00"),
                        "Vendedor Demo", "NV-000010"),
                new LibroCotizacionesService.LibroCotizacionesFila(2L, "COT-000002", LocalDate.of(2026, 9, 6),
                        "Cliente Sin Rut", null, EstadoCotizacion.RECHAZADA,
                        new BigDecimal("500.00"), new BigDecimal("95.00"), new BigDecimal("595.00"),
                        "Vendedor Demo", "—"));
        return new LibroCotizacionesService.LibroCotizacionesResponse(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null, filas,
                new LibroCotizacionesService.LibroCotizacionesResumen(2, new BigDecimal("1500.00"),
                        new BigDecimal("285.00"), new BigDecimal("1785.00")));
    }

    @Test
    void generaUnExcelReleibleConElResumenYElDetalle() throws IOException {
        byte[] excel = service.generar(libroDeEjemplo());

        assertTrue(excel.length > 0);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            Sheet hoja = workbook.getSheet("Libro de Cotizaciones");
            assertNotNull(hoja);

            assertTrue(hoja.getRow(0).getCell(0).getStringCellValue().contains("01-09-2026"));

            Row filaResumen = hoja.getRow(3);
            assertEquals(2.0, filaResumen.getCell(0).getNumericCellValue(), 0.001);
            assertEquals(1785.00, filaResumen.getCell(3).getNumericCellValue(), 0.001);

            Row encabezadoDetalle = hoja.getRow(5);
            assertEquals("Número", encabezadoDetalle.getCell(0).getStringCellValue());
            assertEquals("Documentos relacionados", encabezadoDetalle.getCell(9).getStringCellValue());

            Row detalle1 = hoja.getRow(6);
            assertEquals("COT-000001", detalle1.getCell(0).getStringCellValue());
            assertEquals("ACEPTADA", detalle1.getCell(4).getStringCellValue());
            assertEquals("NV-000010", detalle1.getCell(9).getStringCellValue());

            Row detalle2 = hoja.getRow(7);
            assertEquals("", detalle2.getCell(3).getStringCellValue());
        }
    }

    @Test
    void elTituloIncluyeElEstadoCuandoSeFiltro() throws IOException {
        var base = libroDeEjemplo();
        var filtrado = new LibroCotizacionesService.LibroCotizacionesResponse(
                base.desde(), base.hasta(), EstadoCotizacion.ACEPTADA, base.filas(), base.resumen());

        byte[] excel = service.generar(filtrado);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            assertTrue(workbook.getSheet("Libro de Cotizaciones").getRow(0).getCell(0)
                    .getStringCellValue().contains("ACEPTADA"));
        }
    }

    @Test
    void unLibroVacioGeneraUnExcelValidoSinFilasDeDetalle() throws IOException {
        var vacio = new LibroCotizacionesService.LibroCotizacionesResponse(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null, List.of(),
                new LibroCotizacionesService.LibroCotizacionesResumen(0, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO));

        byte[] excel = service.generar(vacio);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            Sheet hoja = workbook.getSheet("Libro de Cotizaciones");
            assertEquals(0.0, hoja.getRow(3).getCell(0).getNumericCellValue(), 0.001);
            assertNull(hoja.getRow(6));
        }
    }
}
