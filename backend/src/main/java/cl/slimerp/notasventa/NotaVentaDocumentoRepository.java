package cl.slimerp.notasventa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface NotaVentaDocumentoRepository extends JpaRepository<NotaVentaDocumento, Long> {

    List<NotaVentaDocumento> findByTenantIdAndNotaVentaIdOrderByFechaAsc(Long tenantId, Long notaVentaId);

    List<NotaVentaDocumento> findByTenantIdAndNotaVentaIdIn(Long tenantId, Collection<Long> notaVentaIds);
}