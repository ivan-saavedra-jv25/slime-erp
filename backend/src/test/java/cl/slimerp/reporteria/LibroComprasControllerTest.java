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

class LibroComprasControllerTest {

    private LibroComprasService libroComprasService;
    private LibroComprasExcelService libroComprasExcelService;
    private LibroComprasController controller;

    private final Long tenantId = 1L;
    private final LocalDate desde = LocalDate.of(2026, 9, 1);
    private final LocalDate hasta = LocalDate.of(2026, 9, 30);

    @BeforeEach
    void setUp() {
        libroComprasService = mock(LibroComprasService.class);
        libroComprasExcelService = mock(LibroComprasExcelService.class);
        controller = new LibroComprasController(libroComprasService, libroComprasExcelService);
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private LibroComprasService.LibroComprasResponse libroVacio() {
        return new LibroComprasService.LibroComprasResponse(desde, hasta, List.of(),
                new LibroComprasService.LibroComprasResumen(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                List.of());
    }

    @Test
    void libroComprasDelegaEnElServicioConElTenantDelContexto() {
        when(libroComprasService.generar(tenantId, desde, hasta)).thenReturn(libroVacio());

        LibroComprasService.LibroComprasResponse respuesta = controller.libroCompras(desde, hasta);

        assertEquals(desde, respuesta.desde());
        verify(libroComprasService).generar(tenantId, desde, hasta);
    }

    @Test
    void libroComprasExcelDevuelveElContentTypeYNombreDeArchivoCorrectos() {
        when(libroComprasService.generar(tenantId, desde, hasta)).thenReturn(libroVacio());
        byte[] excelFalso = new byte[]{1, 2, 3};
        when(libroComprasExcelService.generar(any())).thenReturn(excelFalso);

        ResponseEntity<byte[]> respuesta = controller.libroComprasExcel(desde, hasta);

        assertArrayEquals(excelFalso, respuesta.getBody());
        assertEquals(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                respuesta.getHeaders().getContentType());
        assertTrue(respuesta.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)
                .contains("libro-compras-2026-09-01-a-2026-09-30.xlsx"));
    }
}
