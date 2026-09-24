package cl.slimerp.notascredito;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotaCreditoEventoRepository extends JpaRepository<NotaCreditoEvento, Long> {

    List<NotaCreditoEvento> findByTenantIdAndNotaCreditoIdOrderByFechaAscIdAsc(
            Long tenantId, Long notaCreditoId);
}
