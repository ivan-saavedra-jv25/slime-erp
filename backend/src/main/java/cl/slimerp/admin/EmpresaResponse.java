package cl.slimerp.admin;

import cl.slimerp.tenant.Tenant;

import java.time.LocalDateTime;

public record EmpresaResponse(
        Long id,
        String nombre,
        String rut,
        String plan,
        boolean activo,
        LocalDateTime fechaAlta
) {
    public static EmpresaResponse desde(Tenant tenant) {
        return new EmpresaResponse(
                tenant.getId(), tenant.getNombre(), tenant.getRut(),
                tenant.getPlan(), tenant.isActivo(), tenant.getFechaAlta());
    }
}
