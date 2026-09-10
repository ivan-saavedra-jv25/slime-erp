package cl.slimerp.inventario;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface MovimientoInventarioHeaderRepository extends JpaRepository<MovimientoInventarioHeader, Long>,
        JpaSpecificationExecutor<MovimientoInventarioHeader> {

    List<MovimientoInventarioHeader> findByTenantIdOrderByFechaDesc(Long tenantId);
}
