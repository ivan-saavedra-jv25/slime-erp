package cl.slimerp.inventario;

import cl.slimerp.common.PaginaResponse;
import cl.slimerp.config.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventarioConsultaControllerTest {

    private InventarioConsultaService service;
    private InventarioExportService exportService;
    private InventarioConsultaController controller;

    @BeforeEach
    void setUp() {
        service = mock(InventarioConsultaService.class);
        exportService = mock(InventarioExportService.class);
        controller = new InventarioConsultaController(service, exportService);
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void listarDelegaEnElServicioConElTenantActual() {
        var item = new InventarioConsultaItem(1L, "Mouse", "SKU-1", null, BigDecimal.TEN);
        when(service.consultar(1L, 2L, 3L, 4L, true, TipoBusquedaInventario.SKU, "abc", "stock", "desc", 1, 25))
                .thenReturn(new PaginaResponse<>(List.of(item), 1));

        var respuesta = controller.listar(2L, 3L, 4L, true, TipoBusquedaInventario.SKU, "abc", "stock", "desc", 1, 25);

        assertEquals(1, respuesta.total());
        assertEquals(item, respuesta.contenido().get(0));
    }

    @Test
    void exportarCsvDevuelveElArchivoConElContentTypeYNombreCorrectos() {
        var item = new InventarioConsultaItem(1L, "Mouse", "SKU-1", null, BigDecimal.TEN);
        when(service.consultarTodo(1L, null, null, null, false, TipoBusquedaInventario.NOMBRE, null))
                .thenReturn(List.of(item));
        when(exportService.generarCsv(List.of(item))).thenReturn("csv-bytes".getBytes());

        var respuesta = controller.exportarCsv(null, null, null, false, TipoBusquedaInventario.NOMBRE, null);

        assertEquals(200, respuesta.getStatusCode().value());
        assertEquals("text/csv", respuesta.getHeaders().getContentType().toString());
        assertTrue(respuesta.getHeaders().getContentDisposition().toString().contains("inventario.csv"));
        assertArrayEquals("csv-bytes".getBytes(), respuesta.getBody());
    }

    @Test
    void exportarXlsxDevuelveElArchivoConElContentTypeYNombreCorrectos() {
        when(service.consultarTodo(1L, 5L, null, null, false, TipoBusquedaInventario.NOMBRE, null))
                .thenReturn(List.of());
        when(exportService.generarXlsx(List.of())).thenReturn("xlsx-bytes".getBytes());

        var respuesta = controller.exportarXlsx(5L, null, null, false, TipoBusquedaInventario.NOMBRE, null);

        assertEquals(200, respuesta.getStatusCode().value());
        assertTrue(respuesta.getHeaders().getContentDisposition().toString().contains("inventario.xlsx"));
        assertArrayEquals("xlsx-bytes".getBytes(), respuesta.getBody());
    }
}
