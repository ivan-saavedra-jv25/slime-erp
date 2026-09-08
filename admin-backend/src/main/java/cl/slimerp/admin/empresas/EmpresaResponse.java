package cl.slimerp.admin.empresas;

import cl.slimerp.admin.tenant.Tenant;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EmpresaResponse(
        Long id,
        String nombre,
        String rut,
        String businessName,
        String plan,
        String status,
        boolean activo,
        LocalDateTime fechaAlta,
        LocalDateTime lastAccessAt,
        long usuariosActivos,
        BigDecimal saldoPendiente
) {
    public static EmpresaResponse desde(Tenant tenant, long usuariosActivos, BigDecimal saldoPendiente) {
        return new EmpresaResponse(
                tenant.getId(), tenant.getNombre(), tenant.getRut(), tenant.getBusinessName(),
                tenant.getPlan(), tenant.getStatus(), tenant.isActivo(), tenant.getFechaAlta(),
                tenant.getLastAccessAt(), usuariosActivos, saldoPendiente);
    }
}