package cl.slimerp.admin.cobranza;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;

public interface CobranzaEmpresaRepository extends JpaRepository<CobranzaEmpresa, Long>,
        JpaSpecificationExecutor<CobranzaEmpresa> {

    long countByEstado(EstadoCobranza estado);

    @Query("select coalesce(sum(c.saldoPendiente), 0) from CobranzaEmpresa c "
            + "where c.tenantId = :tenantId and c.estado in :estados")
    BigDecimal sumSaldoPendienteTenant(@Param("tenantId") Long tenantId,
                                       @Param("estados") Collection<EstadoCobranza> estados);
}