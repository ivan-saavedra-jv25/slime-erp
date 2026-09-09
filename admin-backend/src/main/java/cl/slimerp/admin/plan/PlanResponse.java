package cl.slimerp.admin.plan;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

public record PlanResponse(
        Long id,
        String nombre,
        String descripcion,
        BigDecimal precioMensual,
        BigDecimal precioAnual,
        Integer maxUsuarios,
        Integer maxDocumentos,
        List<String> modulos,
        List<String> caracteristicas,
        String estado
) {

    public static PlanResponse desde(Plan plan, ObjectMapper objectMapper) {
        return new PlanResponse(
                plan.getId(),
                plan.getNombre(),
                plan.getDescripcion(),
                plan.getPrecioMensual(),
                plan.getPrecioAnual(),
                plan.getMaxUsuarios(),
                plan.getMaxDocumentos(),
                leerLista(plan.getModulos(), objectMapper),
                leerLista(plan.getCaracteristicas(), objectMapper),
                plan.getEstado());
    }

    private static List<String> leerLista(String json, ObjectMapper objectMapper) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }
}