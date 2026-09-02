package cl.slimerp.catalogo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ProductoRequest(
        String sku,
        @NotBlank String nombre,
        String descripcion,
        @NotNull BigDecimal precioVenta,
        BigDecimal precioCompra
) {
}
