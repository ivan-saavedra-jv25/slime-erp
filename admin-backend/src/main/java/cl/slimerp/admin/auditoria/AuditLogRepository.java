package cl.slimerp.admin.auditoria;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findTop10ByOrderByCreadoEnDesc(Pageable pageable);

    List<AuditLog> findByCompanyIdOrderByCreadoEnDesc(Long companyId, Pageable pageable);
}