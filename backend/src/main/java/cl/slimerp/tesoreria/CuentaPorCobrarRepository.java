package cl.slimerp.tesoreria;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CuentaPorCobrarRepository extends JpaRepository<CuentaPorCobrar, Long> {
    List<CuentaPorCobrar> findByTenantIdOrderByFechaGeneracionDesc(Long tenantId);

    List<CuentaPorCobrar> findByTenantIdAndClienteIdOrderByFechaGeneracionDesc(Long tenantId, Long clienteId);

    List<CuentaPorCobrar> findByTenantIdAndEstadoOrderByFechaGeneracionDesc(Long tenantId, EstadoCuentaPorCobrar estado);

    Optional<CuentaPorCobrar> findByIdAndTenantId(Long id, Long tenantId);

    Optional<CuentaPorCobrar> findByTenantIdAndVentaId(Long tenantId, Long ventaId);
}
