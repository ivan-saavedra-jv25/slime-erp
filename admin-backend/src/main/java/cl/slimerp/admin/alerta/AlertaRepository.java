package cl.slimerp.admin.alerta;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertaRepository extends JpaRepository<Alerta, Long> {

    long countBySeverityAndStatus(String severity, String status);

    long countByCompanyIdAndStatus(Long companyId, String status);

    List<Alerta> findByStatusOrderByCreadaEnDesc(String status);

    Page<Alerta> findByStatus(String status, Pageable pageable);
}