package cl.slimerp.usuarios;

import cl.slimerp.config.TenantContext;
import cl.slimerp.permisos.Permiso;
import cl.slimerp.permisos.UsuarioPermiso;
import cl.slimerp.permisos.UsuarioPermisoRepository;
import cl.slimerp.tenant.Rol;
import cl.slimerp.tenant.Usuario;
import cl.slimerp.tenant.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UsuarioPermisoControllerTest {

    private UsuarioRepository usuarioRepository;
    private UsuarioPermisoRepository usuarioPermisoRepository;
    private UsuarioPermisoController controller;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        usuarioPermisoRepository = mock(UsuarioPermisoRepository.class);
        controller = new UsuarioPermisoController(usuarioRepository, usuarioPermisoRepository);
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Usuario usuarioVendedor() {
        return Usuario.builder().id(3L).tenantId(1L).nombre("Vendedor Uno").email("v1@demo.cl")
                .rut("1-9").rol(Rol.VENDEDOR).activo(true).build();
    }

    @Test
    void obtenerLanzaExcepcionSiElUsuarioNoExisteEnElTenant() {
        when(usuarioRepository.findByIdAndTenantId(3L, 1L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> controller.obtener(3L));
    }

    @Test
    void obtenerDevuelveElRolYLosPermisosDelRolYLosExtra() {
        when(usuarioRepository.findByIdAndTenantId(3L, 1L)).thenReturn(Optional.of(usuarioVendedor()));
        UsuarioPermiso extra = UsuarioPermiso.builder().id(1L).tenantId(1L).usuarioId(3L)
                .permiso(Permiso.COMPRAS_VER).build();
        when(usuarioPermisoRepository.findByTenantIdAndUsuarioId(1L, 3L)).thenReturn(List.of(extra));

        PermisosUsuarioResponse response = controller.obtener(3L);

        assertEquals(Rol.VENDEDOR, response.rol());
        assertTrue(response.permisosRol().contains("CLIENTES_VER"));
        assertEquals(Set.of("COMPRAS_VER"), response.permisosExtra());
    }

    @Test
    void guardarExtraReemplazaLosPermisosExtraExistentes() {
        when(usuarioRepository.findByIdAndTenantId(3L, 1L)).thenReturn(Optional.of(usuarioVendedor()));

        var response = controller.guardarExtra(3L, new PermisosExtraRequest(Set.of(Permiso.COMPRAS_VER)));

        assertEquals(204, response.getStatusCode().value());
        verify(usuarioPermisoRepository).deleteByTenantIdAndUsuarioId(1L, 3L);
        verify(usuarioPermisoRepository).save(argThat(up ->
                up.getTenantId().equals(1L) && up.getUsuarioId().equals(3L) && up.getPermiso() == Permiso.COMPRAS_VER));
    }

    @Test
    void guardarExtraIgnoraSilenciosamenteUnPermisoQueYaDaElRol() {
        when(usuarioRepository.findByIdAndTenantId(3L, 1L)).thenReturn(Optional.of(usuarioVendedor()));

        controller.guardarExtra(3L, new PermisosExtraRequest(Set.of(Permiso.CLIENTES_VER, Permiso.COMPRAS_VER)));

        verify(usuarioPermisoRepository, times(1)).save(any());
        verify(usuarioPermisoRepository).save(argThat(up -> up.getPermiso() == Permiso.COMPRAS_VER));
    }

    @Test
    void guardarExtraLanzaExcepcionSiElUsuarioNoExisteEnElTenant() {
        when(usuarioRepository.findByIdAndTenantId(3L, 1L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> controller.guardarExtra(3L, new PermisosExtraRequest(Set.of(Permiso.COMPRAS_VER))));
    }
}
