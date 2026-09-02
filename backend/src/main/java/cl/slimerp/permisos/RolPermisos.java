package cl.slimerp.permisos;

import cl.slimerp.tenant.Rol;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Mapeo fijo Rol -> Permisos. No configurable por tenant en esta etapa
 * (ver spec: docs/superpowers/specs/2026-08-30-scaffold-desde-slime-erp-old-design.md).
 */
public final class RolPermisos {

    private static final Map<Rol, Set<Permiso>> MAPA = new EnumMap<>(Rol.class);

    static {
        MAPA.put(Rol.SUPER_ADMIN, EnumSet.allOf(Permiso.class));
        MAPA.put(Rol.ADMIN, EnumSet.of(
                Permiso.CLIENTES_VER, Permiso.CLIENTES_EDITAR,
                Permiso.PROVEEDORES_VER, Permiso.PROVEEDORES_EDITAR,
                Permiso.PRODUCTOS_VER, Permiso.PRODUCTOS_EDITAR,
                Permiso.CATEGORIAS_VER, Permiso.CATEGORIAS_EDITAR,
                Permiso.BODEGAS_VER, Permiso.BODEGAS_EDITAR,
                Permiso.FORMAS_PAGO_VER, Permiso.FORMAS_PAGO_EDITAR,
                Permiso.MOVIMIENTOS_VER, Permiso.MOVIMIENTOS_EDITAR,
                Permiso.VENTAS_VER, Permiso.VENTAS_EDITAR,
                Permiso.COMPRAS_VER, Permiso.COMPRAS_EDITAR,
                Permiso.USUARIOS_VER, Permiso.USUARIOS_EDITAR));
        MAPA.put(Rol.VENDEDOR, EnumSet.of(
                Permiso.CLIENTES_VER, Permiso.CLIENTES_EDITAR,
                Permiso.PRODUCTOS_VER, Permiso.CATEGORIAS_VER, Permiso.BODEGAS_VER,
                Permiso.FORMAS_PAGO_VER,
                Permiso.MOVIMIENTOS_VER, Permiso.MOVIMIENTOS_EDITAR,
                Permiso.VENTAS_VER, Permiso.VENTAS_EDITAR));
        MAPA.put(Rol.COMPRADOR, EnumSet.of(
                Permiso.PROVEEDORES_VER, Permiso.PROVEEDORES_EDITAR,
                Permiso.PRODUCTOS_VER, Permiso.PRODUCTOS_EDITAR,
                Permiso.CATEGORIAS_VER, Permiso.CATEGORIAS_EDITAR,
                Permiso.BODEGAS_VER, Permiso.BODEGAS_EDITAR,
                Permiso.MOVIMIENTOS_VER, Permiso.MOVIMIENTOS_EDITAR,
                Permiso.COMPRAS_VER, Permiso.COMPRAS_EDITAR));
        MAPA.put(Rol.VISUALIZADOR, EnumSet.of(
                Permiso.CLIENTES_VER, Permiso.PROVEEDORES_VER, Permiso.PRODUCTOS_VER,
                Permiso.CATEGORIAS_VER, Permiso.BODEGAS_VER,
                Permiso.FORMAS_PAGO_VER, Permiso.MOVIMIENTOS_VER, Permiso.VENTAS_VER,
                Permiso.COMPRAS_VER));
    }

    private RolPermisos() {
    }

    public static Set<Permiso> permisosDe(Rol rol) {
        return MAPA.getOrDefault(rol, Set.of());
    }
}
