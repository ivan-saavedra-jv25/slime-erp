package cl.slimerp.admin.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findFirstByEmailAndActivoTrue(String email);

    boolean existsByEmail(String email);

    long countByTenantIdAndActivoTrue(Long tenantId);

    long countByTenantId(Long tenantId);

    List<Usuario> findByTenantIdIn(List<Long> tenantIds);
}