package cl.slimerp.inventario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BodegaRequest(@NotBlank String nombre, @NotNull TipoBodega tipo) {
}
