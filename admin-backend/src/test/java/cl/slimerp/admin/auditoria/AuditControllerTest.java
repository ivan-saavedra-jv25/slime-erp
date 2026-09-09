package cl.slimerp.admin.auditoria;

import cl.slimerp.admin.common.Paginated;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AuditControllerTest {

    private final AuditService service = mock(AuditService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = standaloneSetup(new AuditController(service)).build();
    }

    @Test
    void listarRetornaPaginaConFiltros() throws Exception {
        AuditLogResponse log = new AuditLogResponse(
                1L, 7L, "Admin Kim", "admin@slimerp.cl", 2L, "Empresa ABC",
                "PLAN_CHANGE", "suscripciones", "suscripcion", 3L,
                "{\"fecha\":\"2026-09-08\"}", "{\"fecha\":\"2026-10-08\"}",
                "127.0.0.1", "Angular", LocalDateTime.of(2026, 9, 8, 19, 30));
        when(service.listar(0, 20, 7L, 2L, "suscripciones", "PLAN_CHANGE", null, null))
                .thenReturn(new Paginated<>(List.of(log), 1, 1, 0, 20));

        mvc.perform(get("/api/admin/audit")
                        .param("adminUserId", "7")
                        .param("companyId", "2")
                        .param("modulo", "suscripciones")
                        .param("action", "PLAN_CHANGE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("PLAN_CHANGE"))
                .andExpect(jsonPath("$.content[0].adminNombre").value("Admin Kim"))
                .andExpect(jsonPath("$.content[0].companyNombre").value("Empresa ABC"))
                .andExpect(jsonPath("$.content[0].oldValue").value("{\"fecha\":\"2026-09-08\"}"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        verify(service).listar(0, 20, 7L, 2L, "suscripciones", "PLAN_CHANGE", null, null);
    }

    @Test
    void listarSinFiltrosUsaDefaults() throws Exception {
        when(service.listar(0, 20, null, null, null, null, null, null))
                .thenReturn(new Paginated<>(List.of(), 0, 0, 0, 20));

        mvc.perform(get("/api/admin/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        verify(service).listar(0, 20, null, null, null, null, null, null);
    }
}