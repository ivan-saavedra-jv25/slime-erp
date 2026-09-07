package cl.slimerp.catalogo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubcategoriaRepository extends JpaRepository<Subcategoria, Long> {
    List<Subcategoria> findByTenantIdAndActivoTrue(Long tenantId);

    List<Subcategoria> findByTenantIdAndCategoriaIdAndActivoTrue(Long tenantId, Long categoriaId);

    Optional<Subcategoria> findByIdAndTenantIdAndActivoTrue(Long id, Long tenantId);

    // Ver la nota en ProductoRepository.buscar(): "busqueda" siempre trae el
    // patrón LIKE ya armado para evitar comparar contra un parámetro nulo.
    @Query("""
            SELECT s FROM Subcategoria s
            WHERE s.tenantId = :tenantId AND s.categoriaId = :categoriaId AND s.activo = true
              AND LOWER(s.nombre) LIKE :busqueda
            """)
    Page<Subcategoria> buscar(
            @Param("tenantId") Long tenantId,
            @Param("categoriaId") Long categoriaId,
            @Param("busqueda") String busqueda,
            Pageable pageable);
}
