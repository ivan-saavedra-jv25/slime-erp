package cl.slimerp.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmailAndTenantId(String email, Long tenantId);

    // Login: el email es único a nivel global de la app para simplificar el flujo de autenticación
    // (el usuario no necesita saber su tenant_id de antemano).
    Optional<Usuario> findFirstByEmailAndActivoTrue(String email);

    boolean existsByEmail(String email);

    List<Usuario> findByTenantId(Long tenantId);

    Optional<Usuario> findByIdAndTenantId(Long id, Long tenantId);
}
