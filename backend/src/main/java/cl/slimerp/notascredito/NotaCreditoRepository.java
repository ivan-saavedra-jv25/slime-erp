package cl.slimerp.notascredito;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface NotaCreditoRepository extends JpaRepository<NotaCredito, Long>,
        JpaSpecificationExecutor<NotaCredito> {

    @EntityGraph(attributePaths = "detalle")
    Optional<NotaCredito> findByIdAndTenantId(Long id, Long tenantId);

    // Dashboard: el período completo, sin paginar (un período típico son decenas
    // de documentos, no miles).
    List<NotaCredito> findByTenantIdAndFechaBetweenOrderByFolioAsc(
            Long tenantId, LocalDate desde, LocalDate hasta);

    // Trazabilidad hacia atrás: qué notas de crédito corrigen un documento.
    List<NotaCredito> findByTenantIdAndVentaIdOrderByFolioAsc(Long tenantId, Long ventaId);

    boolean existsByTenantIdAndVentaId(Long tenantId, Long ventaId);
}
