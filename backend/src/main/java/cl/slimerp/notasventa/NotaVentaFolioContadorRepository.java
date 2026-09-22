package cl.slimerp.notasventa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotaVentaFolioContadorRepository extends JpaRepository<NotaVentaFolioContador, Long> {

    // Crea el contador del tenant en 1 si no existe, o lo incrementa en 1 si ya
    // existe — en una sola sentencia atómica, sin condición de carrera entre
    // notas de venta concurrentes (el UPSERT bloquea la fila en conflicto).
    // Mismo patrón que CotizacionFolioContadorRepository: una sola serie por
    // tenant, las notas de venta no se separan por tipo de documento.
    @Query(value = """
            INSERT INTO nota_venta_folio_contador (tenant_id, ultimo_folio)
            VALUES (:tenantId, 1)
            ON CONFLICT (tenant_id)
            DO UPDATE SET ultimo_folio = nota_venta_folio_contador.ultimo_folio + 1
            RETURNING ultimo_folio
            """, nativeQuery = true)
    Integer siguienteFolio(@Param("tenantId") Long tenantId);
}