package cl.slimerp.notasventa;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NotaVentaRepository extends JpaRepository<NotaVenta, Long>, JpaSpecificationExecutor<NotaVenta> {

    @EntityGraph(attributePaths = "detalle")
    Optional<NotaVenta> findByIdAndTenantId(Long id, Long tenantId);

    // Dashboard y Libro de Notas de Venta: el período completo, sin paginar
    // (un período típico son decenas de documentos, no miles).
    List<NotaVenta> findByTenantIdAndFechaEmisionBetweenOrderByFolioAsc(
            Long tenantId, LocalDate desde, LocalDate hasta);

    // Para el listado por cliente/vendedor resuelve ids en una sola consulta.
    List<NotaVenta> findByTenantIdAndClienteIdIn(Long tenantId, Collection<Long> clienteIds);

    // Una sola nota de venta por cotización de origen: evita duplicar el vínculo
    // de trazabilidad de la cadena.
    Optional<NotaVenta> findByTenantIdAndCotizacionId(Long tenantId, Long cotizacionId);
}