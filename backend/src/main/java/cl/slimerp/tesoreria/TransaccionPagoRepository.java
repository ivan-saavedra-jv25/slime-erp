package cl.slimerp.tesoreria;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface TransaccionPagoRepository extends JpaRepository<TransaccionPago, Long>,
        JpaSpecificationExecutor<TransaccionPago> {
    List<TransaccionPago> findByTenantIdAndCuentaPorCobrarIdOrderByFechaDesc(Long tenantId, Long cuentaPorCobrarId);

    Optional<TransaccionPago> findByIdAndTenantId(Long id, Long tenantId);

    List<TransaccionPago> findByTenantIdAndCuentaPorCobrarIdAndEstado(
            Long tenantId, Long cuentaPorCobrarId, EstadoTransaccion estado);
}
