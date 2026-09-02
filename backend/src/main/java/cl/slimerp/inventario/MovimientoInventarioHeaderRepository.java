package cl.slimerp.inventario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovimientoInventarioHeaderRepository extends JpaRepository<MovimientoInventarioHeader, Long> {

    List<MovimientoInventarioHeader> findByTenantIdOrderByFechaDesc(Long tenantId);
}
