package cl.slimerp.notasdebito;

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

@WebMvcTest(NotaDebitoController.class)
@Import(NotaDebitoControllerPermissionTest.MethodSecurityTestConfig.class)
class NotaDebitoControllerPermissionTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotaDebitoService notaDebitoService;

    @MockBean
    private NotaDebitoPdfService notaDebitoPdfService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private PermisoEfectivoService permisoEfectivoService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private NotaDebitoService.NotaDebitoCompleta notaVacia() {
        return new NotaDebitoService.NotaDebitoCompleta(
                1L, 1, "ND-000001", EstadoNotaDebito.BORRADOR, TipoReversion.REVIERTE_MONTO,
                LocalDate.of(2026, 9, 23),
                5L, "Empresa ABC SpA", null, "76.111.222-3", null, null, null,
                7L, "Vendedor Demo",
                new NotaDebitoService.DocumentoAsociado(90L, "NC-000090",
                        LocalDate.of(2026, 9, 15), 90, TipoDocumentoVenta.FACTURA,
                        new BigDecimal("12852"), new BigDecimal("12852"),
                        "Devolución rechazada"),
                null, null, null,
                3L, "Bodega Central", false, "CLP",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, ImpactoInventario.NO,
                List.of(), List.of(), List.of());
    }

    @Test
    @WithMockUser(authorities = "NOTAS_DEBITO_VER")
    void permiteListarConElPermisoDeLectura() throws Exception {
        when(notaDebitoService.buscar(any(), any(), any(), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt())).thenReturn(new PaginaResponse<>(List.of(), 0));

        mockMvc.perform(get("/api/notas-debito")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "NOTAS_VENTA_VER")
    void rechazaListarSinElPermisoCorrecto() throws Exception {
        mockMvc.perform(get("/api/notas-debito")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "NOTAS_DEBITO_VER")
    void permiteConsultarLaCadenaDeDocumentosAsociados() throws Exception {
        when(notaDebitoService.cadenaDe(1L)).thenReturn(List.of(
                new NotaDebitoService.EslabonCadena("COTIZACION", 70L, "COT-000006",
                        LocalDate.of(2026, 9, 8), new BigDecimal("12852.00"))));

        mockMvc.perform(get("/api/notas-debito/1/cadena")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "NOTAS_VENTA_VER")
    void rechazaConsultarLaCadenaSinElPermisoDeNotasDeDebito() throws Exception {
        mockMvc.perform(get("/api/notas-debito/1/cadena")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "NOTAS_DEBITO_VER")
    void permiteConsultarLasLineasDeLaNotaDeCreditoAsociada() throws Exception {
        when(notaDebitoService.lineasNotaCredito(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/notas-debito/notas-credito/90/lineas")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "NOTAS_DEBITO_VER")
    void elPermisoDeLecturaNoAlcanzaParaEmitir() throws Exception {
        mockMvc.perform(post("/api/notas-debito/1/emitir").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "NOTAS_DEBITO_VER")
    void elPermisoDeLecturaNoAlcanzaParaAnular() throws Exception {
        mockMvc.perform(post("/api/notas-debito/1/anular").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "NOTAS_DEBITO_EDITAR")
    void permiteEmitirConElPermisoDeEdicion() throws Exception {
        when(notaDebitoService.emitir(1L)).thenReturn(notaVacia());

        mockMvc.perform(post("/api/notas-debito/1/emitir").with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "NOTAS_DEBITO_EDITAR")
    void devuelve400CuandoLaTransicionNoEsValida() throws Exception {
        when(notaDebitoService.emitir(1L)).thenThrow(new IllegalArgumentException(
                "No se puede emitir una nota de débito en estado EMITIDA"));

        mockMvc.perform(post("/api/notas-debito/1/emitir").with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "NOTAS_DEBITO_EDITAR")
    void devuelve400CuandoElMontoSuperaElDisponibleDeLaNotaDeCredito() throws Exception {
        when(notaDebitoService.emitir(1L)).thenThrow(new IllegalArgumentException(
                "El monto de la nota de débito (2380) supera el disponible de la nota de crédito (852)"));

        mockMvc.perform(post("/api/notas-debito/1/emitir").with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "NOTAS_DEBITO_EDITAR")
    void devuelve400CuandoElCuerpoDeLaNotaEsInvalido() throws Exception {
        // Sin notaCreditoId, tipoReversion, fecha ni razón: la validación de bean
        // lo rechaza antes de llegar al servicio.
        mockMvc.perform(post("/api/notas-debito").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}