package cl.slimerp.admin.dte;

/** Resumen del monitoreo DTE. Solo expone los contadores disponibles en el ERP
 * (emitidos y anulados); los estados de envío SII se sumarán con la integración. */
public record DteDashboardResponse(
        long emitidos,
        long anulados
) {}