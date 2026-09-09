package cl.slimerp.admin.alerta;

import cl.slimerp.admin.auditoria.AuditService;
import cl.slimerp.admin.common.Paginated;
import cl.slimerp.admin.tenant.TenantRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AlertaService {

    private static final Set<String> SEVERIDADES = Set.of("CRITICAL", "WARNING", "INFO");
    private static final String ESTADO_ABIERTA = "OPEN";

    private final AlertaRepository alertaRepository;
    private final TenantRepository tenantRepository;
    private final AuditService auditService;

    public AlertaService(AlertaRepository alertaRepository,
                         TenantRepository tenantRepository,
                         AuditService auditService) {
        this.alertaRepository = alertaRepository;
        this.tenantRepository = tenantRepository;
        this.auditService = auditService;
    }

    public Paginated<AlertaResponse> listar(int page, int limit, String severity, String status,
                                            Long companyId, String tipo) {
        Specification<Alerta> spec = Specification.where(null);
        if (severity != null && !severity.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("severity"), severity));
        }
        if (status != null && !status.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (companyId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("companyId"), companyId));
        }
        if (tipo != null && !tipo.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("tipo"), tipo));
        }

        int pagina = Math.max(0, page);
        int tamano = Math.min(Math.max(1, limit), 100);
        Pageable pageable = PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.DESC, "creadaEn"));
        Page<Alerta> resultados = alertaRepository.findAll(spec, pageable);

        Map<Long, String> nombresEmpresa = nombresEmpresaDe(resultados.getContent());
        return Paginated.desde(resultados.map(a -> AlertaResponse.desde(a, nombresEmpresa.get(a.getCompanyId()))));
    }

    @Transactional
    public AlertaResponse marcarLeida(Long id) {
        Alerta alerta = alertaObligatoria(id);
        alerta.setStatus("READ");
        alerta.setLeidaEn(LocalDateTime.now());
        return AlertaResponse.desde(alertaRepository.save(alerta), nombreEmpresa(alerta.getCompanyId()));
    }

    @Transactional
    public AlertaResponse resolver(Long id, String motivo) {
        Alerta alerta = alertaObligatoria(id);
        String estadoAnterior = alerta.getStatus();
        alerta.setStatus("RESOLVED");
        alerta.setResueltaEn(LocalDateTime.now());
        Alerta guardada = alertaRepository.save(alerta);

        auditService.registrar("ALERTA_RESUELTA", "alertas",
                alerta.getCompanyId() != null
                        ? tenantRepository.findById(alerta.getCompanyId()).orElse(null)
                        : null,
                "alerta", alerta.getId(),
                "{ \"status\": \"" + estadoAnterior + "\" }",
                "{ \"status\": \"RESOLVED\", \"motivo\": \"" + (motivo == null ? "" : motivo) + "\" }");

        return AlertaResponse.desde(guardada, nombreEmpresa(guardada.getCompanyId()));
    }

    @Transactional
    public Alerta crear(Long companyId, String severity, String tipo, String titulo, String descripcion) {
        if (severity == null || !SEVERIDADES.contains(severity.toUpperCase())) {
            throw new IllegalArgumentException("Severidad de alerta inválida: " + severity);
        }
        if (tipo == null || tipo.isBlank()) {
            throw new IllegalArgumentException("El tipo de alerta es obligatorio");
        }

        boolean duplicada = companyId != null
                ? alertaRepository.existsByCompanyIdAndTipoAndTituloAndSeverityAndStatus(
                        companyId, tipo, titulo, severity.toUpperCase(), ESTADO_ABIERTA)
                : alertaRepository.existsByCompanyIdIsNullAndTipoAndTituloAndSeverityAndStatus(
                        tipo, titulo, severity.toUpperCase(), ESTADO_ABIERTA);
        if (duplicada) {
            return null;
        }

        return alertaRepository.save(Alerta.builder()
                .companyId(companyId)
                .severity(severity.toUpperCase())
                .tipo(tipo)
                .titulo(titulo)
                .descripcion(descripcion)
                .status(ESTADO_ABIERTA)
                .build());
    }

    public Map<String, Long> resumenAbiertas() {
        return Map.of(
                "critical", alertaRepository.countBySeverityAndStatus("CRITICAL", ESTADO_ABIERTA),
                "warning", alertaRepository.countBySeverityAndStatus("WARNING", ESTADO_ABIERTA),
                "info", alertaRepository.countBySeverityAndStatus("INFO", ESTADO_ABIERTA));
    }

    private Alerta alertaObligatoria(Long id) {
        return alertaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe una alerta con el id " + id));
    }

    private Map<Long, String> nombresEmpresaDe(List<Alerta> alertas) {
        Set<Long> ids = alertas.stream()
                .map(Alerta::getCompanyId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return tenantRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(tenant -> tenant.getId(), tenant -> tenant.getNombre(), (a, b) -> a));
    }

    private String nombreEmpresa(Long companyId) {
        if (companyId == null) {
            return null;
        }
        return tenantRepository.findById(companyId)
                .map(tenant -> tenant.getNombre())
                .orElse(null);
    }
}