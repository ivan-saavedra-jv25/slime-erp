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
import static org.mockito.ArgumentMatchers.anyList;
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

    private UsuarioPermiso extra(Long id, Permiso permiso) {
        return UsuarioPermiso.builder().id(id).tenantId(1L).usuarioId(3L).permiso(permiso).build();
    }

    @Test
    void obtenerDevuelve404SiElUsuarioNoExisteEnElTenant() {
        when(usuarioRepository.findByIdAndTenantId(3L, 1L)).thenReturn(Optional.empty());

        var response = controller.obtener(3L);

        assertEquals(404, response.getStatusCode().value());
        assertNull(response.getBody());
    }

    @Test
    void obtenerDevuelveElRolYLosPermisosDelRolYLosExtra() {
        when(usuarioRepository.findByIdAndTenantId(3L, 1L)).thenReturn(Optional.of(usuarioVendedor()));
        when(usuarioPermisoRepository.findByTenantIdAndUsuarioId(1L, 3L))
                .thenReturn(List.of(extra(1L, Permiso.COMPRAS_VER)));

        var response = controller.obtener(3L);

        assertEquals(200, response.getStatusCode().value());
        PermisosUsuarioResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(Rol.VENDEDOR, body.rol());
        assertTrue(body.permisosRol().contains("CLIENTES_VER"));
        assertEquals(Set.of("COMPRAS_VER"), body.permisosExtra());
    }

    @Test
    void guardarExtraReemplazaLosPermisosExtraExistentes() {
        when(usuarioRepository.findByIdAndTenantId(3L, 1L)).thenReturn(Optional.of(usuarioVendedor()));
        when(usuarioPermisoRepository.findByTenantIdAndUsuarioId(1L, 3L))
                .thenReturn(List.of(extra(7L, Permiso.PROVEEDORES_VER)));

        var response = controller.guardarExtra(3L, new PermisosExtraRequest(Set.of(Permiso.COMPRAS_VER)));

        assertEquals(204, response.getStatusCode().value());
        // El permiso que ya no se pide se borra, y solo ese.
        verify(usuarioPermisoRepository).deleteAll(argThat((List<UsuarioPermiso> borrados) ->
                borrados.size() == 1 && borrados.get(0).getPermiso() == Permiso.PROVEEDORES_VER));
        verify(usuarioPermisoRepository).save(argThat(up ->
                up.getTenantId().equals(1L) && up.getUsuarioId().equals(3L) && up.getPermiso() == Permiso.COMPRAS_VER));
        verify(usuarioPermisoRepository, times(1)).save(any());
    }

    @Test
    void guardarExtraIgnoraSilenciosamenteUnPermisoQueYaDaElRol() {
        when(usuarioRepository.findByIdAndTenantId(3L, 1L)).thenReturn(Optional.of(usuarioVendedor()));
        when(usuarioPermisoRepository.findByTenantIdAndUsuarioId(1L, 3L)).thenReturn(List.of());

        controller.guardarExtra(3L, new PermisosExtraRequest(Set.of(Permiso.CLIENTES_VER, Permiso.COMPRAS_VER)));

        verify(usuarioPermisoRepository, times(1)).save(any());
        verify(usuarioPermisoRepository).save(argThat(up -> up.getPermiso() == Permiso.COMPRAS_VER));
    }

    @Test
    void guardarExtraDosVecesSeguidasSoloInsertaElPermisoNuevo() {
        when(usuarioRepository.findByIdAndTenantId(3L, 1L)).thenReturn(Optional.of(usuarioVendedor()));

        // Primera llamada: el usuario no tiene permisos extra todavía.
        when(usuarioPermisoRepository.findByTenantIdAndUsuarioId(1L, 3L)).thenReturn(List.of());
        controller.guardarExtra(3L, new PermisosExtraRequest(Set.of(Permiso.COMPRAS_VER)));
        verify(usuarioPermisoRepository).save(argThat(up -> up.getPermiso() == Permiso.COMPRAS_VER));

        clearInvocations(usuarioPermisoRepository);

        // Segunda llamada: COMPRAS_VER ya está otorgado y se vuelve a enviar.
        when(usuarioPermisoRepository.findByTenantIdAndUsuarioId(1L, 3L))
                .thenReturn(List.of(extra(9L, Permiso.COMPRAS_VER)));

        var response = controller.guardarExtra(3L,
                new PermisosExtraRequest(Set.of(Permiso.COMPRAS_VER, Permiso.PROVEEDORES_VER)));

        assertEquals(204, response.getStatusCode().value());
        // La fila existente no se borra (conserva su fecha_creacion) ni se reinserta
        // (no choca con el UNIQUE (usuario_id, permiso)).
        verify(usuarioPermisoRepository, never()).deleteAll(anyList());
        verify(usuarioPermisoRepository, never()).deleteByTenantIdAndUsuarioId(any(), any());
        verify(usuarioPermisoRepository, times(1)).save(any());
        verify(usuarioPermisoRepository).save(argThat(up -> up.getPermiso() == Permiso.PROVEEDORES_VER));
    }

    @Test
    void guardarExtraConConjuntoVacioBorraTodosLosPermisosExtra() {
        when(usuarioRepository.findByIdAndTenantId(3L, 1L)).thenReturn(Optional.of(usuarioVendedor()));
        when(usuarioPermisoRepository.findByTenantIdAndUsuarioId(1L, 3L))
                .thenReturn(List.of(extra(1L, Permiso.COMPRAS_VER), extra(2L, Permiso.PROVEEDORES_VER)));

        var response = controller.guardarExtra(3L, new PermisosExtraRequest(Set.of()));

        assertEquals(204, response.getStatusCode().value());
        verify(usuarioPermisoRepository).deleteAll(argThat((List<UsuarioPermiso> borrados) -> borrados.size() == 2));
        verify(usuarioPermisoRepository, never()).save(any());
    }

    @Test
    void guardarExtraRechazaElPermisoDeAdministrarEmpresas() {
        when(usuarioRepository.findByIdAndTenantId(3L, 1L)).thenReturn(Optional.of(usuarioVendedor()));

        var ex = assertThrows(IllegalArgumentException.class, () -> controller.guardarExtra(3L,
                new PermisosExtraRequest(Set.of(Permiso.EMPRESAS_ADMINISTRAR, Permiso.COMPRAS_VER))));

        assertTrue(ex.getMessage().contains("EMPRESAS_ADMINISTRAR"));
        verify(usuarioPermisoRepository, never()).save(any());
        verify(usuarioPermisoRepository, never()).deleteAll(anyList());
    }

    @Test
    void guardarExtraDevuelve404SiElUsuarioNoExisteEnElTenant() {
        when(usuarioRepository.findByIdAndTenantId(3L, 1L)).thenReturn(Optional.empty());

        var response = controller.guardarExtra(3L, new PermisosExtraRequest(Set.of(Permiso.COMPRAS_VER)));

        assertEquals(404, response.getStatusCode().value());
        verify(usuarioPermisoRepository, never()).save(any());
        verify(usuarioPermisoRepository, never()).deleteAll(anyList());
    }
}
