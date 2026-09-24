package cl.slimerp.notasdebito;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotaDebitoMovimientoRepository extends JpaRepository<NotaDebitoMovimiento, Long> {

    List<NotaDebitoMovimiento> findByTenantIdAndNotaDebitoIdOrderByFechaAscIdAsc(
            Long tenantId, Long notaDebitoId);

    List<NotaDebitoMovimiento> findByTenantIdAndNotaDebitoIdAndTipo(
            Long tenantId, Long notaDebitoId, TipoMovimientoNotaDebito tipo);
}