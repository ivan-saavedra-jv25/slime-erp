package cl.slimerp.notascredito;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotaCreditoMovimientoRepository extends JpaRepository<NotaCreditoMovimiento, Long> {

    List<NotaCreditoMovimiento> findByTenantIdAndNotaCreditoIdOrderByFechaAscIdAsc(
            Long tenantId, Long notaCreditoId);

    List<NotaCreditoMovimiento> findByTenantIdAndNotaCreditoIdAndTipo(
            Long tenantId, Long notaCreditoId, TipoMovimientoNotaCredito tipo);
}
