package cl.slimerp.inventario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BodegaRepository extends JpaRepository<Bodega, Long> {
    List<Bodega> findByTenantIdAndActivoTrue(Long tenantId);

    Optional<Bodega> findByIdAndTenantIdAndActivoTrue(Long id, Long tenantId);

    Optional<Bodega> findByTenantIdAndPrincipalTrueAndActivoTrue(Long tenantId);
}
