package cl.slimerp.notascredito;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotaCreditoFolioContadorRepository extends JpaRepository<NotaCreditoFolioContador, Long> {

    // Crea el contador del tenant en 1 si no existe, o lo incrementa en 1 si ya
    // existe — en una sola sentencia atómica, sin condición de carrera entre
    // notas de crédito concurrentes (el UPSERT bloquea la fila en conflicto).
    // Mismo patrón que NotaVentaFolioContadorRepository: una sola serie por
    // tenant, las notas de crédito no se separan por tipo de documento.
    @Query(value = """
            INSERT INTO nota_credito_folio_contador (tenant_id, ultimo_folio)
            VALUES (:tenantId, 1)
            ON CONFLICT (tenant_id)
            DO UPDATE SET ultimo_folio = nota_credito_folio_contador.ultimo_folio + 1
            RETURNING ultimo_folio
            """, nativeQuery = true)
    Integer siguienteFolio(@Param("tenantId") Long tenantId);
}
