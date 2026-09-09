package cl.slimerp.admin.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class PlanControllerTest {

    private final PlanService service = mock(PlanService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = standaloneSetup(new PlanController(service)).setValidator(validator).build();
    }

    @Test
    void crearConCamposCompletosDevuelvePlan() throws Exception {
        PlanResponse plan = new PlanResponse(1L, "Basico", "Para operaciones pequeñas",
                BigDecimal.valueOf(15990), BigDecimal.valueOf(159900), 3, 500,
                List.of("ventas", "compras"), List.of(), "ACTIVE");
        when(service.crear(any(PlanRequest.class))).thenReturn(plan);

        mvc.perform(post("/api/admin/planes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Basico\",\"precioMensual\":15990,\"precioAnual\":159900,\"maxUsuarios\":3,\"maxDocumentos\":500,\"modulos\":[\"ventas\",\"compras\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.modulos[0]").value("ventas"));
    }

    @Test
    void crearOmiteCamposObligatoriosDevuelve400() throws Exception {
        mvc.perform(post("/api/admin/planes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listarDevuelvePlanes() throws Exception {
        PlanResponse plan = new PlanResponse(1L, "Basico", "Desc", BigDecimal.ONE,
                BigDecimal.TEN, 3, 500, List.of("ventas"), List.of(), "ACTIVE");
        when(service.listar()).thenReturn(List.of(plan));

        mvc.perform(get("/api/admin/planes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Basico"))
                .andExpect(jsonPath("$[0].estado").value("ACTIVE"));
    }

    @Test
    void actualizarDevuelvePlanActualizado() throws Exception {
        PlanResponse plan = new PlanResponse(1L, "Basico", "Desc", BigDecimal.ONE,
                BigDecimal.TEN, 5, 600, List.of("ventas"), List.of(), "ACTIVE");
        when(service.actualizar(any(Long.class), any(PlanRequest.class))).thenReturn(plan);

        mvc.perform(put("/api/admin/planes/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Basico\",\"precioMensual\":15990,\"maxUsuarios\":5,\"maxDocumentos\":600,\"modulos\":[\"ventas\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxUsuarios").value(5));
    }

    @Test
    void cambiarEstadoLlamaAlServicio() throws Exception {
        PlanResponse plan = new PlanResponse(1L, "Basico", "Desc", BigDecimal.ONE,
                BigDecimal.TEN, 3, 500, List.of("ventas"), List.of(), "INACTIVE");
        when(service.cambiarEstado(1L, "INACTIVE")).thenReturn(plan);

        mvc.perform(patch("/api/admin/planes/1/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("INACTIVE"));

        verify(service).cambiarEstado(1L, "INACTIVE");
    }
}