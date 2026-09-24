package cl.slimerp.notascredito;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotaCreditoDetalleRepository extends JpaRepository<NotaCreditoDetalle, Long> {

    // Cuánto se ha recuperado ya de cada línea del documento original. Solo
    // cuentan las notas de crédito EMITIDAS: un borrador no reserva nada y una
    // anulada ya liberó su cantidad. Es la base del control que impide recuperar
    // dos veces la misma mercadería.
    @Query("""
           SELECT new cl.slimerp.notascredito.CantidadRecuperada(d.ventaDetalleId, SUM(d.cantidad))
           FROM NotaCreditoDetalle d
           WHERE d.notaCredito.tenantId = :tenantId
             AND d.notaCredito.ventaId = :ventaId
             AND d.notaCredito.estado = cl.slimerp.notascredito.EstadoNotaCredito.EMITIDA
             AND d.recuperaInventario = true
             AND d.ventaDetalleId IS NOT NULL
           GROUP BY d.ventaDetalleId
           """)
    List<CantidadRecuperada> cantidadesRecuperadas(@Param("tenantId") Long tenantId,
                                                   @Param("ventaId") Long ventaId);

    // Igual que el anterior pero ignorando una nota de crédito concreta: se usa
    // al editar o emitir, para que sus propias líneas no se cuenten como ya
    // recuperadas. Son dos consultas separadas en vez de un "(:id IS NULL OR ...)"
    // porque el driver de Postgres no infiere el tipo de un parámetro que solo se
    // compara contra IS NULL.
    @Query("""
           SELECT new cl.slimerp.notascredito.CantidadRecuperada(d.ventaDetalleId, SUM(d.cantidad))
           FROM NotaCreditoDetalle d
           WHERE d.notaCredito.tenantId = :tenantId
             AND d.notaCredito.ventaId = :ventaId
             AND d.notaCredito.estado = cl.slimerp.notascredito.EstadoNotaCredito.EMITIDA
             AND d.notaCredito.id <> :excluyendoId
             AND d.recuperaInventario = true
             AND d.ventaDetalleId IS NOT NULL
           GROUP BY d.ventaDetalleId
           """)
    List<CantidadRecuperada> cantidadesRecuperadasExcluyendo(@Param("tenantId") Long tenantId,
                                                             @Param("ventaId") Long ventaId,
                                                             @Param("excluyendoId") Long excluyendoId);
}
