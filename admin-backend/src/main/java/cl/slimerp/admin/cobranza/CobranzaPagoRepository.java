package cl.slimerp.admin.cobranza;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CobranzaPagoRepository extends JpaRepository<CobranzaPago, Long> {

    List<CobranzaPago> findByCobranzaEmpresaIdOrderByFechaDesc(Long cobranzaEmpresaId);

    List<CobranzaPago> findByCobranzaEmpresaIdAndEstado(Long cobranzaEmpresaId, EstadoPagoCobranza estado);
}