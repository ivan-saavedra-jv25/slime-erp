package cl.slimerp.inventario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockProductoBodegaRepository extends JpaRepository<StockProductoBodega, Long> {
    List<StockProductoBodega> findByTenantIdAndBodegaId(Long tenantId, Long bodegaId);

    List<StockProductoBodega> findByTenantIdAndProductoId(Long tenantId, Long productoId);

    Optional<StockProductoBodega> findByTenantIdAndProductoIdAndBodegaId(Long tenantId, Long productoId, Long bodegaId);
}
