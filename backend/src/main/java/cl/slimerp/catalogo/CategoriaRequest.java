package cl.slimerp.catalogo;

import jakarta.validation.constraints.NotBlank;

public record CategoriaRequest(@NotBlank String nombre) {
}
