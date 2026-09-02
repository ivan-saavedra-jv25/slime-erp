package cl.slimerp.catalogo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
    List<Categoria> findByTenantIdAndActivoTrue(Long tenantId);

    Optional<Categoria> findByIdAndTenantIdAndActivoTrue(Long id, Long tenantId);
}
