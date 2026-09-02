package cl.slimerp.catalogo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubcategoriaRepository extends JpaRepository<Subcategoria, Long> {
    List<Subcategoria> findByTenantIdAndActivoTrue(Long tenantId);

    List<Subcategoria> findByTenantIdAndCategoriaIdAndActivoTrue(Long tenantId, Long categoriaId);

    Optional<Subcategoria> findByIdAndTenantIdAndActivoTrue(Long id, Long tenantId);
}
