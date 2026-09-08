package cl.slimerp.inventario;

import cl.slimerp.catalogo.Producto;
import cl.slimerp.catalogo.ProductoRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MovimientoImportServiceTest {

    private ProductoRepository productoRepository;
    private MovimientoImportService importService;

    private final Long tenantId = 1L;
    private final Producto productoA = Producto.builder().id(10L).tenantId(1L).sku("SKU-A").nombre("Producto A").activo(true).build();
    private final Producto productoB = Producto.builder().id(20L).tenantId(1L).sku("SKU-B").codigoBarra("7801234567890").nombre("Producto B").activo(true).build();

    @BeforeEach
    void setUp() {
        productoRepository = mock(ProductoRepository.class);
        importService = new MovimientoImportService(productoRepository);

        when(productoRepository.findFirstByTenantIdAndSku(tenantId, "SKU-A")).thenReturn(Optional.of(productoA));
        when(productoRepository.findFirstByTenantIdAndSku(tenantId, "SKU-B")).thenReturn(Optional.empty());
        when(productoRepository.findFirstByTenantIdAndCodigoBarra(tenantId, "SKU-B")).thenReturn(Optional.empty());
        when(productoRepository.findFirstByTenantIdAndSku(tenantId, "7801234567890")).thenReturn(Optional.empty());
        when(productoRepository.findFirstByTenantIdAndCodigoBarra(tenantId, "7801234567890")).thenReturn(Optional.of(productoB));
    }

    private InputStream workbook(String... filas) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("Movimientos");
            String[] encabezado = {"Codigo", "Cantidad"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < encabezado.length; i++) header.createCell(i).setCellValue(encabezado[i]);

            for (int f = 0; f < filas.length; f++) {
                Row row = sheet.createRow(f + 1);
                String[] valores = filas[f].split("\\|", -1);
                for (int i = 0; i < valores.length; i++) row.createCell(i).setCellValue(valores[i]);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    // Variante que escribe la columna Codigo como celda NUMERICA (no String), simulando lo que
    // Excel hace cuando un usuario tipea un código de barras largo directamente en la celda.
    private InputStream workbookConCodigoNumerico(double codigoNumerico, String cantidad) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("Movimientos");
            String[] encabezado = {"Codigo", "Cantidad"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < encabezado.length; i++) header.createCell(i).setCellValue(encabezado[i]);

            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(codigoNumerico);
            row.createCell(1).setCellValue(cantidad);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    @Test
    void codigoDeBarraEscritoComoCeldaNumericaSeResuelveCorrectamente() throws IOException {
        InputStream xlsx = workbookConCodigoNumerico(7801234567890d, "3");

        var resultado = importService.importar(tenantId, xlsx);

        assertEquals(1, resultado.totalFilas());
        assertTrue(resultado.errores().isEmpty());
        assertEquals(1, resultado.items().size());
        assertEquals(20L, resultado.items().get(0).productoId());
        assertEquals(new BigDecimal("3"), resultado.items().get(0).cantidad());
    }

    @Test
    void resuelveVariasFilasPorSkuYPorCodigoDeBarra() throws IOException {
        InputStream xlsx = workbook(
                "SKU-A|2",
                "7801234567890|3"
        );

        var resultado = importService.importar(tenantId, xlsx);

        assertEquals(2, resultado.totalFilas());
        assertTrue(resultado.errores().isEmpty());
        assertEquals(2, resultado.items().size());
        assertEquals("Producto A", resultado.items().get(0).productoNombre());
        assertEquals(new BigDecimal("2"), resultado.items().get(0).cantidad());
        assertEquals("Producto B", resultado.items().get(1).productoNombre());
        assertEquals(new BigDecimal("3"), resultado.items().get(1).cantidad());
    }

    @Test
    void filasQueRepitenElMismoProductoSumanLaCantidad() throws IOException {
        InputStream xlsx = workbook(
                "SKU-A|2",
                "SKU-A|5"
        );

        var resultado = importService.importar(tenantId, xlsx);

        assertEquals(2, resultado.totalFilas());
        assertEquals(1, resultado.items().size());
        assertEquals(new BigDecimal("7"), resultado.items().get(0).cantidad());
    }

    @Test
    void filaConCodigoDesconocidoQuedaComoError() throws IOException {
        InputStream xlsx = workbook("NO-EXISTE|1");

        var resultado = importService.importar(tenantId, xlsx);

        assertEquals(0, resultado.items().size());
        assertEquals(1, resultado.errores().size());
        assertEquals(2, resultado.errores().get(0).numeroFila());
    }

    @Test
    void filaConCantidadInvalidaQuedaComoErrorSinAbortarElResto() throws IOException {
        InputStream xlsx = workbook(
                "SKU-A|no-es-un-numero",
                "7801234567890|4"
        );

        var resultado = importService.importar(tenantId, xlsx);

        assertEquals(1, resultado.items().size());
        assertEquals(new BigDecimal("4"), resultado.items().get(0).cantidad());
        assertEquals(1, resultado.errores().size());
        assertEquals(2, resultado.errores().get(0).numeroFila());
    }

    @Test
    void filaConCantidadCeroONegativaQuedaComoError() throws IOException {
        InputStream xlsx = workbook("SKU-A|0");

        var resultado = importService.importar(tenantId, xlsx);

        assertEquals(0, resultado.items().size());
        assertEquals(1, resultado.errores().size());
    }
}
