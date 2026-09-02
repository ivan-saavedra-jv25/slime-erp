package cl.slimerp.ventas;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record VentaRequest(
        @NotNull Long clienteId,
        @NotNull Long formaPagoId,
        Long bodegaId,
        @NotNull TipoDocumentoVenta tipoDocumento,
        boolean exento,
        String observacion,
        BigDecimal descuento,
        @NotEmpty @Valid List<Item> items
) {
    public record Item(
            @NotNull Long productoId,
            @NotNull BigDecimal cantidad,
            @NotNull BigDecimal precioUnitario
    ) {
    }
}
