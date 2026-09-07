package cl.slimerp.usuarios;

import cl.slimerp.config.TenantContext;
import cl.slimerp.permisos.Permiso;
import cl.slimerp.permisos.RolPermisos;
import cl.slimerp.permisos.UsuarioPermiso;
import cl.slimerp.permisos.UsuarioPermisoRepository;
import cl.slimerp.tenant.Usuario;
import cl.slimerp.tenant.UsuarioRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/usuarios/{id}")
public class UsuarioPermisoController {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioPermisoRepository usuarioPermisoRepository;

    public UsuarioPermisoController(UsuarioRepository usuarioRepository,
                                     UsuarioPermisoRepository usuarioPermisoRepository) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioPermisoRepository = usuarioPermisoRepository;
    }

    @GetMapping("/permisos")
    @PreAuthorize("hasAuthority('USUARIOS_VER')")
    public PermisosUsuarioResponse obtener(@PathVariable Long id) {
        Usuario usuario = usuarioRepository.findByIdAndTenantId(id, TenantContext.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + id));

        Set<String> permisosRol = RolPermisos.permisosDe(usuario.getRol()).stream()
                .map(Permiso::name).collect(Collectors.toSet());
        Set<String> permisosExtra = usuarioPermisoRepository
                .findByTenantIdAndUsuarioId(TenantContext.getTenantId(), id).stream()
                .map(up -> up.getPermiso().name()).collect(Collectors.toSet());

        return new PermisosUsuarioResponse(usuario.getRol(), permisosRol, permisosExtra);
    }

    @PutMapping("/permisos-extra")
    @PreAuthorize("hasAuthority('USUARIOS_EDITAR')")
    @Transactional
    public ResponseEntity<Void> guardarExtra(@PathVariable Long id, @Valid @RequestBody PermisosExtraRequest request) {
        Long tenantId = TenantContext.getTenantId();
        Usuario usuario = usuarioRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + id));

        Set<Permiso> permisosDelRol = RolPermisos.permisosDe(usuario.getRol());

        usuarioPermisoRepository.deleteByTenantIdAndUsuarioId(tenantId, id);
        request.permisos().stream()
                .filter(permiso -> !permisosDelRol.contains(permiso))
                .forEach(permiso -> usuarioPermisoRepository.save(UsuarioPermiso.builder()
                        .tenantId(tenantId)
                        .usuarioId(id)
                        .permiso(permiso)
                        .build()));

        return ResponseEntity.noContent().build();
    }
}
