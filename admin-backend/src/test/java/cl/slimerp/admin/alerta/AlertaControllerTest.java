package cl.slimerp.admin.alerta;

import cl.slimerp.admin.common.Paginated;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AlertaControllerTest {

    private final AlertaService service = mock(AlertaService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = standaloneSetup(new AlertaController(service)).build();
    }

    private AlertaResponse alerta() {
        return new AlertaResponse(1L, 2L, "Empresa ABC", "CERTIFICATE_EXPIRED", "CRITICAL",
                "Certificado vencido", "El certificado venció",
                LocalDateTime.of(2026, 9, 8, 10, 0), null, null, "OPEN");
    }

    @Test
    void listarRetornaPaginaConFiltros() throws Exception {
        when(service.listar(0, 20, "CRITICAL", "OPEN", 2L, "CERTIFICATE_EXPIRED"))
                .thenReturn(new Paginated<>(List.of(alerta()), 1, 1, 0, 20));

        mvc.perform(get("/api/admin/alerts")
                        .param("severity", "CRITICAL")
                        .param("status", "OPEN")
                        .param("companyId", "2")
                        .param("tipo", "CERTIFICATE_EXPIRED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].severity").value("CRITICAL"))
                .andExpect(jsonPath("$.content[0].status").value("OPEN"))
                .andExpect(jsonPath("$.content[0].type").value("CERTIFICATE_EXPIRED"))
                .andExpect(jsonPath("$.content[0].companyNombre").value("Empresa ABC"));

        verify(service).listar(0, 20, "CRITICAL", "OPEN", 2L, "CERTIFICATE_EXPIRED");
    }

    @Test
    void summaryRetornaConteos() throws Exception {
        when(service.resumenAbiertas()).thenReturn(Map.of("critical", 2L, "warning", 5L, "info", 1L));

        mvc.perform(get("/api/admin/alerts/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.critical").value(2))
                .andExpect(jsonPath("$.warning").value(5))
                .andExpect(jsonPath("$.info").value(1));
    }

    @Test
    void marcarLeidaDelegaAlService() throws Exception {
        when(service.marcarLeida(1L)).thenReturn(alerta());

        mvc.perform(patch("/api/admin/alerts/1/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));

        verify(service).marcarLeida(1L);
    }

    @Test
    void resolverSinBodyEsValido() throws Exception {
        when(service.resolver(1L, null)).thenReturn(alerta());

        mvc.perform(post("/api/admin/alerts/1/resolve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        verify(service).resolver(1L, null);
    }

    @Test
    void resolverConMotivoLoTraspasa() throws Exception {
        when(service.resolver(1L, "Se renovó")).thenReturn(alerta());

        mvc.perform(post("/api/admin/alerts/1/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Se renovó\"}"))
                .andExpect(status().isOk());

        verify(service).resolver(1L, "Se renovó");
    }
}