package cl.slimerp.ventas;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FolioVentaContadorRepository extends JpaRepository<FolioVentaContador, Long> {

    // Crea el contador en 1 si no existe, o lo incrementa en 1 si ya existe —
    // en una sola sentencia atómica, sin condición de carrera entre ventas
    // concurrentes del mismo tipo (el UPSERT bloquea la fila en conflicto).
    @Query(value = """
            INSERT INTO folio_venta_contador (tenant_id, clave, ultimo_folio)
            VALUES (:tenantId, :clave, 1)
            ON CONFLICT (tenant_id, clave)
            DO UPDATE SET ultimo_folio = folio_venta_contador.ultimo_folio + 1
            RETURNING ultimo_folio
            """, nativeQuery = true)
    Integer siguienteFolio(@Param("tenantId") Long tenantId, @Param("clave") String clave);
}
