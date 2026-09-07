package cl.slimerp.usuarios;

import cl.slimerp.permisos.Permiso;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record PermisosExtraRequest(
        @NotNull Set<Permiso> permisos
) {
}
