package cl.slimerp.admin.tenant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long>, JpaSpecificationExecutor<Tenant> {
    Optional<Tenant> findByRut(String rut);

    long countByActivoTrue();

    long countByStatus(String status);

    long countByFechaAltaBetween(LocalDateTime desde, LocalDateTime hasta);

    long countByActivoFalse();
}