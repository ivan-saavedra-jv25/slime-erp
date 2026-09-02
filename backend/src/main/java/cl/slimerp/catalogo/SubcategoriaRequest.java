package cl.slimerp.catalogo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SubcategoriaRequest(@NotNull Long categoriaId, @NotBlank String nombre) {
}
