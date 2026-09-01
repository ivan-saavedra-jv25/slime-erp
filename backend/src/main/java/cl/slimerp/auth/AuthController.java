package cl.slimerp.auth;

import cl.slimerp.config.JwtService;
import cl.slimerp.permisos.Permiso;
import cl.slimerp.permisos.RolPermisos;
import cl.slimerp.tenant.Tenant;
import cl.slimerp.tenant.TenantRepository;
import cl.slimerp.tenant.Usuario;
import cl.slimerp.tenant.UsuarioRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UsuarioRepository usuarioRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(UsuarioRepository usuarioRepository, TenantRepository tenantRepository,
                           PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        Usuario usuario = usuarioRepository.findFirstByEmailAndActivoTrue(request.email())
                .orElseThrow(() -> new BadCredentialsException("Credenciales inválidas"));

        if (!passwordEncoder.matches(request.password(), usuario.getPasswordHash())) {
            throw new BadCredentialsException("Credenciales inválidas");
        }

        Tenant tenant = tenantRepository.findById(usuario.getTenantId())
                .orElseThrow(() -> new BadCredentialsException("Credenciales inválidas"));

        if (!tenant.isActivo()) {
            throw new BadCredentialsException("Credenciales inválidas");
        }

        String token = jwtService.generarToken(
                usuario.getId(), usuario.getTenantId(), usuario.getEmail(), usuario.getRol().name());

        var permisos = RolPermisos.permisosDe(usuario.getRol()).stream().map(Permiso::name).toList();

        return ResponseEntity.ok(new LoginResponse(
                token, usuario.getId(), usuario.getTenantId(), tenant.getNombre(), usuario.getNombre(),
                usuario.getEmail(), usuario.getRut(), usuario.getRol().name(), permisos));
    }
}
