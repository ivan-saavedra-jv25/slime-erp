package cl.slimerp.admin.cobranza;

import jakarta.validation.constraints.NotBlank;

public record AnularRequest(
        @NotBlank String motivo
) {}