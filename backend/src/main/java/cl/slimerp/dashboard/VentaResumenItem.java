package cl.slimerp.dashboard;

import cl.slimerp.ventas.TipoDocumentoVenta;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record VentaResumenItem(
        Long id,
        LocalDateTime fecha,
        String clienteNombre,
        BigDecimal total,
        TipoDocumentoVenta tipoDocumento
) {
}
