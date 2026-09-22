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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LibroNotasVentaController.class)
@Import(LibroNotasVentaControllerPermissionTest.MethodSecurityTestConfig.class)
class LibroNotasVentaControllerPermissionTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LibroNotasVentaService libroNotasVentaService;

    @MockBean
    private LibroNotasVentaExcelService libroNotasVentaExcelService;

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

    private LibroNotasVentaService.LibroNotasVentaResponse libroVacio() {
        return new LibroNotasVentaService.LibroNotasVentaResponse(desde, hasta, null, List.of(),
                new LibroNotasVentaService.LibroNotasVentaResumen(0, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO));
    }

    @Test
    @WithMockUser(authorities = "NOTAS_VENTA_VER")
    void permiteConsultarConElPermisoCorrecto() throws Exception {
        TenantContext.setTenantId(1L);
        when(libroNotasVentaService.generar(1L, desde, hasta, null)).thenReturn(libroVacio());

        mockMvc.perform(get("/api/reportes/libro-notas-venta")
                        .param("desde", "2026-09-01").param("hasta", "2026-09-30"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "PRODUCTOS_VER")
    void rechazaConsultarSinElPermisoCorrecto() throws Exception {
        mockMvc.perform(get("/api/reportes/libro-notas-venta")
                        .param("desde", "2026-09-01").param("hasta", "2026-09-30"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "NOTAS_VENTA_VER")
    void devuelve400CuandoDesdeEsPosteriorAHasta() throws Exception {
        TenantContext.setTenantId(1L);
        when(libroNotasVentaService.generar(eq(1L), eq(LocalDate.of(2026, 9, 30)),
                eq(LocalDate.of(2026, 9, 1)), eq(null)))
                .thenThrow(new IllegalArgumentException("La fecha 'desde' no puede ser posterior a 'hasta'"));

        mockMvc.perform(get("/api/reportes/libro-notas-venta")
                        .param("desde", "2026-09-30").param("hasta", "2026-09-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "NOTAS_VENTA_VER")
    void pasaElParametroEstadoAlServicioCuandoViaja() throws Exception {
        TenantContext.setTenantId(1L);
        when(libroNotasVentaService.generar(1L, desde, hasta,
                cl.slimerp.notasventa.EstadoNotaVenta.CANCELADA)).thenReturn(libroVacio());

        mockMvc.perform(get("/api/reportes/libro-notas-venta")
                        .param("desde", "2026-09-01").param("hasta", "2026-09-30").param("estado", "CANCELADA"))
                .andExpect(status().isOk());
    }
}