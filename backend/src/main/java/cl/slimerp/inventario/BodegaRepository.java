package cl.slimerp.inventario;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BodegaRepository extends JpaRepository<Bodega, Long> {
    List<Bodega> findByTenantIdAndActivoTrue(Long tenantId);

    Optional<Bodega> findByIdAndTenantIdAndActivoTrue(Long id, Long tenantId);

    Optional<Bodega> findByTenantIdAndPrincipalTrueAndActivoTrue(Long tenantId);

    // Ver la nota en ProductoRepository.buscar(): "busqueda" siempre trae el
    // patrón LIKE ya armado para evitar comparar contra un parámetro nulo.
    @Query("""
            SELECT b FROM Bodega b
            WHERE b.tenantId = :tenantId AND b.activo = true
              AND LOWER(b.nombre) LIKE :busqueda
            """)
    Page<Bodega> buscar(@Param("tenantId") Long tenantId, @Param("busqueda") String busqueda, Pageable pageable);
}
