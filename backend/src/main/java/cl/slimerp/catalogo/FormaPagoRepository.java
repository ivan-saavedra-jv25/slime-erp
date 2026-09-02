package cl.slimerp.catalogo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FormaPagoRepository extends JpaRepository<FormaPago, Long> {
    List<FormaPago> findByTenantIdAndActivoTrue(Long tenantId);

    Optional<FormaPago> findByIdAndTenantIdAndActivoTrue(Long id, Long tenantId);
}
