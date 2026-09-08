package cl.slimerp.admin.empresas;

import cl.slimerp.admin.common.Paginated;
import cl.slimerp.admin.usuarios.UsuarioAdminResponse;
import cl.slimerp.admin.usuarios.UsuarioAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class EmpresaAdminControllerTest {

    private final EmpresaAdminService service = mock(EmpresaAdminService.class);
    private final UsuarioAdminService usuarioAdminService = mock(UsuarioAdminService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = standaloneSetup(new EmpresaAdminController(service, usuarioAdminService)).setValidator(validator).build();
    }

    @Test
    void listarRetornaPaginaConFiltros() throws Exception {
        EmpresaResponse empresa = new EmpresaResponse(
                1L, "Empresa Demo", "76.123.456-7", null, "basico", "ACTIVE", true,
                LocalDateTime.now(), null, 3L, java.math.BigDecimal.ZERO);
        when(service.listar(0, 10, null, null, null, null, "ACTIVE", null, null))
                .thenReturn(new Paginated<>(List.of(empresa), 1, 1, 0, 10));

        mvc.perform(get("/api/admin/empresas").param("estado", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nombre").value("Empresa Demo"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        verify(service).listar(eq(0), eq(10), any(), any(), any(), any(), eq("ACTIVE"), any(), any());
    }

    @Test
    void detalleRetornaEmpresaConKpis() throws Exception {
        when(service.detalle(1L)).thenReturn(new EmpresaDetalleResponse(
                1L, "Empresa Demo", "76.123.456-7", null, "basico", "ACTIVE", true,
                null, null, 5L, 3L, java.math.BigDecimal.ZERO, "ACTIVE", 2L));

        mvc.perform(get("/api/admin/empresas/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuariosTotales").value(5))
                .andExpect(jsonPath("$.alertasAbiertas").value(2));
    }

    @Test
    void usuariosDeEmpresaRetornaLista() throws Exception {
        UsuarioAdminResponse u = new UsuarioAdminResponse(
                1L, 1L, "Empresa Demo", "Juan Pérez", "11.222.333-4", "juan@empresa.cl",
                cl.slimerp.admin.tenant.Rol.ADMIN, true, LocalDateTime.now());
        when(usuarioAdminService.listar(1L, null)).thenReturn(List.of(u));

        mvc.perform(get("/api/admin/empresas/1/usuarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Juan Pérez"))
                .andExpect(jsonPath("$[0].tenantId").value(1));
    }

    @Test
    void cambiarEstadoValidoActualizaStatus() throws Exception {
        when(service.cambiarEstado(1L, EstadoEmpresa.SUSPENDED, "Impago"))
                .thenReturn(org.mockito.Mockito.mock(cl.slimerp.admin.tenant.Tenant.class));
        when(service.conKpis(any())).thenReturn(new EmpresaResponse(
                1L, "Empresa Demo", "76.123.456-7", null, "basico", "SUSPENDED", false,
                null, null, 3L, java.math.BigDecimal.ZERO));

        mvc.perform(patch("/api/admin/empresas/1/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"SUSPENDED\",\"motivo\":\"Impago\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    void cambiarEstadoSinMotivoRetorna400() throws Exception {
        when(service.cambiarEstado(1L, EstadoEmpresa.SUSPENDED, " "))
                .thenReturn(new cl.slimerp.admin.tenant.Tenant());

        mvc.perform(patch("/api/admin/empresas/1/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"SUSPENDED\",\"motivo\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crearEmpresaRetornaRespuestaCompleta() throws Exception {
        when(service.crear(any())).thenReturn(cl.slimerp.admin.tenant.Tenant.builder()
                .id(1L).nombre("Empresa Nueva").rut("1.111.111-1").plan("basico").build());

        mvc.perform(post("/api/admin/empresas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Empresa Nueva","rut":"1.111.111-1","plan":"basico",
                                 "adminNombre":"Admin","adminRut":"2.222.222-2",
                                 "adminEmail":"admin@nueva.cl","adminPassword":"clave123"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Empresa Nueva"));
    }

    @Test
    void crearEmpresaConDatosIncompletosRetorna400() throws Exception {
        mvc.perform(post("/api/admin/empresas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}