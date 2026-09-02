package cl.slimerp.inventario;

import jakarta.validation.constraints.NotBlank;

public record BodegaRequest(@NotBlank String nombre) {
}
