package cl.slimerp.notasventa;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

// Registro de un avance de entrega: las cantidades entregadas por producto.
public record EntregaRequest(
        String observacion,
        @NotEmpty @Valid List<Linea> lineas
) {
    public record Linea(
            @NotNull Long productoId,
            @NotNull BigDecimal cantidad
    ) {
    }
}