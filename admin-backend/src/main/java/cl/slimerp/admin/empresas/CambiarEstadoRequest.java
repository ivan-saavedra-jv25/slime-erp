package cl.slimerp.admin.empresas;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CambiarEstadoRequest(
        @NotNull EstadoEmpresa estado,
        @NotBlank String motivo) {
}