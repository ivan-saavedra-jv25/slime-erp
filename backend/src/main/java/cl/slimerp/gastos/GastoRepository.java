package cl.slimerp.gastos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.Optional;

public interface GastoRepository extends JpaRepository<Gasto, Long>, JpaSpecificationExecutor<Gasto> {
    Optional<Gasto> findByIdAndTenantIdAndActivoTrue(Long id, Long tenantId);

    boolean existsByTenantIdAndGastoRecurrenteIdAndFechaBetween(
            Long tenantId, Long gastoRecurrenteId, LocalDate desde, LocalDate hasta);
}
