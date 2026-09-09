package cl.slimerp.admin.auditoria;

import cl.slimerp.admin.tenant.Tenant;
import cl.slimerp.admin.usuario.AdminUsuario;

import java.time.LocalDateTime;

/** Detalle de un registro de auditoría (shape del spec §19). */
public record AuditLogResponse(
        Long id,
        Long adminUserId,
        String adminNombre,
        String adminEmail,
        Long companyId,
        String companyNombre,
        String action,
        String modulo,
        String entityType,
        Long entityId,
        String oldValue,
        String newValue,
        String ipAddress,
        String userAgent,
        LocalDateTime creadoEn) {

    public static AuditLogResponse desde(AuditLog log) {
        AdminUsuario admin = log.getAdminUsuario();
        Tenant company = log.getCompany();
        return new AuditLogResponse(
                log.getId(),
                admin != null ? admin.getId() : null,
                admin != null ? admin.getNombre() : null,
                admin != null ? admin.getEmail() : null,
                company != null ? company.getId() : null,
                company != null ? company.getNombre() : null,
                log.getAction(),
                log.getModulo(),
                log.getEntityType(),
                log.getEntityId(),
                log.getOldValue(),
                log.getNewValue(),
                log.getIpAddress(),
                log.getUserAgent(),
                log.getCreadoEn());
    }
}