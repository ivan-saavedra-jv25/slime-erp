package cl.slimerp.admin.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findByRut(String rut);

    long countByActivoTrue();

    long countByStatus(String status);

    long countByFechaAltaBetween(LocalDateTime desde, LocalDateTime hasta);

    long countByActivoFalse();
}