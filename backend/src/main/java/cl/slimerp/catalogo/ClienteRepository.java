package cl.slimerp.catalogo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    List<Cliente> findByTenantIdAndActivoTrue(Long tenantId);

    long countByTenantIdAndActivoTrue(Long tenantId);

    Optional<Cliente> findByIdAndTenantIdAndActivoTrue(Long id, Long tenantId);

    // Resuelve nombre/RUT de varios clientes en una sola consulta (evita N+1
    // al armar el Libro de Ventas).
    List<Cliente> findByTenantIdAndIdIn(Long tenantId, List<Long> ids);

    // Ver la nota en ProductoRepository.buscar(): "busqueda" siempre trae el
    // patrón LIKE ya armado para evitar comparar contra un parámetro nulo.
    @Query("""
            SELECT c FROM Cliente c
            WHERE c.tenantId = :tenantId AND c.activo = true
              AND (LOWER(c.nombre) LIKE :busqueda
                   OR LOWER(COALESCE(c.rut, '')) LIKE :busqueda
                   OR LOWER(COALESCE(c.email, '')) LIKE :busqueda
                   OR LOWER(COALESCE(c.telefono, '')) LIKE :busqueda)
            """)
    Page<Cliente> buscar(@Param("tenantId") Long tenantId, @Param("busqueda") String busqueda, Pageable pageable);

    // Ids de los clientes que calzan con un texto, para filtrar documentos que
    // solo guardan clienteId (sin relación JPA) sin tener que traerlos todos a
    // memoria. No filtra por activo: un documento histórico puede apuntar a un
    // cliente ya desactivado y debe seguir siendo encontrable.
    @Query("""
            SELECT c.id FROM Cliente c
            WHERE c.tenantId = :tenantId
              AND (LOWER(c.nombre) LIKE :busqueda OR LOWER(COALESCE(c.rut, '')) LIKE :busqueda)
            """)
    List<Long> idsPorBusqueda(@Param("tenantId") Long tenantId, @Param("busqueda") String busqueda);
}
