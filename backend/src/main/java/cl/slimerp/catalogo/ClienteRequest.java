package cl.slimerp.catalogo;

import jakarta.validation.constraints.NotBlank;

public record ClienteRequest(
        @NotBlank String nombre,
        String rut,
        String email,
        String telefono,
        String direccion,
        String razonSocial,
        String giro,
        String comuna,
        String ciudad
) {
}
