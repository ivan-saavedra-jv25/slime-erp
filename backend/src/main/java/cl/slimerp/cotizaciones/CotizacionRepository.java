package cl.slimerp.cotizaciones;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CotizacionRepository extends JpaRepository<Cotizacion, Long>, JpaSpecificationExecutor<Cotizacion> {

    @EntityGraph(attributePaths = "detalle")
    Optional<Cotizacion> findByIdAndTenantId(Long id, Long tenantId);

    // Usado por el job de vencimiento: las cotizaciones enviadas cuya vigencia
    // ya expiró.
    List<Cotizacion> findByTenantIdAndEstadoAndFechaVencimientoBefore(
            Long tenantId, EstadoCotizacion estado, LocalDate fecha);

    // Usado por el dashboard y el Libro de Cotizaciones: el período completo,
    // sin paginar (un período típico son decenas de documentos, no miles).
    List<Cotizacion> findByTenantIdAndFechaEmisionBetweenOrderByFolioAsc(
            Long tenantId, LocalDate desde, LocalDate hasta);
}
