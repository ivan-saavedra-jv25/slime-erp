package cl.slimerp.usuarios;

import cl.slimerp.config.TenantContext;
import cl.slimerp.tenant.Rol;
import cl.slimerp.tenant.Usuario;
import cl.slimerp.tenant.UsuarioRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioController(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USUARIOS_VER')")
    public List<UsuarioResponse> listar() {
        return usuarioRepository.findByTenantId(TenantContext.getTenantId()).stream()
                .map(UsuarioResponse::desde)
                .toList();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USUARIOS_EDITAR')")
    public ResponseEntity<UsuarioResponse> crear(@Valid @RequestBody UsuarioRequest request) {
        validarRolAsignable(request.rol());
        if (usuarioRepository.existsByEmail(request.email())) {
            throw new UsuarioConflictException("Ya existe un usuario con el email " + request.email());
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new IllegalArgumentException("La contraseña es obligatoria al crear un usuario");
        }

        Usuario usuario = Usuario.builder()
                .tenantId(TenantContext.getTenantId())
                .nombre(request.nombre())
                .rut(request.rut())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .rol(request.rol())
                .activo(request.activo() == null || request.activo())
                .build();
        return ResponseEntity.ok(UsuarioResponse.desde(usuarioRepository.save(usuario)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USUARIOS_EDITAR')")
    public ResponseEntity<UsuarioResponse> actualizar(@PathVariable Long id, @Valid @RequestBody UsuarioRequest request) {
        validarRolAsignable(request.rol());
        return usuarioRepository.findByIdAndTenantId(id, TenantContext.getTenantId())
                .map(usuario -> {
                    if (!usuario.getEmail().equals(request.email()) && usuarioRepository.existsByEmail(request.email())) {
                        throw new UsuarioConflictException("Ya existe un usuario con el email " + request.email());
                    }
                    usuario.setEmail(request.email());
                    usuario.setNombre(request.nombre());
                    usuario.setRut(request.rut());
                    usuario.setRol(request.rol());
                    if (request.activo() != null) {
                        usuario.setActivo(request.activo());
                    }
                    if (request.password() != null && !request.password().isBlank()) {
                        usuario.setPasswordHash(passwordEncoder.encode(request.password()));
                    }
                    return ResponseEntity.ok(UsuarioResponse.desde(usuarioRepository.save(usuario)));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USUARIOS_EDITAR')")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        return usuarioRepository.findByIdAndTenantId(id, TenantContext.getTenantId())
                .map(usuario -> {
                    usuario.setActivo(false);
                    usuarioRepository.save(usuario);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private void validarRolAsignable(Rol rol) {
        if (rol == Rol.SUPER_ADMIN) {
            throw new IllegalArgumentException("No se puede asignar el rol SUPER_ADMIN desde este módulo");
        }
    }
}
