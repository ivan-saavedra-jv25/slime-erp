package cl.slimerp.inventario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovimientoInventarioRepository extends JpaRepository<MovimientoInventario, Long> {
    List<MovimientoInventario> findByTenantIdAndProductoIdOrderByFechaDesc(Long tenantId, Long productoId);

    List<MovimientoInventario> findByTenantIdAndHeaderId(Long tenantId, Long headerId);
}
