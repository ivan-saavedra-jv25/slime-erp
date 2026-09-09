package cl.slimerp.admin.plan;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record PlanRequest(
        @NotBlank(message = "El nombre del plan es obligatorio") String nombre,
        String descripcion,

        @NotNull(message = "El precio mensual es obligatorio")
        @DecimalMin(value = "0.0", message = "El precio mensual no puede ser negativo")
        BigDecimal precioMensual,

        @DecimalMin(value = "0.0", message = "El precio anual no puede ser negativo")
        BigDecimal precioAnual,

        @NotNull(message = "El límite de usuarios es obligatorio")
        @Min(value = 1, message = "El límite de usuarios debe ser al menos 1")
        Integer maxUsuarios,

        @NotNull(message = "El límite de documentos es obligatorio")
        @Min(value = 1, message = "El límite de documentos debe ser al menos 1")
        Integer maxDocumentos,

        @NotNull(message = "Los módulos son obligatorios")
        List<String> modulos,

        List<String> caracteristicas,

        String estado
) {
}