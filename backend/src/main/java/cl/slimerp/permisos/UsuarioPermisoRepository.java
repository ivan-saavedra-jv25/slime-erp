package cl.slimerp.permisos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UsuarioPermisoRepository extends JpaRepository<UsuarioPermiso, Long> {

    List<UsuarioPermiso> findByTenantIdAndUsuarioId(Long tenantId, Long usuarioId);

    void deleteByTenantIdAndUsuarioId(Long tenantId, Long usuarioId);
}
