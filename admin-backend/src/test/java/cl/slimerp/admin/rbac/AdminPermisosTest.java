package cl.slimerp.admin.rbac;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AdminPermisosTest {

    @Test
    void superAdminDebeContenerTodosLosPermisos() {
        Set<String> permisos = AdminPermisos.permisosDe(AdminRol.SUPER_ADMIN);
        assertTrue(permisos.contains("EMPRESAS_VER"));
        assertTrue(permisos.contains("EMPRESAS_EDITAR"));
        assertTrue(permisos.contains("PLANES_EDITAR"));
        assertTrue(permisos.contains("SUSCRIPCIONES_EDITAR"));
        assertTrue(permisos.contains("PAGOS_EDITAR"));
        assertTrue(permisos.contains("CONFIG_GLOBAL_EDITAR"));
        assertTrue(permisos.contains("AUDITORIA_VER"));
        assertTrue(permisos.contains("SESIONES_EDITAR"));
        assertTrue(permisos.contains("SISTEMA_VER"));
    }

    @Test
    void supportNoTienePermisosDePagosNiConfigGlobal() {
        Set<String> permisos = AdminPermisos.permisosDe(AdminRol.SUPPORT);
        assertFalse(permisos.contains("PAGOS_EDITAR"));
        assertFalse(permisos.contains("PAGOS_VER"));
        assertFalse(permisos.contains("CONFIG_GLOBAL_EDITAR"));
        assertFalse(permisos.contains("CONFIG_GLOBAL_VER"));
        assertFalse(permisos.contains("PLANES_EDITAR"));
    }

    @Test
    void supportVeSoporteYAlertas() {
        Set<String> permisos = AdminPermisos.permisosDe(AdminRol.SUPPORT);
        assertTrue(permisos.contains("SOPORTE_EDITAR"));
        assertTrue(permisos.contains("ALERTAS_VER"));
        assertTrue(permisos.contains("DTE_VER"));
        assertTrue(permisos.contains("SII_VER"));
    }

    @Test
    void auditorSoloLectura() {
        Set<String> permisos = AdminPermisos.permisosDe(AdminRol.AUDITOR);
        assertFalse(permisos.contains("EMPRESAS_EDITAR"));
        assertFalse(permisos.contains("PLANES_EDITAR"));
        assertFalse(permisos.contains("PAGOS_EDITAR"));
        assertFalse(permisos.contains("SUSCRIPCIONES_EDITAR"));
        assertFalse(permisos.contains("SOPORTE_EDITAR"));
        assertTrue(permisos.contains("AUDITORIA_VER"));
    }

    @Test
    void financeTienePermisosComercialesYNoDeSoporte() {
        Set<String> permisos = AdminPermisos.permisosDe(AdminRol.FINANCE);
        assertTrue(permisos.contains("PLANES_EDITAR"));
        assertTrue(permisos.contains("SUSCRIPCIONES_EDITAR"));
        assertTrue(permisos.contains("PAGOS_EDITAR"));
        assertFalse(permisos.contains("SOPORTE_EDITAR"));
        assertFalse(permisos.contains("ALERTAS_EDITAR"));
        assertFalse(permisos.contains("AUDITORIA_VER"));
    }

    @Test
    void adminTienePermisosDeOperacionBasica() {
        Set<String> permisos = AdminPermisos.permisosDe(AdminRol.ADMIN);
        assertTrue(permisos.contains("EMPRESAS_VER"));
        assertTrue(permisos.contains("USUARIOS_ADMIN_EDITAR"));
        assertTrue(permisos.contains("SOPORTE_EDITAR"));
        assertFalse(permisos.contains("EMPRESAS_EDITAR"));
        assertFalse(permisos.contains("PAGOS_EDITAR"));
        assertFalse(permisos.contains("CONFIG_GLOBAL_EDITAR"));
    }
}