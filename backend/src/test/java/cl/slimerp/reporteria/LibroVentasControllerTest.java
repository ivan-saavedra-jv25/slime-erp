package cl.slimerp.reporteria;

import cl.slimerp.config.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LibroVentasControllerTest {

    private LibroVentasService libroVentasService;
    private LibroVentasExcelService libroVentasExcelService;
    private LibroVentasController controller;

    private final Long tenantId = 1L;
    private final LocalDate desde = LocalDate.of(2026, 9, 1);
    private final LocalDate hasta = LocalDate.of(2026, 9, 30);

    @BeforeEach
    void setUp() {
        libroVentasService = mock(LibroVentasService.class);
        libroVentasExcelService = mock(LibroVentasExcelService.class);
        controller = new LibroVentasController(libroVentasService, libroVentasExcelService);
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private LibroVentasService.LibroVentasResponse libroVacio(String tipoDocumento, String busqueda) {
        return new LibroVentasService.LibroVentasResponse(desde, hasta, tipoDocumento, busqueda, List.of(), List.of(),
                new LibroVentasService.LibroVentasSubtotal("Total", 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                0, 0, 10);
    }

    @Test
    void libroVentasDelegaEnElServicioConElTenantDelContextoYLosValoresPorDefecto() {
        when(libroVentasService.generar(tenantId, desde, hasta, null, null, 0, 10)).thenReturn(libroVacio(null, null));

        LibroVentasService.LibroVentasResponse respuesta = controller.libroVentas(desde, hasta, null, null, 0, 10);

        assertEquals(desde, respuesta.desde());
        verify(libroVentasService).generar(tenantId, desde, hasta, null, null, 0, 10);
    }

    @Test
    void libroVentasPasaElTipoDocumentoLaBusquedaYLaPaginacionAlServicio() {
        when(libroVentasService.generar(tenantId, desde, hasta, "Factura", "andes", 1, 25))
                .thenReturn(libroVacio("Factura", "andes"));

        LibroVentasService.LibroVentasResponse respuesta = controller.libroVentas(desde, hasta, "Factura", "andes", 1, 25);

        assertEquals("Factura", respuesta.tipoDocumento());
        verify(libroVentasService).generar(tenantId, desde, hasta, "Factura", "andes", 1, 25);
    }

    @Test
    void libroVentasExcelUsaGenerarCompletoSinPaginar() {
        when(libroVentasService.generarCompleto(tenantId, desde, hasta, null, null)).thenReturn(libroVacio(null, null));
        byte[] excelFalso = new byte[]{1, 2, 3};
        when(libroVentasExcelService.generar(any())).thenReturn(excelFalso);

        ResponseEntity<byte[]> respuesta = controller.libroVentasExcel(desde, hasta, null, null);

        assertArrayEquals(excelFalso, respuesta.getBody());
        assertEquals(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                respuesta.getHeaders().getContentType());
        assertTrue(respuesta.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)
                .contains("libro-ventas-2026-09-01-a-2026-09-30.xlsx"));
        verify(libroVentasService).generarCompleto(tenantId, desde, hasta, null, null);
        verify(libroVentasService, never()).generar(any(), any(), any(), any(), any(), anyInt(), anyInt());
    }
}
