package cl.slimerp.notasventa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotaVentaEntregaRepository extends JpaRepository<NotaVentaEntrega, Long> {

    List<NotaVentaEntrega> findByTenantIdAndNotaVentaIdOrderByFechaAscIdAsc(Long tenantId, Long notaVentaId);
}