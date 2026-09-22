package cl.slimerp.cotizaciones;

import cl.slimerp.common.PaginaResponse;
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
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CotizacionController.class)
@Import(CotizacionControllerPermissionTest.MethodSecurityTestConfig.class)
class CotizacionControllerPermissionTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CotizacionService cotizacionService;

    @MockBean
    private CotizacionPdfService cotizacionPdfService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private PermisoEfectivoService permisoEfectivoService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private CotizacionService.CotizacionCompleta cotizacionVacia() {
        return new CotizacionService.CotizacionCompleta(
                1L, 1, "COT-000001", EstadoCotizacion.BORRADOR,
                LocalDate.of(2026, 9, 22), LocalDate.of(2026, 10, 22), false,
                5L, "Empresa ABC SpA", null, "76.111.222-3", null, null, null,
                7L, "Vendedor Demo", null, null,
                null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), List.of());
    }

    @Test
    @WithMockUser(authorities = "COTIZACIONES_VER")
    void permiteListarConElPermisoDeLectura() throws Exception {
        when(cotizacionService.buscar(any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PaginaResponse<>(List.of(), 0));

        mockMvc.perform(get("/api/cotizaciones")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "VENTAS_VER")
    void rechazaListarSinElPermisoCorrecto() throws Exception {
        mockMvc.perform(get("/api/cotizaciones")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "COTIZACIONES_VER")
    void elPermisoDeLecturaNoAlcanzaParaCambiarDeEstado() throws Exception {
        mockMvc.perform(post("/api/cotizaciones/1/enviar").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "COTIZACIONES_EDITAR")
    void permiteEnviarConElPermisoDeEdicion() throws Exception {
        when(cotizacionService.enviar(1L)).thenReturn(cotizacionVacia());

        mockMvc.perform(post("/api/cotizaciones/1/enviar").with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "COTIZACIONES_EDITAR")
    void devuelve400CuandoLaTransicionNoEsValida() throws Exception {
        when(cotizacionService.aceptar(1L))
                .thenThrow(new IllegalArgumentException("No se puede aceptar una cotización en estado BORRADOR"));

        mockMvc.perform(post("/api/cotizaciones/1/aceptar").with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "COTIZACIONES_EDITAR")
    void devuelve400CuandoElCuerpoDeLaCotizacionEsInvalido() throws Exception {
        // Sin clienteId ni items: la validación de bean lo rechaza antes del servicio.
        mockMvc.perform(post("/api/cotizaciones").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fechaEmision\":\"2026-09-22\",\"fechaVencimiento\":\"2026-10-22\"}"))
                .andExpect(status().isBadRequest());
    }
}
