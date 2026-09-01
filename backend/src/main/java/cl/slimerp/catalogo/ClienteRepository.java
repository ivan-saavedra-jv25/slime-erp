package cl.slimerp.catalogo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    List<Cliente> findByTenantIdAndActivoTrue(Long tenantId);

    Optional<Cliente> findByIdAndTenantIdAndActivoTrue(Long id, Long tenantId);
}
