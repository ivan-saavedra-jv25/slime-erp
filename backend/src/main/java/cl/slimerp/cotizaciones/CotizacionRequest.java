package cl.slimerp.cotizaciones;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CotizacionRequest(
        @NotNull Long clienteId,
        Long formaPagoId,
        @NotNull LocalDate fechaEmision,
        @NotNull LocalDate fechaVencimiento,
        boolean exenta,
        BigDecimal descuento,
        String condicionesComerciales,
        String observaciones,
        @NotEmpty @Valid List<Item> items
) {
    public record Item(
            @NotNull Long productoId,
            @NotNull BigDecimal cantidad,
            @NotNull BigDecimal precioUnitario,
            BigDecimal descuento
    ) {
    }
}
