package cl.slimerp.notasventa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface NotaVentaEventoRepository extends JpaRepository<NotaVentaEvento, Long> {

    List<NotaVentaEvento> findByTenantIdAndNotaVentaIdOrderByFechaAscIdAsc(Long tenantId, Long notaVentaId);

    List<NotaVentaEvento> findByTenantIdAndNotaVentaIdIn(Long tenantId, Collection<Long> notaVentaIds);
}