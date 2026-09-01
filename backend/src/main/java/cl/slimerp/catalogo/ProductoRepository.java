package cl.slimerp.catalogo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductoRepository extends JpaRepository<Producto, Long> {
    List<Producto> findByTenantIdAndActivoTrue(Long tenantId);

    Optional<Producto> findByIdAndTenantIdAndActivoTrue(Long id, Long tenantId);

    Optional<Producto> findFirstByTenantIdAndSku(Long tenantId, String sku);
}
