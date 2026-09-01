package cl.slimerp.admin;

import cl.slimerp.tenant.Rol;
import cl.slimerp.tenant.Tenant;
import cl.slimerp.tenant.TenantRepository;
import cl.slimerp.tenant.Usuario;
import cl.slimerp.tenant.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EmpresaAdminService {

    private static final String PLAN_PLATAFORMA = "plataforma";

    private final TenantRepository tenantRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public EmpresaAdminService(TenantRepository tenantRepository, UsuarioRepository usuarioRepository,
                                PasswordEncoder passwordEncoder) {
        this.tenantRepository = tenantRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Tenant> listar() {
        return tenantRepository.findAll().stream()
                .filter(t -> !PLAN_PLATAFORMA.equals(t.getPlan()))
                .toList();
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

        return tenant;
    }

    @Transactional
    public Tenant activar(Long id) {
        return cambiarEstado(id, true);
    }

    @Transactional
    public Tenant desactivar(Long id) {
        return cambiarEstado(id, false);
    }

    public Tenant obtenerGestionable(Long id) {
        return tenantGestionable(id);
    }

    private Tenant cambiarEstado(Long id, boolean activo) {
        Tenant tenant = tenantGestionable(id);
        tenant.setActivo(activo);
        return tenantRepository.save(tenant);
    }

    private Tenant tenantGestionable(Long id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Empresa no encontrada"));
        if (PLAN_PLATAFORMA.equals(tenant.getPlan())) {
            throw new EmpresaConflictException("El tenant de plataforma no puede administrarse aquí");
        }
        return tenant;
    }
}
