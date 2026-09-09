package cl.slimerp.admin.cobranza;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;

public interface CobranzaPagoRepository extends JpaRepository<CobranzaPago, Long>,
        JpaSpecificationExecutor<CobranzaPago> {

    List<CobranzaPago> findByCobranzaEmpresaIdOrderByFechaDesc(Long cobranzaEmpresaId);

    List<CobranzaPago> findByCobranzaEmpresaIdAndEstado(Long cobranzaEmpresaId, EstadoPagoCobranza estado);
}