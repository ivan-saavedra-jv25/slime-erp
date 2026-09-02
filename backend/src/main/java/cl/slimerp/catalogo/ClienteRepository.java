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
}
