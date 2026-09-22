package cl.slimerp.notasventa;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record NotaVentaRequest(
        @NotNull Long clienteId,
        Long formaPagoId,
        @NotNull LocalDate fechaEmision,
        LocalDate fechaEntregaEstimada,
        String direccionEntrega,
        String condicionesVenta,
        String observaciones,
        boolean exenta,
        String moneda,
        BigDecimal descuento,
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