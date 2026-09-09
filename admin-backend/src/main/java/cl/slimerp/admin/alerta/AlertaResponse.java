package cl.slimerp.admin.alerta;

import java.time.LocalDateTime;

/** Detalle de una alerta de plataforma (shape del spec §17). */
public record AlertaResponse(
        Long id,
        Long companyId,
        String companyNombre,
        String type,
        String severity,
        String title,
        String description,
        LocalDateTime createdAt,
        LocalDateTime readAt,
        LocalDateTime resolvedAt,
        String status) {

    public static AlertaResponse desde(Alerta alerta, String companyNombre) {
        return new AlertaResponse(
                alerta.getId(),
                alerta.getCompanyId(),
                companyNombre,
                alerta.getTipo(),
                alerta.getSeverity(),
                alerta.getTitulo(),
                alerta.getDescripcion(),
                alerta.getCreadaEn(),
                alerta.getLeidaEn(),
                alerta.getResueltaEn(),
                alerta.getStatus());
    }
}