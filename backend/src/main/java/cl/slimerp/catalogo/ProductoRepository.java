package cl.slimerp.catalogo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductoRepository extends JpaRepository<Producto, Long> {
    List<Producto> findByTenantIdAndActivoTrue(Long tenantId);

    Optional<Producto> findByIdAndTenantIdAndActivoTrue(Long id, Long tenantId);

    Optional<Producto> findFirstByTenantIdAndSku(Long tenantId, String sku);

    // "busqueda" siempre viene con el patrón LIKE ya armado (p.ej. "%mouse%",
    // o "%%" si no hay término) para no comparar contra un parámetro nulo:
    // Postgres no logra inferir el tipo de un parámetro que solo se usa en
    // "IS NULL" y falla con "could not determine data type of parameter".
    @Query("""
            SELECT p FROM Producto p
            WHERE p.tenantId = :tenantId AND p.activo = true
              AND (LOWER(p.nombre) LIKE :busqueda
                   OR LOWER(COALESCE(p.sku, '')) LIKE :busqueda
                   OR EXISTS (
                       SELECT 1 FROM Categoria c
                       WHERE c.id = p.categoriaId AND LOWER(c.nombre) LIKE :busqueda
                   ))
            """)
    Page<Producto> buscar(@Param("tenantId") Long tenantId, @Param("busqueda") String busqueda, Pageable pageable);
}
