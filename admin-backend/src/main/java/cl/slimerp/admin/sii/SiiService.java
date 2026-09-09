package cl.slimerp.admin.sii;

import cl.slimerp.admin.common.Paginated;
import cl.slimerp.admin.dte.VentaDte;
import cl.slimerp.admin.dte.VentaDteRepository;
import cl.slimerp.admin.tenant.Tenant;
import cl.slimerp.admin.tenant.TenantRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Estado de configuración SII por empresa (spec §15).
 *
 * El ERP aún no expone un subsistema SII/certificados, por lo que el estado se
 * calcula como NOT_CONFIGURED para todas las empresas. Cuando exista una fuente
 * real de certificados, este service la consumirá y derivará los demás estados
 * (CERTIFICATE_EXPIRING, CERTIFICATE_EXPIRED, CONNECTION_ERROR). Nunca se
 * devuelven secretos ni credenciales. */
@Service
public class SiiService {

    private static final String PLAN_PLATAFORMA = "plataforma";
    private static final List<String> ESTADOS = List.of(
            "CONFIGURED", "NOT_CONFIGURED", "CERTIFICATE_EXPIRING",
            "CERTIFICATE_EXPIRED", "CONNECTION_ERROR");

    private final TenantRepository tenantRepository;
    private final VentaDteRepository ventaDteRepository;

    public SiiService(TenantRepository tenantRepository, VentaDteRepository ventaDteRepository) {
        this.tenantRepository = tenantRepository;
        this.ventaDteRepository = ventaDteRepository;
    }

    @Transactional(readOnly = true)
    public Paginated<SiiEstadoResponse> listar(int page, int limit, String estado) {
        String estadoNorm = estado == null || estado.isBlank() ? null : estado.trim().toUpperCase();
        if (estadoNorm != null && !ESTADOS.contains(estadoNorm)) {
            throw new IllegalArgumentException("Estado SII inválido: " + estado
                    + " (disponibles: " + String.join(", ", ESTADOS) + ")");
        }

        Specification<Tenant> spec = (root, query, cb) -> cb.notEqual(root.get("plan"), PLAN_PLATAFORMA);
        if (estadoNorm != null && !"NOT_CONFIGURED".equals(estadoNorm)) {
            // Sin configuración SII real aún, solo el estado NOT_CONFIGURED coincide.
            spec = spec.and((root, query, cb) -> cb.disjunction());
        }

        int pagina = Math.max(0, page);
        int tamano = Math.min(Math.max(1, limit), 100);
        Page<Tenant> paginaTenants = tenantRepository.findAll(spec,
                PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.ASC, "nombre")));

        return Paginated.desde(paginaTenants.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public SiiEstadoResponse empresa(Long empresaId) {
        Tenant tenant = tenantRepository.findById(empresaId)
                .orElseThrow(() -> new IllegalArgumentException("No existe una empresa con el id " + empresaId));
        if (PLAN_PLATAFORMA.equals(tenant.getPlan())) {
            throw new IllegalArgumentException("El tenant de plataforma no corresponde a una empresa del ERP");
        }
        return toResponse(tenant);
    }

    private SiiEstadoResponse toResponse(Tenant tenant) {
        LocalDateTime ultimoDte = ventaDteRepository
                .findFirstByTenantIdOrderByFechaDesc(tenant.getId())
                .map(VentaDte::getFecha)
                .orElse(null);
        return new SiiEstadoResponse(
                tenant.getId(),
                tenant.getNombre(),
                "NOT_CONFIGURED",
                null,
                null,
                null,
                ultimoDte);
    }
}