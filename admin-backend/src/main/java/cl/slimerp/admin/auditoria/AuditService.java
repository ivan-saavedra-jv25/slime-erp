package cl.slimerp.admin.auditoria;

import cl.slimerp.admin.auth.AdminActual;
import cl.slimerp.admin.tenant.Tenant;
import cl.slimerp.admin.usuario.AdminUsuario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

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

        return auditLogRepository.save(AuditLog.builder()
                .adminUsuario(admin)
                .company(company)
                .action(action)
                .modulo(modulo)
                .entityType(entityType)
                .entityId(entityId)
                .oldValue(oldValue)
                .newValue(newValue)
                .build());
    }
}