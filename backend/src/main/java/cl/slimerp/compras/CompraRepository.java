package cl.slimerp.compras;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompraRepository extends JpaRepository<Compra, Long> {
    @EntityGraph(attributePaths = "detalle")
    List<Compra> findByTenantIdAndActivoTrueOrderByFechaDesc(Long tenantId);

    @EntityGraph(attributePaths = "detalle")
    Optional<Compra> findByIdAndTenantIdAndActivoTrue(Long id, Long tenantId);
}
