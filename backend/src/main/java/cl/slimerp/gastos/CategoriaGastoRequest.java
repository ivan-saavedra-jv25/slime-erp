package cl.slimerp.gastos;

import jakarta.validation.constraints.NotBlank;

public record CategoriaGastoRequest(@NotBlank String nombre) {
}
