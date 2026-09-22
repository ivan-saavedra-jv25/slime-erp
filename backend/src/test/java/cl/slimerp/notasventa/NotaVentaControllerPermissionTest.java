package cl.slimerp.notasventa;

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

@WebMvcTest(NotaVentaController.class)
@Import(NotaVentaControllerPermissionTest.MethodSecurityTestConfig.class)
class NotaVentaControllerPermissionTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotaVentaService notaVentaService;

    @MockBean
    private NotaVentaPdfService notaVentaPdfService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private PermisoEfectivoService permisoEfectivoService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private NotaVentaService.NotaVentaCompleta notaVacia() {
        return new NotaVentaService.NotaVentaCompleta(
                1L, 1, "NV-000001", EstadoNotaVenta.BORRADOR, false,
                OrigenNotaVenta.VENTA_DIRECTA, null, null,
                "CLP", LocalDate.of(2026, 9, 22), null,
                5L, "Empresa ABC SpA", "Empresa ABC SpA", "76.111.222-3", null, null, null,
                7L, "Vendedor Demo",
                null, null,
                null, null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), List.of(), List.of());
    }

    @Test
    @WithMockUser(authorities = "NOTAS_VENTA_VER")
    void permiteListarConElPermisoDeLectura() throws Exception {
        when(notaVentaService.buscar(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PaginaResponse<>(List.of(), 0));

        mockMvc.perform(get("/api/notas-venta")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "VENTAS_VER")
    void rechazaListarSinElPermisoCorrecto() throws Exception {
        mockMvc.perform(get("/api/notas-venta")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "NOTAS_VENTA_VER")
    void elPermisoDeLecturaNoAlcanzaParaCambiarDeEstado() throws Exception {
        mockMvc.perform(post("/api/notas-venta/1/confirmar").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "NOTAS_VENTA_EDITAR")
    void permiteConfirmarConElPermisoDeEdicion() throws Exception {
        when(notaVentaService.confirmar(1L)).thenReturn(notaVacia());

        mockMvc.perform(post("/api/notas-venta/1/confirmar").with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "NOTAS_VENTA_EDITAR")
    void devuelve400CuandoLaTransicionNoEsValida() throws Exception {
        when(notaVentaService.confirmar(1L))
                .thenThrow(new IllegalArgumentException("No se puede confirmar una nota de venta en estado CONFIRMADA"));

        mockMvc.perform(post("/api/notas-venta/1/confirmar").with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "NOTAS_VENTA_EDITAR")
    void devuelve400CuandoElCuerpoDeLaNotaEsInvalido() throws Exception {
        // Sin clienteId ni items: la validación de bean lo rechaza antes del servicio.
        mockMvc.perform(post("/api/notas-venta").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fechaEmision\":\"2026-09-22\"}"))
                .andExpect(status().isBadRequest());
    }
}