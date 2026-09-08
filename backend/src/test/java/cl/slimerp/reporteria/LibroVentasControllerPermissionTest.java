package cl.slimerp.reporteria;

import cl.slimerp.config.JwtService;
import cl.slimerp.config.TenantContext;
import cl.slimerp.permisos.PermisoEfectivoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LibroVentasController.class)
@Import(LibroVentasControllerPermissionTest.MethodSecurityTestConfig.class)
class LibroVentasControllerPermissionTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LibroVentasService libroVentasService;

    @MockBean
    private LibroVentasExcelService libroVentasExcelService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private PermisoEfectivoService permisoEfectivoService;

    private final LocalDate desde = LocalDate.of(2026, 9, 1);
    private final LocalDate hasta = LocalDate.of(2026, 9, 30);

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
    @WithMockUser(authorities = "VENTAS_VER")
    void permiteConsultarConElPermisoCorrectoYLosValoresPorDefectoDePaginacion() throws Exception {
        TenantContext.setTenantId(1L);
        when(libroVentasService.generar(1L, desde, hasta, null, null, 0, 10)).thenReturn(libroVacio(null, null));

        mockMvc.perform(get("/api/reportes/libro-ventas").param("desde", "2026-09-01").param("hasta", "2026-09-30"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "PRODUCTOS_VER")
    void rechazaConsultarSinElPermisoCorrecto() throws Exception {
        mockMvc.perform(get("/api/reportes/libro-ventas").param("desde", "2026-09-01").param("hasta", "2026-09-30"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "VENTAS_VER")
    void devuelve400CuandoDesdeEsPosteriorAHasta() throws Exception {
        TenantContext.setTenantId(1L);
        LocalDate desdeInvertido = LocalDate.of(2026, 9, 30);
        LocalDate hastaInvertido = LocalDate.of(2026, 9, 1);
        when(libroVentasService.generar(1L, desdeInvertido, hastaInvertido, null, null, 0, 10))
                .thenThrow(new IllegalArgumentException("La fecha 'desde' no puede ser posterior a 'hasta'"));

        mockMvc.perform(get("/api/reportes/libro-ventas").param("desde", "2026-09-30").param("hasta", "2026-09-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "VENTAS_VER")
    void pasaElParametroTipoDocumentoAlServicioCuandoViaja() throws Exception {
        TenantContext.setTenantId(1L);
        when(libroVentasService.generar(1L, desde, hasta, "Factura", null, 0, 10))
                .thenReturn(libroVacio("Factura", null));

        mockMvc.perform(get("/api/reportes/libro-ventas")
                        .param("desde", "2026-09-01").param("hasta", "2026-09-30").param("tipoDocumento", "Factura"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "VENTAS_VER")
    void devuelve400CuandoElTipoDocumentoNoEsReconocido() throws Exception {
        TenantContext.setTenantId(1L);
        when(libroVentasService.generar(1L, desde, hasta, "Nota de Crédito", null, 0, 10))
                .thenThrow(new IllegalArgumentException("Tipo de documento no reconocido: Nota de Crédito"));

        mockMvc.perform(get("/api/reportes/libro-ventas")
                        .param("desde", "2026-09-01").param("hasta", "2026-09-30").param("tipoDocumento", "Nota de Crédito"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "VENTAS_VER")
    void pasaElParametroQYLaPaginacionAlServicio() throws Exception {
        TenantContext.setTenantId(1L);
        when(libroVentasService.generar(1L, desde, hasta, null, "andes", 2, 25))
                .thenReturn(libroVacio(null, "andes"));

        mockMvc.perform(get("/api/reportes/libro-ventas")
                        .param("desde", "2026-09-01").param("hasta", "2026-09-30")
                        .param("q", "andes").param("pagina", "2").param("tamano", "25"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "VENTAS_VER")
    void devuelve400CuandoLaPaginaEsNegativa() throws Exception {
        TenantContext.setTenantId(1L);
        when(libroVentasService.generar(1L, desde, hasta, null, null, -1, 10))
                .thenThrow(new IllegalArgumentException("La página debe ser 0 o mayor"));

        mockMvc.perform(get("/api/reportes/libro-ventas")
                        .param("desde", "2026-09-01").param("hasta", "2026-09-30").param("pagina", "-1"))
                .andExpect(status().isBadRequest());
    }
}
