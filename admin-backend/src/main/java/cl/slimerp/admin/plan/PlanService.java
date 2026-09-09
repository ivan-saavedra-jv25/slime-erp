package cl.slimerp.admin.plan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import cl.slimerp.admin.suscripcion.SuscripcionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class PlanService {

    private static final List<String> ESTADOS = List.of("ACTIVE", "INACTIVE");
    private static final List<String> ESTADOS_SUSCRIPCION_ACTIVA = List.of("TRIAL", "ACTIVE", "PAST_DUE");

    private final PlanRepository planRepository;
    private final SuscripcionRepository suscripcionRepository;
    private final ObjectMapper objectMapper;

    public PlanService(PlanRepository planRepository,
                       SuscripcionRepository suscripcionRepository,
                       ObjectMapper objectMapper) {
        this.planRepository = planRepository;
        this.suscripcionRepository = suscripcionRepository;
        this.objectMapper = objectMapper;
    }

    public List<PlanResponse> listar() {
        return planRepository.findAll().stream()
                .map(plan -> PlanResponse.desde(plan, objectMapper))
                .toList();
    }

    public PlanResponse obtener(Long id) {
        return PlanResponse.desde(planObligatorio(id), objectMapper);
    }

    @Transactional
    public PlanResponse crear(PlanRequest request) {
        validarNombreUnico(request.nombre(), null);
        Plan plan = Plan.builder()
                .nombre(request.nombre().trim())
                .descripcion(request.descripcion())
                .precioMensual(request.precioMensual())
                .precioAnual(request.precioAnual())
                .maxUsuarios(request.maxUsuarios())
                .maxDocumentos(request.maxDocumentos())
                .modulos(aJson(request.modulos()))
                .caracteristicas(aJson(request.caracteristicas()))
                .estado(normalizarEstado(request.estado()))
                .build();
        return PlanResponse.desde(planRepository.save(plan), objectMapper);
    }

    @Transactional
    public PlanResponse actualizar(Long id, PlanRequest request) {
        Plan plan = planObligatorio(id);
        validarNombreUnico(request.nombre(), id);
        plan.setNombre(request.nombre().trim());
        plan.setDescripcion(request.descripcion());
        plan.setPrecioMensual(request.precioMensual());
        plan.setPrecioAnual(request.precioAnual());
        plan.setMaxUsuarios(request.maxUsuarios());
        plan.setMaxDocumentos(request.maxDocumentos());
        plan.setModulos(aJson(request.modulos()));
        plan.setCaracteristicas(aJson(request.caracteristicas()));
        if (request.estado() != null && !request.estado().isBlank()) {
            plan.setEstado(normalizarEstado(request.estado()));
        }
        return PlanResponse.desde(planRepository.save(plan), objectMapper);
    }

    @Transactional
    public PlanResponse cambiarEstado(Long id, String estado) {
        Plan plan = planObligatorio(id);
        String nuevo = normalizarEstado(estado);
        if ("INACTIVE".equals(nuevo) && !"INACTIVE".equals(plan.getEstado())
                && suscripcionRepository.countByPlanIdAndEstadoIn(id, ESTADOS_SUSCRIPCION_ACTIVA) > 0) {
            throw new PlanConflictException(
                    "No se puede desactivar el plan \"" + plan.getNombre() + "\" porque tiene suscripciones activas");
        }
        plan.setEstado(nuevo);
        return PlanResponse.desde(planRepository.save(plan), objectMapper);
    }

    private Plan planObligatorio(Long id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe un plan con el id " + id));
    }

    private void validarNombreUnico(String nombre, Long excluyendoId) {
        String limpio = nombre == null ? "" : nombre.trim();
        boolean existe = planRepository.findAll().stream()
                .anyMatch(plan -> plan.getNombre().equalsIgnoreCase(limpio)
                        && !Objects.equals(plan.getId(), excluyendoId));
        if (existe) {
            throw new PlanConflictException("Ya existe un plan llamado \"" + limpio + "\"");
        }
    }

    private String normalizarEstado(String estado) {
        String normalizado = estado == null ? "ACTIVE" : estado.trim().toUpperCase();
        if (!ESTADOS.contains(normalizado)) {
            throw new IllegalArgumentException("Estado de plan inválido: " + estado);
        }
        return normalizado;
    }

    private String aJson(List<String> valores) {
        if (valores == null || valores.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(valores);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("No se pudieron serializar los valores", ex);
        }
    }
}