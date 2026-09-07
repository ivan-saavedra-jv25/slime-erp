package cl.slimerp.auth;

import cl.slimerp.config.JwtService;
import cl.slimerp.permisos.PermisoEfectivoService;
import cl.slimerp.permisos.RolPermisos;
import cl.slimerp.tenant.Rol;
import cl.slimerp.tenant.Tenant;
import cl.slimerp.tenant.TenantRepository;
import cl.slimerp.tenant.Usuario;
import cl.slimerp.tenant.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthControllerTest {

    private UsuarioRepository usuarioRepository;
    private TenantRepository tenantRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private PermisoEfectivoService permisoEfectivoService;
    private AuthController authController;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        tenantRepository = mock(TenantRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        permisoEfectivoService = mock(PermisoEfectivoService.class);
        authController = new AuthController(usuarioRepository, tenantRepository, passwordEncoder, jwtService,
                permisoEfectivoService);
    }

    private Usuario usuario() {
        return Usuario.builder()
                .id(1L).tenantId(10L).email("user@demo.cl").passwordHash("hash")
                .rut("1.111.111-1").nombre("Usuario Demo").rol(Rol.VENDEDOR).activo(true)
                .build();
    }

    private LoginRequest request() {
        return new LoginRequest("user@demo.cl", "clave");
    }

    @Test
    void loginRechazaCuandoTenantEstaInactivo() {
        when(usuarioRepository.findFirstByEmailAndActivoTrue("user@demo.cl")).thenReturn(Optional.of(usuario()));
        when(passwordEncoder.matches("clave", "hash")).thenReturn(true);
        Tenant tenantInactivo = Tenant.builder().id(10L).nombre("Empresa X").rut("1-9").activo(false).build();
        when(tenantRepository.findById(10L)).thenReturn(Optional.of(tenantInactivo));

        assertThrows(BadCredentialsException.class, () -> authController.login(request()));
    }

    @Test
    void loginIncluyeLosPermisosEfectivosDelUsuario() {
        when(usuarioRepository.findFirstByEmailAndActivoTrue("user@demo.cl")).thenReturn(Optional.of(usuario()));
        when(passwordEncoder.matches("clave", "hash")).thenReturn(true);
        Tenant tenantActivo = Tenant.builder().id(10L).nombre("Empresa X").rut("1-9").activo(true).build();
        when(tenantRepository.findById(10L)).thenReturn(Optional.of(tenantActivo));
        when(jwtService.generarToken(1L, 10L, "user@demo.cl", "VENDEDOR")).thenReturn("token-123");
        when(permisoEfectivoService.calcular(10L, 1L, Rol.VENDEDOR)).thenReturn(RolPermisos.permisosDe(Rol.VENDEDOR));

        var response = authController.login(request());

        assertEquals(200, response.getStatusCode().value());
        assertEquals("token-123", response.getBody().token());
        Set<String> permisosEsperados = Set.of("CLIENTES_VER", "CLIENTES_EDITAR", "PRODUCTOS_VER", "CATEGORIAS_VER",
                "BODEGAS_VER", "FORMAS_PAGO_VER", "MOVIMIENTOS_VER", "MOVIMIENTOS_EDITAR",
                "VENTAS_VER", "VENTAS_EDITAR", "TESORERIA_VER", "TESORERIA_EDITAR");
        assertEquals(permisosEsperados, Set.copyOf(response.getBody().permisos()));
    }

    @Test
    void loginIncluyeLosPermisosExtraAdemasDeLosDelRol() {
        when(usuarioRepository.findFirstByEmailAndActivoTrue("user@demo.cl")).thenReturn(Optional.of(usuario()));
        when(passwordEncoder.matches("clave", "hash")).thenReturn(true);
        Tenant tenantActivo = Tenant.builder().id(10L).nombre("Empresa X").rut("1-9").activo(true).build();
        when(tenantRepository.findById(10L)).thenReturn(Optional.of(tenantActivo));
        when(jwtService.generarToken(1L, 10L, "user@demo.cl", "VENDEDOR")).thenReturn("token-123");
        Set<cl.slimerp.permisos.Permiso> conExtra = java.util.EnumSet.copyOf(RolPermisos.permisosDe(Rol.VENDEDOR));
        conExtra.add(cl.slimerp.permisos.Permiso.COMPRAS_VER);
        when(permisoEfectivoService.calcular(10L, 1L, Rol.VENDEDOR)).thenReturn(conExtra);

        var response = authController.login(request());

        assertTrue(response.getBody().permisos().contains("COMPRAS_VER"));
    }
}
