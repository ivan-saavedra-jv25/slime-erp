package cl.slimerp.catalogo;

import jakarta.validation.constraints.NotBlank;

public record ProveedorRequest(
        @NotBlank String nombre,
        String rut,
        String email,
        String telefono,
        String direccion
) {
}
