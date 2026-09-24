package cl.slimerp.notasdebito;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotaDebitoEventoRepository extends JpaRepository<NotaDebitoEvento, Long> {

    List<NotaDebitoEvento> findByTenantIdAndNotaDebitoIdOrderByFechaAscIdAsc(
            Long tenantId, Long notaDebitoId);
}