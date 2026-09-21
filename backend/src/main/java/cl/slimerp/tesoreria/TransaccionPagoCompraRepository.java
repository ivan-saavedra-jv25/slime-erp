package cl.slimerp.tesoreria;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface TransaccionPagoCompraRepository extends JpaRepository<TransaccionPagoCompra, Long>,
        JpaSpecificationExecutor<TransaccionPagoCompra> {
    List<TransaccionPagoCompra> findByTenantIdAndCuentaPorPagarIdOrderByFechaDesc(Long tenantId, Long cuentaPorPagarId);

    Optional<TransaccionPagoCompra> findByIdAndTenantId(Long id, Long tenantId);

    List<TransaccionPagoCompra> findByTenantIdAndCuentaPorPagarIdAndEstado(
            Long tenantId, Long cuentaPorPagarId, EstadoTransaccion estado);
}
