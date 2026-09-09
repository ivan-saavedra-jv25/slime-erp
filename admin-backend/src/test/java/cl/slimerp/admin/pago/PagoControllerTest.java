package cl.slimerp.admin.pago;

import cl.slimerp.admin.cobranza.MedioPago;
import cl.slimerp.admin.common.Paginated;
import cl.slimerp.admin.config.UsuarioActual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class PagoControllerTest {

    private final PagoService service = mock(PagoService.class);
    private final UsuarioActual usuarioActual = mock(UsuarioActual.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(usuarioActual.id()).thenReturn(5L);
        mvc = standaloneSetup(new PagoController(service, usuarioActual)).build();
    }

    private PagoResponse pago() {
        return new PagoResponse(1L, 2L, 1L, "Empresa ABC", null,
                new BigDecimal("50000"), "TRANSFERENCIA", "PAID", "REF-001",
                LocalDateTime.of(2026, 9, 8, 11, 0), "Admin Uno");
    }

    @Test
    void listarRetornaPaginaConFiltros() throws Exception {
        when(service.listar(0, 20, 1L, "PAID"))
                .thenReturn(new Paginated<>(List.of(pago()), 1, 1, 0, 20));

        mvc.perform(get("/api/admin/pagos")
                        .param("empresaId", "1")
                        .param("estado", "PAID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].companyNombre").value("Empresa ABC"))
                .andExpect(jsonPath("$.content[0].estado").value("PAID"))
                .andExpect(jsonPath("$.content[0].monto").value(50000));

        verify(service).listar(0, 20, 1L, "PAID");
    }

    @Test
    void obtenerRetornaPago() throws Exception {
        when(service.obtener(1L)).thenReturn(pago());

        mvc.perform(get("/api/admin/pagos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.referencia").value("REF-001"));

        verify(service).obtener(1L);
    }

    @Test
    void registrarDelegaAlService() throws Exception {
        when(service.registrarManual(
                new RegistrarPagoManualRequest(1L, null, new BigDecimal("50000"), MedioPago.TRANSFERENCIA, "REF-001", null),
                5L)).thenReturn(pago());

        mvc.perform(post("/api/admin/pagos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"companyId\": 1, \"monto\": 50000, \"metodo\": \"TRANSFERENCIA\", "
                                + "\"referencia\": \"REF-001\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PAID"));

        verify(service).registrarManual(
                new RegistrarPagoManualRequest(1L, null, new BigDecimal("50000"), MedioPago.TRANSFERENCIA, "REF-001", null),
                5L);
    }
}