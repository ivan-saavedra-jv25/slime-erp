package cl.slimerp.tesoreria;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CuentaPorPagarRepository extends JpaRepository<CuentaPorPagar, Long> {
    List<CuentaPorPagar> findByTenantIdOrderByFechaGeneracionDesc(Long tenantId);

    List<CuentaPorPagar> findByTenantIdAndEstadoOrderByFechaGeneracionDesc(Long tenantId, EstadoCuentaPorPagar estado);

    List<CuentaPorPagar> findByTenantIdAndProveedorIdOrderByFechaGeneracionDesc(Long tenantId, Long proveedorId);

    List<CuentaPorPagar> findByTenantIdAndCategoriaGastoIdOrderByFechaGeneracionDesc(Long tenantId, Long categoriaGastoId);

    Optional<CuentaPorPagar> findByIdAndTenantId(Long id, Long tenantId);

    Optional<CuentaPorPagar> findByTenantIdAndCompraId(Long tenantId, Long compraId);

    Optional<CuentaPorPagar> findByTenantIdAndGastoId(Long tenantId, Long gastoId);

    List<CuentaPorPagar> findByTenantIdAndGastoIdIn(Long tenantId, Collection<Long> gastoIds);

    List<CuentaPorPagar> findByTenantIdAndIdIn(Long tenantId, Collection<Long> ids);
}
