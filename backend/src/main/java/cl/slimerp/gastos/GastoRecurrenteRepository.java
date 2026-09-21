package cl.slimerp.gastos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GastoRecurrenteRepository extends JpaRepository<GastoRecurrente, Long> {
    List<GastoRecurrente> findByTenantIdAndActivoTrue(Long tenantId);

    Optional<GastoRecurrente> findByIdAndTenantIdAndActivoTrue(Long id, Long tenantId);
}
