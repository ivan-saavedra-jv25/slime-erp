package cl.slimerp.admin.suscripcion;

import java.time.LocalDate;

public record ExtenderSuscripcionRequest(
        LocalDate nuevoVencimiento,
        Integer dias,
        String motivo
) {
}