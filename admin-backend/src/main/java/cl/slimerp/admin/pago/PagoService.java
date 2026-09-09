package cl.slimerp.admin.pago;

import cl.slimerp.admin.auditoria.AuditService;
import cl.slimerp.admin.cobranza.CobranzaEmpresa;
import cl.slimerp.admin.cobranza.CobranzaEmpresaRepository;
import cl.slimerp.admin.cobranza.CobranzaPago;
import cl.slimerp.admin.cobranza.CobranzaPagoRepository;
import cl.slimerp.admin.cobranza.CobranzaService;
import cl.slimerp.admin.cobranza.EstadoCobranza;
import cl.slimerp.admin.cobranza.EstadoPagoCobranza;
import cl.slimerp.admin.cobranza.PagoCobranzaRequest;
import cl.slimerp.admin.common.Paginated;
import cl.slimerp.admin.tenant.Tenant;
import cl.slimerp.admin.tenant.TenantRepository;
import cl.slimerp.admin.usuario.AdminUsuarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PagoService {

    private static final String PLAN_PLATAFORMA = "plataforma";
    private static final String MODULO = "pagos";
    private static final String ACCION_REGISTRAR = "PAYMENT_REGISTERED";
    private static final List<EstadoCobranza> ESTADOS_COBRANZA_ACTIVA =
            List.of(EstadoCobranza.DEUDA, EstadoCobranza.PARCIAL);

    private final CobranzaEmpresaRepository cobranzaEmpresaRepository;
    private final CobranzaPagoRepository cobranzaPagoRepository;
    private final CobranzaService cobranzaService;
    private final TenantRepository tenantRepository;
    private final AdminUsuarioRepository adminUsuarioRepository;
    private final AuditService auditService;

    public PagoService(CobranzaEmpresaRepository cobranzaEmpresaRepository,
                       CobranzaPagoRepository cobranzaPagoRepository,
                       CobranzaService cobranzaService,
                       TenantRepository tenantRepository,
                       AdminUsuarioRepository adminUsuarioRepository,
                       AuditService auditService) {
        this.cobranzaEmpresaRepository = cobranzaEmpresaRepository;
        this.cobranzaPagoRepository = cobranzaPagoRepository;
        this.cobranzaService = cobranzaService;
        this.tenantRepository = tenantRepository;
        this.adminUsuarioRepository = adminUsuarioRepository;
        this.auditService = auditService;
    }

    public Paginated<PagoResponse> listar(int page, int limit, Long empresaId, String estado) {
        Specification<CobranzaPago> spec = Specification.where(null);
        if (empresaId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("tenantId"), empresaId));
        }
        Set<EstadoPagoCobranza> estados = normalizarEstado(estado);
        if (estados != null) {
            spec = spec.and((root, query, cb) -> root.get("estado").in(estados));
        }

        int pagina = Math.max(0, page);
        int tamano = Math.min(Math.max(1, limit), 100);
        Pageable pageable = PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.DESC, "fecha"));
        Page<CobranzaPago> resultados = cobranzaPagoRepository.findAll(spec, pageable);

        Map<Long, String> nombresEmpresa = nombresEmpresa(resultados.getContent());
        Map<Long, String> nombresAdmin = nombresAdmin(resultados.getContent());
        return Paginated.desde(resultados.map(p ->
                PagoResponse.desde(p, nombresEmpresa.get(p.getTenantId()), nombresAdmin.get(p.getUsuarioAdminId()))));
    }

    public PagoResponse obtener(Long id) {
        CobranzaPago pago = cobranzaPagoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pago no encontrado: " + id));
        return PagoResponse.desde(pago, nombreEmpresa(pago.getTenantId()), nombreAdmin(pago.getUsuarioAdminId()));
    }

    @Transactional
    public PagoResponse registrarManual(RegistrarPagoManualRequest request, Long usuarioAdminId) {
        Tenant tenant = tenantRepository.findById(request.companyId())
                .orElseThrow(() -> new IllegalArgumentException("Empresa no encontrada: " + request.companyId()));
        if (PLAN_PLATAFORMA.equals(tenant.getPlan())) {
            throw new IllegalArgumentException("No se puede registrar un pago al tenant de plataforma");
        }

        CobranzaEmpresa cobranza = cobranzaEmpresaRepository
                .findFirstByTenantIdAndEstadoInOrderByFechaEmisionDesc(request.companyId(), ESTADOS_COBRANZA_ACTIVA)
                .orElseGet(() -> emitirCobranzaAutomatica(request));

        CobranzaPago pago = cobranzaService.registrarPago(
                cobranza.getId(),
                new PagoCobranzaRequest(request.monto(), request.metodo(), request.referencia(), null),
                usuarioAdminId);

        if (request.fecha() != null) {
            pago.setFecha(request.fecha());
            cobranzaPagoRepository.save(pago);
        }

        auditService.registrar(ACCION_REGISTRAR, MODULO,
                tenant, "pago", pago.getId(),
                "{}",
                jsonRegistro(request, pago));

        return PagoResponse.desde(pago, tenant.getNombre(), nombreAdmin(usuarioAdminId));
    }

    private CobranzaEmpresa emitirCobranzaAutomatica(RegistrarPagoManualRequest request) {
        String periodo = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        return cobranzaEmpresaRepository.save(CobranzaEmpresa.builder()
                .tenantId(request.companyId())
                .concepto("Suscripción plataforma")
                .periodo(periodo)
                .montoTotal(request.monto())
                .saldoPendiente(request.monto())
                .build());
    }

    private String jsonRegistro(RegistrarPagoManualRequest request, CobranzaPago pago) {
        StringBuilder sb = new StringBuilder("{ \"companyId\": ").append(request.companyId());
        if (request.suscripcionId() != null) {
            sb.append(", \"suscripcionId\": ").append(request.suscripcionId());
        }
        sb.append(", \"monto\": ").append(request.monto());
        sb.append(", \"metodo\": \"").append(request.metodo()).append('"');
        if (request.referencia() != null && !request.referencia().isBlank()) {
            sb.append(", \"referencia\": \"").append(request.referencia()).append('"');
        }
        sb.append(", \"estado\": \"").append(PagoResponse.estadoEspec(pago.getEstado())).append("\" }");
        return sb.toString();
    }

    private Set<EstadoPagoCobranza> normalizarEstado(String estado) {
        if (estado == null || estado.isBlank()) {
            return null;
        }
        return switch (estado) {
            case "PAID", "CONFIRMADA" -> Set.of(EstadoPagoCobranza.CONFIRMADA);
            case "CANCELLED", "ANULADA" -> Set.of(EstadoPagoCobranza.ANULADA);
            default -> throw new IllegalArgumentException("Estado de pago inválido: " + estado);
        };
    }

    private Map<Long, String> nombresEmpresa(List<CobranzaPago> pagos) {
        Set<Long> ids = pagos.stream().map(CobranzaPago::getTenantId).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return tenantRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(tenant -> tenant.getId(), tenant -> tenant.getNombre(), (a, b) -> a));
    }

    private Map<Long, String> nombresAdmin(List<CobranzaPago> pagos) {
        Set<Long> ids = pagos.stream()
                .map(CobranzaPago::getUsuarioAdminId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return adminUsuarioRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(usuario -> usuario.getId(), usuario -> usuario.getNombre(), (a, b) -> a));
    }

    private String nombreEmpresa(Long companyId) {
        return companyId == null ? null
                : tenantRepository.findById(companyId).map(Tenant::getNombre).orElse(null);
    }

    private String nombreAdmin(Long adminId) {
        return adminId == null ? null
                : adminUsuarioRepository.findById(adminId).map(usuario -> usuario.getNombre()).orElse(null);
    }
}