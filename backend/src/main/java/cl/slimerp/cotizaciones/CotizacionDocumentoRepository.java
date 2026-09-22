package cl.slimerp.cotizaciones;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CotizacionDocumentoRepository extends JpaRepository<CotizacionDocumento, Long> {

    List<CotizacionDocumento> findByTenantIdAndCotizacionIdOrderByFechaAsc(Long tenantId, Long cotizacionId);

    // Para el Libro de Cotizaciones: resuelve los documentos de varias
    // cotizaciones en una sola consulta.
    List<CotizacionDocumento> findByTenantIdAndCotizacionIdIn(Long tenantId, Collection<Long> cotizacionIds);
}
