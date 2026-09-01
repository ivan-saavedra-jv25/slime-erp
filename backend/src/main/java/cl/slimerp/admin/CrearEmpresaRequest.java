package cl.slimerp.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CrearEmpresaRequest(
        @NotBlank String nombre,
        @NotBlank String rut,
        String plan,
        @NotBlank String adminNombre,
        @NotBlank String adminRut,
        @NotBlank @Email String adminEmail,
        @NotBlank String adminPassword
) {}
