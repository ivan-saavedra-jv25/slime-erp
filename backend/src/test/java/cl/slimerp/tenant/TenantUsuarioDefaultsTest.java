package cl.slimerp.tenant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TenantUsuarioDefaultsTest {

    @Test
    void tenantUsaPlanBasicoYActivoPorDefecto() {
        Tenant tenant = Tenant.builder().nombre("Empresa Demo").rut("76.123.456-7").build();

        assertEquals("basico", tenant.getPlan());
        assertTrue(tenant.isActivo());
        assertNotNull(tenant.getFechaAlta());
    }

    @Test
    void usuarioUsaRolAdminYActivoPorDefecto() {
        Usuario usuario = Usuario.builder()
                .tenantId(1L).email("admin@demo.cl").rut("1-9").passwordHash("hash").nombre("Admin")
                .build();

        assertEquals(Rol.ADMIN, usuario.getRol());
        assertTrue(usuario.isActivo());
        assertNotNull(usuario.getFechaCreacion());
    }
}
