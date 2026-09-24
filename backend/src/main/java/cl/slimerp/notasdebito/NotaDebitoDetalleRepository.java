package cl.slimerp.notasdebito;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotaDebitoDetalleRepository extends JpaRepository<NotaDebitoDetalle, Long> {

    // Cuánto se ha revertido ya de cada línea de la nota de crédito. Solo cuentan
    // las notas de débito EMITIDAS: un borrador no reserva nada y una anulada ya
    // liberó su cantidad. Es la base del control que impide revertir dos veces la
    // misma mercadería.
    @Query("""
           SELECT new cl.slimerp.notasdebito.CantidadRevertida(d.notaCreditoDetalleId, SUM(d.cantidad))
           FROM NotaDebitoDetalle d
           WHERE d.notaDebito.tenantId = :tenantId
             AND d.notaDebito.notaCreditoId = :notaCreditoId
             AND d.notaDebito.estado = cl.slimerp.notasdebito.EstadoNotaDebito.EMITIDA
             AND d.revierteInventario = true
             AND d.notaCreditoDetalleId IS NOT NULL
           GROUP BY d.notaCreditoDetalleId
           """)
    List<CantidadRevertida> cantidadesRevertidas(@Param("tenantId") Long tenantId,
                                                 @Param("notaCreditoId") Long notaCreditoId);

    // Igual que el anterior pero ignorando una nota de débito concreta: se usa
    // al editar, para que sus propias líneas no se cuenten como ya revertidas.
    // Son dos consultas separadas en vez de un "(:id IS NULL OR ...)" porque el
    // driver de Postgres no infiere el tipo de un parámetro que solo se compara
    // contra IS NULL.
    @Query("""
           SELECT new cl.slimerp.notasdebito.CantidadRevertida(d.notaCreditoDetalleId, SUM(d.cantidad))
           FROM NotaDebitoDetalle d
           WHERE d.notaDebito.tenantId = :tenantId
             AND d.notaDebito.notaCreditoId = :notaCreditoId
             AND d.notaDebito.estado = cl.slimerp.notasdebito.EstadoNotaDebito.EMITIDA
             AND d.notaDebito.id <> :excluyendoId
             AND d.revierteInventario = true
             AND d.notaCreditoDetalleId IS NOT NULL
           GROUP BY d.notaCreditoDetalleId
           """)
    List<CantidadRevertida> cantidadesRevertidasExcluyendo(@Param("tenantId") Long tenantId,
                                                           @Param("notaCreditoId") Long notaCreditoId,
                                                           @Param("excluyendoId") Long excluyendoId);
}