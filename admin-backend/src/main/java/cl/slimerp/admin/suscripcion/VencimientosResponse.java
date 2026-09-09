package cl.slimerp.admin.suscripcion;

import java.util.List;

/** Ventanas de vencimiento de suscripciones (spec §13). */
public record VencimientosResponse(
        List<SuscripcionResponse> vencenHoy,
        List<SuscripcionResponse> vencenEn3Dias,
        List<SuscripcionResponse> vencenEn7Dias,
        List<SuscripcionResponse> vencenEn30Dias,
        List<SuscripcionResponse> vencidas
) {}