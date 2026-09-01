package cl.slimerp.catalogo;

import cl.slimerp.config.JwtService;
import cl.slimerp.config.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClienteController.class)
@Import(ClienteControllerPermissionTest.MethodSecurityTestConfig.class)
class ClienteControllerPermissionTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClienteRepository clienteRepository;

    @MockBean
    private JwtService jwtService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @WithMockUser(authorities = "CLIENTES_VER")
    void permiteListarConElPermisoCorrecto() throws Exception {
        TenantContext.setTenantId(1L);
        when(clienteRepository.findByTenantIdAndActivoTrue(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/clientes"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "PRODUCTOS_VER")
    void rechazaListarSinElPermisoCorrecto() throws Exception {
        mockMvc.perform(get("/api/clientes"))
                .andExpect(status().isForbidden());
    }
}
