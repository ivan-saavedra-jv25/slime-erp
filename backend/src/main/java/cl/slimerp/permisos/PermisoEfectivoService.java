package cl.slimerp.permisos;

import cl.slimerp.tenant.Rol;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Set;

/**
 * Calcula los permisos reales de un usuario: los que le da su rol (fijos,
 * ver {@link RolPermisos}) más los permisos extra que se le hayan otorgado
 * individualmente ({@link UsuarioPermiso}). Nunca resta permisos del rol.
 */
@Service
public class PermisoEfectivoService {

    private final UsuarioPermisoRepository usuarioPermisoRepository;

    public PermisoEfectivoService(UsuarioPermisoRepository usuarioPermisoRepository) {
        this.usuarioPermisoRepository = usuarioPermisoRepository;
    }

    public Set<Permiso> calcular(Long tenantId, Long usuarioId, Rol rol) {
        Set<Permiso> permisos = EnumSet.noneOf(Permiso.class);
        permisos.addAll(RolPermisos.permisosDe(rol));
        usuarioPermisoRepository.findByTenantIdAndUsuarioId(tenantId, usuarioId)
                .forEach(up -> permisos.add(up.getPermiso()));
        return permisos;
    }
}
