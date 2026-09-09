package cl.slimerp.admin.dte;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Documento tributario de venta para monitoreo global (spec §16). */
public record DteResponse(
        Long id,
        Long empresaId,
        String empresaNombre,
        String tipoDocumento,
        Integer codigoSii,
        boolean exento,
        Integer folio,
        String rutReceptor,
        String razonSocialReceptor,
        LocalDateTime fecha,
        BigDecimal montoTotal,
        String estado
) {}