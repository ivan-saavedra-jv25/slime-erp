package cl.slimerp.notasdebito;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface NotaDebitoRepository extends JpaRepository<NotaDebito, Long>,
        JpaSpecificationExecutor<NotaDebito> {

    @EntityGraph(attributePaths = "detalle")
    Optional<NotaDebito> findByIdAndTenantId(Long id, Long tenantId);

    // Dashboard: el período completo, sin paginar (un período típico son decenas
    // de documentos, no miles).
    List<NotaDebito> findByTenantIdAndFechaBetweenOrderByFolioAsc(
            Long tenantId, LocalDate desde, LocalDate hasta);

    // Trazabilidad hacia atrás: qué notas de débito revierten una nota de crédito.
    List<NotaDebito> findByTenantIdAndNotaCreditoIdOrderByFolioAsc(
            Long tenantId, Long notaCreditoId);

    // Para calcular en una sola consulta el monto ya revertido de varias NCs
    // candidatas al armar "notas-credito-asociables".
    List<NotaDebito> findByTenantIdAndNotaCreditoIdInOrderByFolioAsc(
            Long tenantId, List<Long> notaCreditoIds);

    boolean existsByTenantIdAndNotaCreditoId(Long tenantId, Long notaCreditoId);
}