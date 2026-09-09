package cl.slimerp.admin.suscripcion;

import cl.slimerp.admin.auditoria.AuditService;
import cl.slimerp.admin.common.Paginated;
import cl.slimerp.admin.configuracion.ConfiguracionRepository;
import cl.slimerp.admin.plan.Plan;
import cl.slimerp.admin.plan.PlanRepository;
import cl.slimerp.admin.tenant.Tenant;
import cl.slimerp.admin.tenant.TenantRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class SuscripcionService {

    private static final List<String> ESTADOS = List.of(
            "TRIAL", "ACTIVE", "PAST_DUE", "SUSPENDED", "CANCELLED", "EXPIRED");
    private static final List<String> ESTADOS_ACTIVOS = List.of("TRIAL", "ACTIVE", "PAST_DUE");
    private static final String PLAN_PLATAFORMA = "plataforma";
    private static final String CLAVE_GRACE_PERIOD = "suscripcion.gracePeriodDays";

    private final SuscripcionRepository suscripcionRepository;
    private final PlanRepository planRepository;
    private final TenantRepository tenantRepository;
    private final ConfiguracionRepository configuracionRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public SuscripcionService(SuscripcionRepository suscripcionRepository,
                              PlanRepository planRepository,
                              TenantRepository tenantRepository,
                              ConfiguracionRepository configuracionRepository,
                              AuditService auditService,
                              ObjectMapper objectMapper) {
        this.suscripcionRepository = suscripcionRepository;
        this.planRepository = planRepository;
        this.tenantRepository = tenantRepository;
        this.configuracionRepository = configuracionRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Paginated<SuscripcionResponse> listar(int page, int limit, String estado, Long planId,
                                                 Long empresaId, Integer proximasAVencerDias) {
        Specification<Suscripcion> spec = (root, query, cb) -> cb.conjunction();
        if (estado != null && !estado.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("estado"), estado.trim().toUpperCase()));
        }
        if (planId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("plan").get("id"), planId));
        }
        if (empresaId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("companyId"), empresaId));
        }
        LocalDate hoy = LocalDate.now();
        boolean esActiva = estado == null || estado.isBlank() || ESTADOS_ACTIVOS.contains(estado.trim().toUpperCase());
        boolean filtroVencimiento = proximasAVencerDias != null && proximasAVencerDias >= 0 && esActiva;
        if (filtroVencimiento) {
            LocalDate hasta = hoy.plusDays(proximasAVencerDias);
            spec = spec.and((root, query, cb) -> {
                List<Predicate> p = new ArrayList<>();
                p.add(cb.greaterThanOrEqualTo(root.get("fechaVencimiento"), hoy));
                p.add(cb.lessThanOrEqualTo(root.get("fechaVencimiento"), hasta));
                p.add(root.get("estado").in(ESTADOS_ACTIVOS));
                return cb.and(p.toArray(new Predicate[0]));
            });
        }
        if (proximasAVencerDias != null && proximasAVencerDias < 0) {
            throw new IllegalArgumentException("proximasAVencerDias no puede ser negativo");
        }

        int pagina = Math.max(0, page);
        int tamano = Math.min(Math.max(1, limit), 100);
        Page<Suscripcion> paginaSuscripciones = suscripcionRepository.findAll(spec,
                PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.ASC, "fechaVencimiento")));

        return Paginated.desde(paginaSuscripciones.map(s -> SuscripcionResponse.desde(s, nombreEmpresa(s.getCompanyId()))));
    }

    @Transactional(readOnly = true)
    public SuscripcionResponse obtener(Long id) {
        Suscripcion suscripcion = suscripcionObligatoria(id);
        return SuscripcionResponse.desde(suscripcion, nombreEmpresa(suscripcion.getCompanyId()));
    }

    @Transactional
    public SuscripcionResponse crear(SuscripcionRequest request) {
        Tenant tenant = tenantObligatoria(request.companyId());
        Plan plan = planObligatorio(request.planId());
        if (!"ACTIVE".equals(plan.getEstado())) {
            throw new SuscripcionConflictException("El plan \"" + plan.getNombre() + "\" está inactivo y no puede asignarse");
        }

        String estado = normalizarEstado(request.estado(), "ACTIVE");
        if (ESTADOS_ACTIVOS.contains(estado)
                && suscripcionRepository.countByCompanyIdAndEstadoIn(tenant.getId(), ESTADOS_ACTIVOS) > 0) {
            throw new SuscripcionConflictException(
                    "La empresa \"" + tenant.getNombre() + "\" ya tiene una suscripción activa");
        }
        if (!request.fechaVencimiento().isAfter(request.fechaInicio())) {
            throw new IllegalArgumentException("La fecha de vencimiento debe ser posterior a la fecha de inicio");
        }
        String ciclo = normalizarCiclo(request.cicloFacturacion());
        Integer gracia = request.periodoGraciaDias() != null ? request.periodoGraciaDias() : gracePeriodDays();

        Suscripcion suscripcion = suscripcionRepository.save(Suscripcion.builder()
                .companyId(tenant.getId())
                .plan(plan)
                .estado(estado)
                .fechaInicio(request.fechaInicio())
                .fechaVencimiento(request.fechaVencimiento())
                .cicloFacturacion(ciclo)
                .precio(request.precio())
                .periodoGraciaDias(gracia)
                .build());

        auditService.registrar("SUBSCRIPTION_CREATED", "suscripciones", tenant, "suscripcion",
                suscripcion.getId(), null, valorJson(suscripcion));
        return SuscripcionResponse.desde(suscripcion, tenant.getNombre());
    }

    @Transactional
    public SuscripcionResponse extender(Long id, ExtenderSuscripcionRequest request) {
        Suscripcion suscripcion = suscripcionObligatoria(id);
        Tenant tenant = tenantObligatoria(suscripcion.getCompanyId());
        LocalDate nuevaFecha;
        if (request.nuevoVencimiento() == null && (request.dias() == null || request.dias() <= 0)) {
            throw new IllegalArgumentException("Indica un nuevo vencimiento o una cantidad de días positiva");
        }
        if (request.nuevoVencimiento() != null) {
            nuevaFecha = request.nuevoVencimiento();
        } else {
            nuevaFecha = suscripcion.getFechaVencimiento().plusDays(request.dias());
        }
        if (!nuevaFecha.isAfter(suscripcion.getFechaVencimiento())) {
            throw new IllegalArgumentException("El nuevo vencimiento debe ser posterior al vencimiento actual");
        }

        String fechaAnterior = suscripcion.getFechaVencimiento().toString();
        suscripcion.setFechaVencimiento(nuevaFecha);
        Suscripcion guardada = suscripcionRepository.save(suscripcion);

        auditService.registrar("SUBSCRIPTION_EXTENDED", "suscripciones", tenant, "suscripcion",
                guardada.getId(),
                "{ \"fechaVencimiento\": \"" + fechaAnterior + "\" }",
                "{ \"fechaVencimiento\": \"" + nuevaFecha + "\""
                        + (request.motivo() == null || request.motivo().isBlank() ? "" : ", \"motivo\": \"" + request.motivo().trim() + "\"")
                        + " }");
        return SuscripcionResponse.desde(guardada, tenant.getNombre());
    }

    @Transactional
    public SuscripcionResponse cambiarPlan(Long id, CambiarPlanRequest request) {
        Suscripcion suscripcion = suscripcionObligatoria(id);
        Tenant tenant = tenantObligatoria(suscripcion.getCompanyId());
        Plan nuevoPlan = planObligatorio(request.planId());
        if (!"ACTIVE".equals(nuevoPlan.getEstado())) {
            throw new SuscripcionConflictException("El plan \"" + nuevoPlan.getNombre() + "\" está inactivo");
        }
        if (suscripcion.getPlan().getId().equals(nuevoPlan.getId())) {
            throw new SuscripcionConflictException("La suscripción ya usa el plan \"" + nuevoPlan.getNombre() + "\"");
        }

        BigDecimal precioNuevo = esAnual(suscripcion.getCicloFacturacion()) && nuevoPlan.getPrecioAnual() != null
                ? nuevoPlan.getPrecioAnual()
                : nuevoPlan.getPrecioMensual();

        String planAnterior = suscripcion.getPlan().getNombre();
        String precioAnterior = suscripcion.getPrecio().toPlainString();
        suscripcion.setPlan(nuevoPlan);
        suscripcion.setPrecio(precioNuevo);
        Suscripcion guardada = suscripcionRepository.save(suscripcion);

        auditService.registrar("PLAN_CHANGE", "suscripciones", tenant, "suscripcion",
                guardada.getId(),
                "{ \"plan\": \"" + planAnterior + "\", \"precio\": " + precioAnterior + " }",
                "{ \"plan\": \"" + nuevoPlan.getNombre() + "\", \"precio\": " + precioNuevo.toPlainString()
                        + (request.motivo() == null || request.motivo().isBlank() ? "" : ", \"motivo\": \"" + request.motivo().trim() + "\"")
                        + " }");
        return SuscripcionResponse.desde(guardada, tenant.getNombre());
    }

    @Transactional
    public SuscripcionResponse suspender(Long id, String motivo) {
        Suscripcion suscripcion = suscripcionObligatoria(id);
        if (!ESTADOS_ACTIVOS.contains(suscripcion.getEstado())) {
            throw new IllegalArgumentException("Solo se puede suspender una suscripción activa (estado actual: "
                    + suscripcion.getEstado() + ")");
        }
        Tenant tenant = tenantObligatoria(suscripcion.getCompanyId());
        String estadoAnterior = suscripcion.getEstado();

        suscripcion.setEstado("SUSPENDED");
        suscripcionRepository.save(suscripcion);
        tenant.setStatus("SUSPENDED");
        tenant.setActivo(false);
        tenantRepository.save(tenant);

        auditService.registrar("SUBSCRIPTION_SUSPENDED", "suscripciones", tenant, "suscripcion",
                suscripcion.getId(),
                "{ \"estado\": \"" + estadoAnterior + "\" }",
                "{ \"estado\": \"SUSPENDED\", \"motivo\": \"" + motivo.trim() + "\" }");
        return SuscripcionResponse.desde(suscripcion, tenant.getNombre());
    }

    @Transactional
    public SuscripcionResponse reactivar(Long id, String motivo) {
        Suscripcion suscripcion = suscripcionObligatoria(id);
        if (!"SUSPENDED".equals(suscripcion.getEstado())) {
            throw new IllegalArgumentException("Solo se puede reactivar una suscripción suspendida (estado actual: "
                    + suscripcion.getEstado() + ")");
        }
        Tenant tenant = tenantObligatoria(suscripcion.getCompanyId());

        suscripcion.setEstado("ACTIVE");
        suscripcionRepository.save(suscripcion);
        tenant.setStatus("ACTIVE");
        tenant.setActivo(true);
        tenantRepository.save(tenant);

        auditService.registrar("SUBSCRIPTION_REACTIVATED", "suscripciones", tenant, "suscripcion",
                suscripcion.getId(),
                "{ \"estado\": \"SUSPENDED\" }",
                "{ \"estado\": \"ACTIVE\", \"motivo\": \"" + motivo.trim() + "\" }");
        return SuscripcionResponse.desde(suscripcion, tenant.getNombre());
    }

    private Suscripcion suscripcionObligatoria(Long id) {
        return suscripcionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe una suscripción con el id " + id));
    }

    private Tenant tenantObligatoria(Long companyId) {
        Tenant tenant = tenantRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("No existe una empresa con el id " + companyId));
        if (PLAN_PLATAFORMA.equals(tenant.getPlan())) {
            throw new SuscripcionConflictException("El tenant de plataforma no puede tener suscripciones");
        }
        return tenant;
    }

    private Plan planObligatorio(Long planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("No existe un plan con el id " + planId));
    }

    private String nombreEmpresa(Long companyId) {
        return tenantRepository.findById(companyId).map(Tenant::getNombre).orElse(null);
    }

    private String normalizarEstado(String estado, String porDefecto) {
        String normalizado = estado == null || estado.isBlank()
                ? porDefecto
                : estado.trim().toUpperCase();
        if (!ESTADOS.contains(normalizado)) {
            throw new IllegalArgumentException("Estado de suscripción inválido: " + estado);
        }
        return normalizado;
    }

    private String normalizarCiclo(String ciclo) {
        String normalizado = ciclo == null || ciclo.isBlank() ? "MONTHLY" : ciclo.trim().toUpperCase();
        if (!List.of("MONTHLY", "ANNUAL").contains(normalizado)) {
            throw new IllegalArgumentException("Ciclo de facturación inválido: " + ciclo);
        }
        return normalizado;
    }

    private boolean esAnual(String ciclo) {
        return "ANNUAL".equalsIgnoreCase(ciclo);
    }

    private Integer gracePeriodDays() {
        return configuracionRepository.findById(CLAVE_GRACE_PERIOD)
                .map(config -> leerEntero(config.getValor(), "gracePeriodDays"))
                .orElseThrow(() -> new IllegalStateException(
                        "No se encontró la configuración de período de gracia (" + CLAVE_GRACE_PERIOD + ")"));
    }

    private int leerEntero(String json, String campo) {
        try {
            JsonNode nodo = objectMapper.readTree(json);
            return nodo.get(campo).asInt();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Configuración inválida para \"" + campo + "\": " + json, ex);
        }
    }

    private String valorJson(Suscripcion suscripcion) {
        return "{ \"plan\": \"" + suscripcion.getPlan().getNombre()
                + "\", \"estado\": \"" + suscripcion.getEstado()
                + "\", \"fechaInicio\": \"" + suscripcion.getFechaInicio()
                + "\", \"fechaVencimiento\": \"" + suscripcion.getFechaVencimiento()
                + "\", \"precio\": " + suscripcion.getPrecio().toPlainString() + " }";
    }
}