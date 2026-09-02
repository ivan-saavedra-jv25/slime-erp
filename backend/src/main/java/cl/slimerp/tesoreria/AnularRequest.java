package cl.slimerp.tesoreria;

import jakarta.validation.constraints.NotBlank;

public record AnularRequest(@NotBlank String motivo) {
}
