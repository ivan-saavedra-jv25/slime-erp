package cl.slimerp.permisos;

import cl.slimerp.tenant.Rol;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RolPermisosTest {

    @Test
    void superAdminTieneTodosLosPermisos() {
        assertEquals(Set.of(Permiso.values()), RolPermisos.permisosDe(Rol.SUPER_ADMIN));
    }

    @Test
    void adminTieneClientesProductosCategoriasBodegasYUsuarios() {
        assertEquals(
                Set.of(Permiso.CLIENTES_VER, Permiso.CLIENTES_EDITAR,
                        Permiso.PRODUCTOS_VER, Permiso.PRODUCTOS_EDITAR,
                        Permiso.CATEGORIAS_VER, Permiso.CATEGORIAS_EDITAR,
                        Permiso.BODEGAS_VER, Permiso.BODEGAS_EDITAR,
                        Permiso.USUARIOS_VER, Permiso.USUARIOS_EDITAR),
                RolPermisos.permisosDe(Rol.ADMIN));
    }

    @Test
    void vendedorSoloVeProductosCategoriasYBodegasYGestionaClientes() {
        assertEquals(
                Set.of(Permiso.CLIENTES_VER, Permiso.CLIENTES_EDITAR,
                        Permiso.PRODUCTOS_VER, Permiso.CATEGORIAS_VER, Permiso.BODEGAS_VER),
                RolPermisos.permisosDe(Rol.VENDEDOR));
    }

    @Test
    void compradorGestionaProductosCategoriasYBodegas() {
        assertEquals(
                Set.of(Permiso.PRODUCTOS_VER, Permiso.PRODUCTOS_EDITAR,
                        Permiso.CATEGORIAS_VER, Permiso.CATEGORIAS_EDITAR,
                        Permiso.BODEGAS_VER, Permiso.BODEGAS_EDITAR),
                RolPermisos.permisosDe(Rol.COMPRADOR));
    }

    @Test
    void visualizadorSoloLee() {
        assertEquals(
                Set.of(Permiso.CLIENTES_VER, Permiso.PRODUCTOS_VER,
                        Permiso.CATEGORIAS_VER, Permiso.BODEGAS_VER),
                RolPermisos.permisosDe(Rol.VISUALIZADOR));
    }
}
