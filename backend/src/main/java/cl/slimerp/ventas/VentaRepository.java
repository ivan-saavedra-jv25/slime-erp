package cl.slimerp.ventas;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VentaRepository extends JpaRepository<Venta, Long> {
    @EntityGraph(attributePaths = "detalle")
    List<Venta> findByTenantIdAndActivoTrueOrderByFechaDesc(Long tenantId);

    @EntityGraph(attributePaths = "detalle")
    Optional<Venta> findByIdAndTenantIdAndActivoTrue(Long id, Long tenantId);

    // Usados por el Dashboard: no necesitan el detalle de líneas.
    List<Venta> findByTenantIdAndActivoTrueAndFechaBetween(Long tenantId, LocalDateTime desde, LocalDateTime hasta);

    // Para el Libro de Ventas: mismo filtro que el del Dashboard pero ordenado,
    // porque el libro se lee de arriba hacia abajo por fecha.
    List<Venta> findByTenantIdAndActivoTrueAndFechaBetweenOrderByFechaAsc(
            Long tenantId, LocalDateTime desde, LocalDateTime hasta);

    List<Venta> findTop8ByTenantIdAndActivoTrueOrderByFechaDesc(Long tenantId);
}
