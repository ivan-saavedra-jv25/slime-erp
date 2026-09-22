package cl.slimerp.gastos;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CategoriaGastoRepository extends JpaRepository<CategoriaGasto, Long> {
    List<CategoriaGasto> findByTenantIdAndActivoTrue(Long tenantId);

    Optional<CategoriaGasto> findByIdAndTenantIdAndActivoTrue(Long id, Long tenantId);

    List<CategoriaGasto> findByTenantIdAndIdIn(Long tenantId, Collection<Long> ids);

    @Query("""
            SELECT c FROM CategoriaGasto c
            WHERE c.tenantId = :tenantId AND c.activo = true
              AND LOWER(c.nombre) LIKE :busqueda
            """)
    Page<CategoriaGasto> buscar(@Param("tenantId") Long tenantId, @Param("busqueda") String busqueda, Pageable pageable);
}
