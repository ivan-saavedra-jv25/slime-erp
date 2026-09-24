package cl.slimerp.notascredito;

import cl.slimerp.common.PaginaResponse;
import cl.slimerp.config.JwtService;
import cl.slimerp.config.TenantContext;
import cl.slimerp.permisos.PermisoEfectivoService;
import cl.slimerp.ventas.TipoDocumentoVenta;
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

@WebMvcTest(NotaCreditoController.class)
@Import(NotaCreditoControllerPermissionTest.MethodSecurityTestConfig.class)
class NotaCreditoControllerPermissionTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotaCreditoService notaCreditoService;

    @MockBean
    private NotaCreditoPdfService notaCreditoPdfService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private PermisoEfectivoService permisoEfectivoService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private NotaCreditoService.NotaCreditoCompleta notaVacia() {
        return new NotaCreditoService.NotaCreditoCompleta(
                1L, 1, "NC-000001", EstadoNotaCredito.BORRADOR, TipoCorreccion.CORRIGE_MONTO,
                LocalDate.of(2026, 9, 23),
                5L, "Empresa ABC SpA", null, "76.111.222-3", null, null, null,
                7L, "Vendedor Demo",
                new NotaCreditoService.DocumentoAsociado(50L, TipoDocumentoVenta.FACTURA, 1042,
                        "FACTURA N.º 1042", LocalDate.of(2026, 9, 12), "Devolución parcial",
                        new BigDecimal("12852")),
                null, null, null,
                3L, "Bodega Central", false, "CLP",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, RecuperacionInventario.NO,
                List.of(), List.of(), List.of());
    }

    @Test
    @WithMockUser(authorities = "NOTAS_CREDITO_VER")
    void permiteListarConElPermisoDeLectura() throws Exception {
        when(notaCreditoService.buscar(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt())).thenReturn(new PaginaResponse<>(List.of(), 0));

        mockMvc.perform(get("/api/notas-credito")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "NOTAS_VENTA_VER")
    void rechazaListarSinElPermisoCorrecto() throws Exception {
        mockMvc.perform(get("/api/notas-credito")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "NOTAS_CREDITO_VER")
    void permiteConsultarLasLineasDelDocumentoAsociado() throws Exception {
        when(notaCreditoService.lineasDocumento(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/notas-credito/ventas/50/lineas")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "NOTAS_CREDITO_VER")
    void elPermisoDeLecturaNoAlcanzaParaEmitir() throws Exception {
        mockMvc.perform(post("/api/notas-credito/1/emitir").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "NOTAS_CREDITO_VER")
    void elPermisoDeLecturaNoAlcanzaParaAnular() throws Exception {
        mockMvc.perform(post("/api/notas-credito/1/anular").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "NOTAS_CREDITO_EDITAR")
    void permiteEmitirConElPermisoDeEdicion() throws Exception {
        when(notaCreditoService.emitir(1L)).thenReturn(notaVacia());

        mockMvc.perform(post("/api/notas-credito/1/emitir").with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "NOTAS_CREDITO_EDITAR")
    void devuelve400CuandoLaTransicionNoEsValida() throws Exception {
        when(notaCreditoService.emitir(1L)).thenThrow(new IllegalArgumentException(
                "No se puede emitir una nota de crédito en estado EMITIDA"));

        mockMvc.perform(post("/api/notas-credito/1/emitir").with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "NOTAS_CREDITO_EDITAR")
    void devuelve400CuandoNoSePuedeRecuperarMasDeLoDisponible() throws Exception {
        when(notaCreditoService.emitir(1L)).thenThrow(new IllegalArgumentException(
                "No se puede recuperar 5 de \"Bidón 20L\": el documento asociado solo tiene 2 disponible(s) "
                        + "para recuperar"));

        mockMvc.perform(post("/api/notas-credito/1/emitir").with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "NOTAS_CREDITO_EDITAR")
    void devuelve400CuandoElCuerpoDeLaNotaEsInvalido() throws Exception {
        // Sin ventaId, tipoCorreccion, fecha ni razón: la validación de bean lo
        // rechaza antes de llegar al servicio.
        mockMvc.perform(post("/api/notas-credito").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
