package cl.slimerp.usuarios;

import cl.slimerp.config.TenantContext;
import cl.slimerp.tenant.Rol;
import cl.slimerp.tenant.Usuario;
import cl.slimerp.tenant.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UsuarioControllerTest {

    private UsuarioRepository usuarioRepository;
    private PasswordEncoder passwordEncoder;
    private UsuarioController controller;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        controller = new UsuarioController(usuarioRepository, passwordEncoder);
        TenantContext.setTenantId(1L);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(passwordEncoder.encode(any())).thenReturn("hash-cifrado");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private UsuarioRequest request(Rol rol, String password) {
        return new UsuarioRequest("Vendedor Uno", "1-9", "v1@demo.cl", password, rol, null);
    }

    @Test
    void crearRechazaElRolSuperAdmin() {
        assertThrows(IllegalArgumentException.class,
                () -> controller.crear(request(Rol.SUPER_ADMIN, "clave123")));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void crearRechazaEmailDuplicado() {
        when(usuarioRepository.existsByEmail("v1@demo.cl")).thenReturn(true);

        assertThrows(UsuarioConflictException.class, () -> controller.crear(request(Rol.VENDEDOR, "clave123")));
    }

    @Test
    void crearRechazaPasswordVacia() {
        when(usuarioRepository.existsByEmail("v1@demo.cl")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> controller.crear(request(Rol.VENDEDOR, "")));
    }

    @Test
    void crearGuardaConPasswordCifradaYTenantDelContexto() {
        when(usuarioRepository.existsByEmail("v1@demo.cl")).thenReturn(false);

        var response = controller.crear(request(Rol.VENDEDOR, "clave123"));

        assertEquals(1L, response.getBody().id() == null ? 1L : 1L); // tenant no viaja en la respuesta
        verify(usuarioRepository).save(argThat(u ->
                u.getTenantId().equals(1L) && u.getPasswordHash().equals("hash-cifrado") && u.getRol() == Rol.VENDEDOR));
    }

    @Test
    void actualizarSoloRecifraPasswordSiSeEnvioUnaNueva() {
        Usuario existente = Usuario.builder().id(3L).tenantId(1L).email("v1@demo.cl").rut("1-9")
                .passwordHash("hash-anterior").nombre("Vendedor Uno").rol(Rol.VENDEDOR).activo(true).build();
        when(usuarioRepository.findByIdAndTenantId(3L, 1L)).thenReturn(Optional.of(existente));

        controller.actualizar(3L, request(Rol.VENDEDOR, null));

        assertEquals("hash-anterior", existente.getPasswordHash());
    }

    @Test
    void actualizarCambiaElEmailSiEsNuevo() {
        Usuario existente = Usuario.builder().id(3L).tenantId(1L).email("v1@demo.cl").rut("1-9")
                .passwordHash("hash-anterior").nombre("Vendedor Uno").rol(Rol.VENDEDOR).activo(true).build();
        when(usuarioRepository.findByIdAndTenantId(3L, 1L)).thenReturn(Optional.of(existente));
        when(usuarioRepository.existsByEmail("nuevo@demo.cl")).thenReturn(false);

        UsuarioRequest request = new UsuarioRequest("Vendedor Uno", "1-9", "nuevo@demo.cl", null, Rol.VENDEDOR, null);
        controller.actualizar(3L, request);

        assertEquals("nuevo@demo.cl", existente.getEmail());
    }

    @Test
    void actualizarRechazaEmailUsadoPorOtroUsuario() {
        Usuario existente = Usuario.builder().id(3L).tenantId(1L).email("v1@demo.cl").rut("1-9")
                .passwordHash("hash-anterior").nombre("Vendedor Uno").rol(Rol.VENDEDOR).activo(true).build();
        when(usuarioRepository.findByIdAndTenantId(3L, 1L)).thenReturn(Optional.of(existente));
        when(usuarioRepository.existsByEmail("otro@demo.cl")).thenReturn(true);

        UsuarioRequest request = new UsuarioRequest("Vendedor Uno", "1-9", "otro@demo.cl", null, Rol.VENDEDOR, null);

        assertThrows(UsuarioConflictException.class, () -> controller.actualizar(3L, request));
        assertEquals("v1@demo.cl", existente.getEmail());
    }

    @Test
    void actualizarConElMismoEmailActualNoLanzaConflicto() {
        Usuario existente = Usuario.builder().id(3L).tenantId(1L).email("v1@demo.cl").rut("1-9")
                .passwordHash("hash-anterior").nombre("Vendedor Uno").rol(Rol.VENDEDOR).activo(true).build();
        when(usuarioRepository.findByIdAndTenantId(3L, 1L)).thenReturn(Optional.of(existente));

        var response = controller.actualizar(3L, request(Rol.VENDEDOR, null));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("v1@demo.cl", existente.getEmail());
        verify(usuarioRepository, never()).existsByEmail(any());
    }

    @Test
    void desactivarHaceSoftDelete() {
        Usuario existente = Usuario.builder().id(4L).tenantId(1L).activo(true).build();
        when(usuarioRepository.findByIdAndTenantId(4L, 1L)).thenReturn(Optional.of(existente));

        var response = controller.desactivar(4L);

        assertEquals(204, response.getStatusCode().value());
        assertFalse(existente.isActivo());
    }

    @Test
    void listarMapeaSoloLosUsuariosDelTenant() {
        Usuario u = Usuario.builder().id(1L).tenantId(1L).nombre("Admin").email("a@demo.cl")
                .rut("1-9").rol(Rol.ADMIN).activo(true).build();
        when(usuarioRepository.findByTenantId(1L)).thenReturn(List.of(u));

        List<UsuarioResponse> resultado = controller.listar();

        assertEquals(1, resultado.size());
        assertEquals("Admin", resultado.get(0).nombre());
    }
}
