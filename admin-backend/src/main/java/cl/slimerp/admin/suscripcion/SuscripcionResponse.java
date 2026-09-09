package cl.slimerp.admin.suscripcion;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SuscripcionResponse(
        Long id,
        Long companyId,
        String empresaNombre,
        Long planId,
        String planNombre,
        String estado,
        LocalDate fechaInicio,
        LocalDate fechaVencimiento,
        String cicloFacturacion,
        BigDecimal precio,
        Integer periodoGraciaDias
) {

    public static SuscripcionResponse desde(Suscripcion suscripcion, String empresaNombre) {
        return new SuscripcionResponse(
                suscripcion.getId(),
                suscripcion.getCompanyId(),
                empresaNombre,
                suscripcion.getPlan().getId(),
                suscripcion.getPlan().getNombre(),
                suscripcion.getEstado(),
                suscripcion.getFechaInicio(),
                suscripcion.getFechaVencimiento(),
                suscripcion.getCicloFacturacion(),
                suscripcion.getPrecio(),
                suscripcion.getPeriodoGraciaDias());
    }
}