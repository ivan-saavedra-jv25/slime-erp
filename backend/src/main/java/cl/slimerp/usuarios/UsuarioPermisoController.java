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

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/usuarios/{id}")
public class UsuarioPermisoController {

    /**
     * Permisos que nunca pueden otorgarse como permiso extra desde este módulo.
     * EMPRESAS_ADMINISTRAR no está acotado al tenant: quien lo tiene administra
     * todas las empresas de la plataforma, por lo que pertenece solo al rol
     * SUPER_ADMIN (ver {@link RolPermisos}) y no puede concederse por usuario,
     * igual que {@code UsuarioController} no permite asignar el rol SUPER_ADMIN.
     */
    private static final Set<Permiso> NO_OTORGABLES = EnumSet.of(Permiso.EMPRESAS_ADMINISTRAR);

    private final UsuarioRepository usuarioRepository;
    private final UsuarioPermisoRepository usuarioPermisoRepository;

    public UsuarioPermisoController(UsuarioRepository usuarioRepository,
                                     UsuarioPermisoRepository usuarioPermisoRepository) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioPermisoRepository = usuarioPermisoRepository;
    }

    @GetMapping("/permisos")
    @PreAuthorize("hasAuthority('USUARIOS_VER')")
    public ResponseEntity<PermisosUsuarioResponse> obtener(@PathVariable Long id) {
        return usuarioRepository.findByIdAndTenantId(id, TenantContext.getTenantId())
                .map(usuario -> {
                    Set<String> permisosRol = RolPermisos.permisosDe(usuario.getRol()).stream()
                            .map(Permiso::name).collect(Collectors.toSet());
                    Set<String> permisosExtra = usuarioPermisoRepository
                            .findByTenantIdAndUsuarioId(TenantContext.getTenantId(), id).stream()
                            .map(up -> up.getPermiso().name()).collect(Collectors.toSet());

                    return ResponseEntity.ok(new PermisosUsuarioResponse(usuario.getRol(), permisosRol, permisosExtra));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/permisos-extra")
    @PreAuthorize("hasAuthority('USUARIOS_EDITAR')")
    @Transactional
    public ResponseEntity<Void> guardarExtra(@PathVariable Long id, @Valid @RequestBody PermisosExtraRequest request) {
        validarPermisosOtorgables(request.permisos());

        Long tenantId = TenantContext.getTenantId();
        return usuarioRepository.findByIdAndTenantId(id, tenantId)
                .map(usuario -> {
                    aplicarPermisosExtra(tenantId, id, usuario, request.permisos());
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Sincroniza los permisos extra del usuario por diferencia: solo borra los
     * que sobran e inserta los que faltan. Las filas que no cambian se dejan
     * intactas, lo que conserva su fecha_creacion original y evita chocar con
     * el UNIQUE (usuario_id, permiso) al reinsertar una fila cuyo DELETE aún no
     * se ha vaciado a la base de datos.
     */
    private void aplicarPermisosExtra(Long tenantId, Long usuarioId, Usuario usuario, Set<Permiso> solicitados) {
        Set<Permiso> permisosDelRol = RolPermisos.permisosDe(usuario.getRol());
        Set<Permiso> deseados = solicitados.stream()
                .filter(permiso -> !permisosDelRol.contains(permiso))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Permiso.class)));

        List<UsuarioPermiso> actuales = usuarioPermisoRepository.findByTenantIdAndUsuarioId(tenantId, usuarioId);
        Set<Permiso> yaOtorgados = actuales.stream()
                .map(UsuarioPermiso::getPermiso)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Permiso.class)));

        List<UsuarioPermiso> aEliminar = actuales.stream()
                .filter(up -> !deseados.contains(up.getPermiso()))
                .toList();
        if (!aEliminar.isEmpty()) {
            usuarioPermisoRepository.deleteAll(aEliminar);
        }

        deseados.stream()
                .filter(permiso -> !yaOtorgados.contains(permiso))
                .forEach(permiso -> usuarioPermisoRepository.save(UsuarioPermiso.builder()
                        .tenantId(tenantId)
                        .usuarioId(usuarioId)
                        .permiso(permiso)
                        .build()));
    }

    private void validarPermisosOtorgables(Set<Permiso> permisos) {
        permisos.stream()
                .filter(NO_OTORGABLES::contains)
                .findFirst()
                .ifPresent(permiso -> {
                    throw new IllegalArgumentException(
                            "No se puede otorgar el permiso " + permiso.name() + " desde este módulo");
                });
    }
}
