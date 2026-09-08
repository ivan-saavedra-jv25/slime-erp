package cl.slimerp.admin.empresas;

import cl.slimerp.admin.alerta.AlertaService;
import cl.slimerp.admin.auditoria.AuditService;
import cl.slimerp.admin.cobranza.CobranzaService;
import cl.slimerp.admin.common.Paginated;
import cl.slimerp.admin.suscripcion.Suscripcion;
import cl.slimerp.admin.suscripcion.SuscripcionRepository;
import cl.slimerp.admin.tenant.Rol;
import cl.slimerp.admin.tenant.Tenant;
import cl.slimerp.admin.tenant.TenantRepository;
import cl.slimerp.admin.tenant.Usuario;
import cl.slimerp.admin.tenant.UsuarioRepository;
import cl.slimerp.admin.alerta.AlertaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class EmpresaAdminService {

    private static final String PLAN_PLATAFORMA = "plataforma";

    private final TenantRepository tenantRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final CobranzaService cobranzaService;
    private final AuditService auditService;
    private final AlertaService alertaService;
    private final SuscripcionRepository suscripcionRepository;
    private final AlertaRepository alertaRepository;

    public EmpresaAdminService(TenantRepository tenantRepository,
                               UsuarioRepository usuarioRepository,
                               PasswordEncoder passwordEncoder,
                               CobranzaService cobranzaService,
                               AuditService auditService,
                               AlertaService alertaService,
                               SuscripcionRepository suscripcionRepository,
                               AlertaRepository alertaRepository) {
        this.tenantRepository = tenantRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.cobranzaService = cobranzaService;
        this.auditService = auditService;
        this.alertaService = alertaService;
        this.suscripcionRepository = suscripcionRepository;
        this.alertaRepository = alertaRepository;
    }

    public Paginated<EmpresaResponse> listar(int page, int limit, Long id, String rut, String razonSocial,
                                             String nombreComercial, String estado, String plan,
                                             LocalDateTime fechaCreacionDesde) {
        Specification<Tenant> spec = (root, query, cb) -> cb.notEqual(root.get("plan"), PLAN_PLATAFORMA);
        if (id != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("id"), id));
        }
        if (rut != null && !rut.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("rut")), "%" + rut.toLowerCase() + "%"));
        }
        if (razonSocial != null && !razonSocial.isBlank()) {
            spec = spec.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("nombre")), "%" + razonSocial.toLowerCase() + "%"));
        }
        if (nombreComercial != null && !nombreComercial.isBlank()) {
            spec = spec.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("businessName")), "%" + nombreComercial.toLowerCase() + "%"));
        }
        if (estado != null && !estado.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), estado));
        }
        if (plan != null && !plan.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("plan"), plan));
        }
        if (fechaCreacionDesde != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("fechaAlta"), fechaCreacionDesde));
        }

        int pagina = Math.max(0, page);
        int tamano = Math.min(Math.max(1, limit), 100);
        Page<Tenant> paginaEmpresas = tenantRepository.findAll(spec,
                PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.DESC, "fechaAlta")));

        return Paginated.desde(paginaEmpresas.map(this::conKpis));
    }

    public EmpresaDetalleResponse detalle(Long id) {
        Tenant tenant = tenantGestionable(id);
        long usuariosTotales = usuarioRepository.countByTenantId(tenant.getId());
        long usuariosActivos = usuarioRepository.countByTenantIdAndActivoTrue(tenant.getId());
        BigDecimal saldoPendiente = cobranzaService.saldoPendienteTenant(tenant.getId());

        String suscripcionEstado = suscripcionRepository.findByCompanyIdOrderByFechaInicioDesc(tenant.getId())
                .stream()
                .findFirst()
                .map(Suscripcion::getEstado)
                .orElse(null);
        long alertasAbiertas = alertaRepository.countByCompanyIdAndStatus(tenant.getId(), "OPEN");

        return new EmpresaDetalleResponse(
                tenant.getId(), tenant.getNombre(), tenant.getRut(), tenant.getBusinessName(),
                tenant.getPlan(), tenant.getStatus(), tenant.isActivo(), tenant.getFechaAlta(),
                tenant.getLastAccessAt(), usuariosTotales, usuariosActivos, saldoPendiente,
                suscripcionEstado, alertasAbiertas);
    }

    public long contarEmpresas() {
        return tenantRepository.count(noPlataforma());
    }

    public long contarEmpresasActivas() {
        Specification<Tenant> spec = noPlataforma()
                .and((root, query, cb) -> cb.isTrue(root.get("activo")));
        return tenantRepository.count(spec);
    }

    private Specification<Tenant> noPlataforma() {
        return (root, query, cb) -> cb.notEqual(root.get("plan"), PLAN_PLATAFORMA);
    }

    @Transactional
    public Tenant crear(CrearEmpresaRequest request) {
        if (tenantRepository.findByRut(request.rut()).isPresent()) {
            throw new EmpresaConflictException("Ya existe una empresa con el RUT " + request.rut());
        }
        if (usuarioRepository.existsByEmail(request.adminEmail())) {
            throw new EmpresaConflictException("Ya existe un usuario con el email " + request.adminEmail());
        }

        String plan = (request.plan() == null || request.plan().isBlank()) ? "basico" : request.plan();
        if (PLAN_PLATAFORMA.equals(plan)) {
            throw new EmpresaConflictException("No se puede crear una empresa con el plan de plataforma");
        }

        Tenant tenant = tenantRepository.save(Tenant.builder()
                .nombre(request.nombre())
                .rut(request.rut())
                .plan(plan)
                .status("ACTIVE")
                .activo(true)
                .build());

        Usuario admin = Usuario.builder()
                .tenantId(tenant.getId())
                .email(request.adminEmail())
                .rut(request.adminRut())
                .passwordHash(passwordEncoder.encode(request.adminPassword()))
                .nombre(request.adminNombre())
                .rol(Rol.ADMIN)
                .build();
        usuarioRepository.save(admin);

        auditService.registrar("EMPRESA_CREADA", "empresas", tenant, "tenant", tenant.getId(),
                null, valorJson(tenant));
        return tenant;
    }

    @Transactional
    public Tenant activar(Long id) {
        return cambiarEstado(id, EstadoEmpresa.ACTIVE, "Activación manual desde consola");
    }

    @Transactional
    public Tenant desactivar(Long id) {
        return cambiarEstado(id, EstadoEmpresa.SUSPENDED, "Suspensión manual desde consola");
    }

    @Transactional
    public Tenant cambiarEstado(Long id, EstadoEmpresa estado, String motivo) {
        Tenant tenant = tenantGestionable(id);
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("El motivo del cambio de estado es obligatorio");
        }

        String estadoAnterior = tenant.getStatus();
        tenant.setStatus(estado.name());
        tenant.setActivo(estado.esActivo());
        Tenant guardado = tenantRepository.save(tenant);

        auditService.registrar("EMPRESA_ESTADO_CAMBIADO", "empresas", tenant, "tenant", tenant.getId(),
                "{ \"status\": \"" + estadoAnterior + "\" }",
                "{ \"status\": \"" + estado.name() + "\", \"motivo\": \"" + motivo + "\" }");

        if (estado == EstadoEmpresa.SUSPENDED || estado == EstadoEmpresa.BLOCKED) {
            alertaService.crear(tenant.getId(), "CRITICAL", "EMPRESA_" + estado.name(),
                    "Empresa " + estado.name().toLowerCase(),
                    motivo);
        } else if (estado == EstadoEmpresa.EXPIRED) {
            alertaService.crear(tenant.getId(), "WARNING", "EMPRESA_EXPIRED",
                    "Empresa vencida", motivo);
        }
        return guardado;
    }

    public EmpresaResponse conKpis(Tenant tenant) {
        long usuariosActivos = usuarioRepository.countByTenantIdAndActivoTrue(tenant.getId());
        BigDecimal saldoPendiente = cobranzaService.saldoPendienteTenant(tenant.getId());
        return EmpresaResponse.desde(tenant, usuariosActivos, saldoPendiente);
    }

    private Tenant tenantGestionable(Long id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Empresa no encontrada"));
        if (PLAN_PLATAFORMA.equals(tenant.getPlan())) {
            throw new EmpresaConflictException("El tenant de plataforma no puede administrarse aquí");
        }
        return tenant;
    }

    private String valorJson(Tenant tenant) {
        return "{ \"nombre\": \"" + tenant.getNombre() + "\", \"rut\": \"" + tenant.getRut()
                + "\", \"plan\": \"" + tenant.getPlan() + "\" }";
    }
}