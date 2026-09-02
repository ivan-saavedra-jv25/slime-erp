package cl.slimerp.ventas;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VentaRepository extends JpaRepository<Venta, Long> {
    @EntityGraph(attributePaths = "detalle")
    List<Venta> findByTenantIdAndActivoTrueOrderByFechaDesc(Long tenantId);

    @EntityGraph(attributePaths = "detalle")
    Optional<Venta> findByIdAndTenantIdAndActivoTrue(Long id, Long tenantId);
}
