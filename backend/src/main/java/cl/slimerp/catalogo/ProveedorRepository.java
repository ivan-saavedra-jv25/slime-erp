package cl.slimerp.catalogo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProveedorRepository extends JpaRepository<Proveedor, Long> {
    List<Proveedor> findByTenantIdAndActivoTrue(Long tenantId);

    Optional<Proveedor> findByIdAndTenantIdAndActivoTrue(Long id, Long tenantId);

    // Resuelve nombre/RUT de varios proveedores en una sola consulta (evita
    // N+1 al armar el Libro de Compras).
    List<Proveedor> findByTenantIdAndIdIn(Long tenantId, List<Long> ids);
}
