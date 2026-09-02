package cl.slimerp.compras;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record CompraRequest(
        @NotNull Long proveedorId,
        Long bodegaId,
        String observacion,
        @NotEmpty @Valid List<Item> items
) {
    public record Item(
            @NotNull Long productoId,
            @NotNull BigDecimal cantidad,
            @NotNull BigDecimal precioUnitario
    ) {
    }
}
