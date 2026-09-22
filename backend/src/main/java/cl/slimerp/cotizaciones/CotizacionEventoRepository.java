package cl.slimerp.cotizaciones;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CotizacionEventoRepository extends JpaRepository<CotizacionEvento, Long> {

    List<CotizacionEvento> findByTenantIdAndCotizacionIdOrderByFechaAscIdAsc(Long tenantId, Long cotizacionId);
}
