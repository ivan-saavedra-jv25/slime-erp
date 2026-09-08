package cl.slimerp.admin.rbac;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/** Mapeo fijo rol admin a permisos (mismo patrón que RolPermisos del backend de negocio). */
public final class AdminPermisos {

    private static final Map<AdminRol, Set<String>> POR_ROL = new EnumMap<>(AdminRol.class);

    static {
        POR_ROL.put(AdminRol.SUPER_ADMIN, Set.of(
                "EMPRESAS_VER", "EMPRESAS_EDITAR", "USUARIOS_ADMIN_VER", "USUARIOS_ADMIN_EDITAR",
                "PLANES_VER", "PLANES_EDITAR", "SUSCRIPCIONES_VER", "SUSCRIPCIONES_EDITAR",
                "PAGOS_VER", "PAGOS_EDITAR", "ALERTAS_VER", "ALERTAS_EDITAR", "SOPORTE_VER",
                "SOPORTE_EDITAR", "AUDITORIA_VER", "CONFIG_GLOBAL_VER", "CONFIG_GLOBAL_EDITAR",
                "DTE_VER", "SII_VER", "SISTEMA_VER", "SESIONES_VER", "SESIONES_EDITAR"));
        POR_ROL.put(AdminRol.ADMIN, Set.of(
                "EMPRESAS_VER", "USUARIOS_ADMIN_VER", "USUARIOS_ADMIN_EDITAR", "SOPORTE_VER",
                "SOPORTE_EDITAR", "ALERTAS_VER", "AUDITORIA_VER"));
        POR_ROL.put(AdminRol.SUPPORT, Set.of(
                "EMPRESAS_VER", "USUARIOS_ADMIN_VER", "ALERTAS_VER", "SOPORTE_VER", "SOPORTE_EDITAR",
                "DTE_VER", "SII_VER"));
        POR_ROL.put(AdminRol.FINANCE, Set.of(
                "EMPRESAS_VER", "PLANES_VER", "PLANES_EDITAR", "SUSCRIPCIONES_VER",
                "SUSCRIPCIONES_EDITAR", "PAGOS_VER", "PAGOS_EDITAR"));
        POR_ROL.put(AdminRol.AUDITOR, Set.of(
                "EMPRESAS_VER", "USUARIOS_ADMIN_VER", "SUSCRIPCIONES_VER", "PAGOS_VER",
                "AUDITORIA_VER", "ALERTAS_VER", "DTE_VER", "SII_VER"));
    }

    private AdminPermisos() {
    }

    public static Set<String> permisosDe(AdminRol rol) {
        Set<String> permisos = POR_ROL.get(rol);
        if (permisos == null) {
            throw new IllegalArgumentException("Rol admin desconocido: " + rol);
        }
        return permisos;
    }
}