package cl.slimerp.admin.sii;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Estado de configuración SII de una empresa (spec §15). Nunca incluye secretos. */
public record SiiEstadoResponse(
        Long empresaId,
        String empresaNombre,
        String estado,
        LocalDate certificadoVence,
        String ambiente,
        LocalDateTime ultimaComunicacion,
        LocalDateTime ultimoDte
) {}