package cl.slimerp.admin.auditoria;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    List<AuditLog> findTop10ByOrderByCreadoEnDesc(Pageable pageable);

    List<AuditLog> findByCompanyIdOrderByCreadoEnDesc(Long companyId, Pageable pageable);
}