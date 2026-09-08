package cl.slimerp.admin.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AdminAuthControllerTest {

    private final AdminAuthService service = mock(AdminAuthService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = standaloneSetup(new AdminAuthController(service)).setValidator(validator).build();
    }

    @Test
    void loginRetornaTokenConDatosAdmin() throws Exception {
        when(service.login("super@slimerp.cl", "clave", "127.0.0.1", null))
                .thenReturn(new AdminAuthResponse(
                        "jwt-token", 10L, "Super", "super@slimerp.cl", "SUPER_ADMIN",
                        List.of("EMPRESAS_VER", "SESIONES_VER")));

        mvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"super@slimerp.cl\",\"password\":\"clave\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.adminId").value(10))
                .andExpect(jsonPath("$.adminRol").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.permisos[0]").value("EMPRESAS_VER"));
    }

    @Test
    void loginConDatosInvalidosRetorna400() throws Exception {
        mvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"invalido\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logoutRevocaLaSesion() throws Exception {
        mvc.perform(post("/api/admin/auth/logout")
                        .header("Authorization", "Bearer tok"))
                .andExpect(status().isNoContent());

        verify(service).logout("tok");
    }
}