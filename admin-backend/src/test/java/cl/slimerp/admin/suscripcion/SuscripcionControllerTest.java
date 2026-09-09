package cl.slimerp.admin.suscripcion;

import cl.slimerp.admin.common.Paginated;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class SuscripcionControllerTest {

    private final SuscripcionService service = mock(SuscripcionService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = standaloneSetup(new SuscripcionController(service)).setValidator(validator).build();
    }

    private SuscripcionResponse suscripcion() {
        return new SuscripcionResponse(1L, 1L, "Empresa Demo", 2L, "Profesional", "ACTIVE",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), "MONTHLY",
                new BigDecimal("29990"), 7);
    }

    @Test
    void listarConFiltrosDevuelvePagina() throws Exception {
        when(service.listar(0, 20, "ACTIVE", 2L, 1L, 30))
                .thenReturn(new Paginated<>(List.of(suscripcion()), 1, 1, 0, 20));

        mvc.perform(get("/api/admin/suscripciones")
                        .param("estado", "ACTIVE")
                        .param("planId", "2")
                        .param("empresaId", "1")
                        .param("proximasAVencerDias", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].planNombre").value("Profesional"))
                .andExpect(jsonPath("$.content[0].empresaNombre").value("Empresa Demo"));
    }

    @Test
    void obtenerDevuelveSuscripcion() throws Exception {
        when(service.obtener(1L)).thenReturn(suscripcion());

        mvc.perform(get("/api/admin/suscripciones/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.fechaVencimiento[0]").value(2026))
                .andExpect(jsonPath("$.fechaVencimiento[1]").value(2));
    }

    @Test
    void crearConCamposCompletosDevuelveSuscripcion() throws Exception {
        when(service.crear(any(SuscripcionRequest.class))).thenReturn(suscripcion());

        mvc.perform(post("/api/admin/suscripciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyId\":1,\"planId\":2,\"fechaInicio\":\"2026-01-01\"," +
                                "\"fechaVencimiento\":\"2026-02-01\",\"cicloFacturacion\":\"MONTHLY\"," +
                                "\"precio\":29990}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.planNombre").value("Profesional"));
    }

    @Test
    void crearOmiteCamposObligatoriosDevuelve400() throws Exception {
        mvc.perform(post("/api/admin/suscripciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cicloFacturacion\":\"MONTHLY\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void extenderLlamaAlServicio() throws Exception {
        when(service.extender(any(Long.class), any(ExtenderSuscripcionRequest.class))).thenReturn(suscripcion());

        mvc.perform(post("/api/admin/suscripciones/1/extend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevoVencimiento\":\"2026-03-01\",\"motivo\":\"Cortesía\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void cambiarPlanLlamaAlServicio() throws Exception {
        when(service.cambiarPlan(any(Long.class), any(CambiarPlanRequest.class))).thenReturn(suscripcion());

        mvc.perform(post("/api/admin/suscripciones/1/cambiar-plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":3,\"motivo\":\"Crecimiento\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planNombre").value("Profesional"));

        verify(service).cambiarPlan(1L, new CambiarPlanRequest(3L, "Crecimiento"));
    }

    @Test
    void suspenderSinMotivoDevuelve400() throws Exception {
        mvc.perform(post("/api/admin/suscripciones/1/suspend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void suspenderConMotivoLlamaAlServicio() throws Exception {
        when(service.suspender(any(Long.class), any(String.class))).thenReturn(suscripcion());

        mvc.perform(post("/api/admin/suscripciones/1/suspend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Mora de pago\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVE"));

        verify(service).suspender(1L, "Mora de pago");
    }

    @Test
    void reactivarLlamaAlServicio() throws Exception {
        when(service.reactivar(any(Long.class), any(String.class))).thenReturn(suscripcion());

        mvc.perform(post("/api/admin/suscripciones/1/reactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Pago recibido\"}"))
                .andExpect(status().isOk());

        verify(service).reactivar(1L, "Pago recibido");
    }
}