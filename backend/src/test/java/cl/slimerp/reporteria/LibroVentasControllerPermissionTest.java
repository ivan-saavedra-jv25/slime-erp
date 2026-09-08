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

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @WithMockUser(authorities = "VENTAS_VER")
    void permiteConsultarConElPermisoCorrecto() throws Exception {
        TenantContext.setTenantId(1L);
        LocalDate desde = LocalDate.of(2026, 9, 1);
        LocalDate hasta = LocalDate.of(2026, 9, 30);
        when(libroVentasService.generar(1L, desde, hasta)).thenReturn(
                new LibroVentasService.LibroVentasResponse(desde, hasta, List.of(), List.of(),
                        new LibroVentasService.LibroVentasSubtotal("Total", 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)));

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
        LocalDate desde = LocalDate.of(2026, 9, 30);
        LocalDate hasta = LocalDate.of(2026, 9, 1);
        when(libroVentasService.generar(1L, desde, hasta))
                .thenThrow(new IllegalArgumentException("La fecha 'desde' no puede ser posterior a 'hasta'"));

        mockMvc.perform(get("/api/reportes/libro-ventas").param("desde", "2026-09-30").param("hasta", "2026-09-01"))
                .andExpect(status().isBadRequest());
    }
}
