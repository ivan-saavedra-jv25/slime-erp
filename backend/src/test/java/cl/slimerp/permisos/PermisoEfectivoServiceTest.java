package cl.slimerp.permisos;

import cl.slimerp.tenant.Rol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PermisoEfectivoServiceTest {

    private UsuarioPermisoRepository usuarioPermisoRepository;
    private PermisoEfectivoService service;

    @BeforeEach
    void setUp() {
        usuarioPermisoRepository = mock(UsuarioPermisoRepository.class);
        service = new PermisoEfectivoService(usuarioPermisoRepository);
    }

    @Test
    void sinPermisosExtraDevuelveSoloLosDelRol() {
        when(usuarioPermisoRepository.findByTenantIdAndUsuarioId(1L, 5L)).thenReturn(List.of());

        Set<Permiso> resultado = service.calcular(1L, 5L, Rol.VISUALIZADOR);

        assertEquals(RolPermisos.permisosDe(Rol.VISUALIZADOR), resultado);
    }

    @Test
    void sumaLosPermisosExtraDelUsuarioALosDelRol() {
        UsuarioPermiso extra = UsuarioPermiso.builder().id(1L).tenantId(1L).usuarioId(5L)
                .permiso(Permiso.COMPRAS_VER).build();
        when(usuarioPermisoRepository.findByTenantIdAndUsuarioId(1L, 5L)).thenReturn(List.of(extra));

        Set<Permiso> resultado = service.calcular(1L, 5L, Rol.VENDEDOR);

        assertTrue(resultado.contains(Permiso.COMPRAS_VER));
        assertTrue(resultado.containsAll(RolPermisos.permisosDe(Rol.VENDEDOR)));
        assertEquals(RolPermisos.permisosDe(Rol.VENDEDOR).size() + 1, resultado.size());
    }

    @Test
    void unPermisoExtraQueElRolYaTeniaNoDuplica() {
        UsuarioPermiso extra = UsuarioPermiso.builder().id(1L).tenantId(1L).usuarioId(5L)
                .permiso(Permiso.CLIENTES_VER).build();
        when(usuarioPermisoRepository.findByTenantIdAndUsuarioId(1L, 5L)).thenReturn(List.of(extra));

        Set<Permiso> resultado = service.calcular(1L, 5L, Rol.VENDEDOR);

        assertEquals(RolPermisos.permisosDe(Rol.VENDEDOR).size(), resultado.size());
    }
}
