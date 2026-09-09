package cl.slimerp.admin.auditoria;

import cl.slimerp.admin.auth.AdminActual;
import cl.slimerp.admin.common.Paginated;
import cl.slimerp.admin.tenant.Tenant;
import cl.slimerp.admin.usuario.AdminUsuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final AdminActual adminActual;

    public AuditService(AuditLogRepository auditLogRepository, AdminActual adminActual) {
        this.auditLogRepository = auditLogRepository;
        this.adminActual = adminActual;
    }

    @Transactional
    public AuditLog registrar(String action, String modulo, Tenant company,
                              String entityType, Long entityId,
                              String oldValue, String newValue) {
        return registrar(action, modulo, company, entityType, entityId, oldValue, newValue, null, null);
    }

    @Transactional
    public AuditLog registrar(String action, String modulo, Tenant company,
                              String entityType, Long entityId,
                              String oldValue, String newValue,
                              String ipAddress, String userAgent) {
        Long adminId = null;
        try {
            adminId = adminActual.id();
        } catch (IllegalStateException ex) {
            // Contexto sin sesión (p. ej. jobs automáticos).
        }

        AdminUsuario admin = null;
        if (adminId != null) {
            admin = AdminUsuario.builder().id(adminId).build();
        }

        try {
            return auditLogRepository.save(AuditLog.builder()
                    .adminUsuario(admin)
                    .company(company)
                    .action(action)
                    .modulo(modulo)
                    .entityType(entityType)
                    .entityId(entityId)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .build());
        } catch (RuntimeException ex) {
            // La auditoría nunca debe interrumpir la operación principal.
            log.error("No se pudo registrar auditoría de {} en {}", action, modulo, ex);
            return null;
        }
    }

    public Paginated<AuditLogResponse> listar(int page, int limit, Long adminUserId, Long companyId,
                                              String modulo, String action,
                                              LocalDateTime desde, LocalDateTime hasta) {
        Specification<AuditLog> spec = Specification.where(null);
        if (adminUserId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("adminUsuario").get("id"), adminUserId));
        }
        if (companyId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("company").get("id"), companyId));
        }
        if (modulo != null && !modulo.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(cb.lower(root.get("modulo")), modulo.toLowerCase()));
        }
        if (action != null && !action.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(cb.lower(root.get("action")), action.toLowerCase()));
        }
        if (desde != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("creadoEn"), desde));
        }
        if (hasta != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("creadoEn"), hasta));
        }

        int pagina = Math.max(0, page);
        int tamano = Math.min(Math.max(1, limit), 100);
        Pageable pageable = PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.DESC, "creadoEn"));
        Page<AuditLog> resultados = auditLogRepository.findAll(spec, pageable);

        return Paginated.desde(resultados.map(AuditLogResponse::desde));
    }
}