package cl.slimerp.admin.usuarios;

import cl.slimerp.admin.auditoria.AuditService;
import cl.slimerp.admin.tenant.Rol;
import cl.slimerp.admin.tenant.Tenant;
import cl.slimerp.admin.tenant.TenantRepository;
import cl.slimerp.admin.tenant.Usuario;
import cl.slimerp.admin.tenant.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UsuarioAdminService {

    private static final String PLAN_PLATAFORMA = "plataforma";

    private final UsuarioRepository usuarioRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UsuarioAdminService(UsuarioRepository usuarioRepository,
                               TenantRepository tenantRepository,
                               PasswordEncoder passwordEncoder,
                               AuditService auditService) {
        this.usuarioRepository = usuarioRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    public List<UsuarioAdminResponse> listar(Long tenantId, Boolean activo) {
        List<Tenant> tenants = tenantRepository.findAll().stream()
                .filter(t -> !PLAN_PLATAFORMA.equals(t.getPlan()))
                .toList();
        if (tenantId != null && tenants.stream().noneMatch(t -> t.getId().equals(tenantId))) {
            return List.of();
        }

        List<Long> tenantIds = tenantId != null
                ? List.of(tenantId)
                : tenants.stream().map(Tenant::getId).toList();
        Map<Long, String> nombres = tenants.stream()
                .collect(Collectors.toMap(Tenant::getId, Tenant::getNombre));

        return usuarioRepository.findByTenantIdIn(tenantIds).stream()
                .filter(u -> activo == null || u.isActivo() == activo)
                .sorted(Comparator
                        .comparing((Usuario u) -> nombres.getOrDefault(u.getTenantId(), ""))
                        .thenComparing(Usuario::getNombre))
                .map(u -> UsuarioAdminResponse.desde(u, nombres.getOrDefault(u.getTenantId(), "")))
                .toList();
    }

    @Transactional
    public UsuarioAdminResponse crear(UsuarioAdminRequest request) {
        Tenant tenant = tenantGestionable(request.tenantId());
        validarRolAsignable(request.rol());
        if (usuarioRepository.existsByEmail(request.email())) {
            throw new UsuarioConflictException("Ya existe un usuario con el email " + request.email());
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new IllegalArgumentException("La contraseña es obligatoria al crear un usuario");
        }

        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .tenantId(tenant.getId())
                .nombre(request.nombre())
                .rut(request.rut())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .rol(request.rol())
                .activo(request.activo() == null || request.activo())
                .build());

        return UsuarioAdminResponse.desde(usuario, tenant.getNombre());
    }

    @Transactional
    public UsuarioAdminResponse actualizar(Long id, UsuarioAdminRequest request) {
        Usuario usuario = usuarioGestionable(id);
        validarRolAsignable(request.rol());
        if (!usuario.getEmail().equals(request.email()) && usuarioRepository.existsByEmail(request.email())) {
            throw new UsuarioConflictException("Ya existe un usuario con el email " + request.email());
        }

        usuario.setNombre(request.nombre());
        usuario.setRut(request.rut());
        usuario.setEmail(request.email());
        usuario.setRol(request.rol());
        if (request.activo() != null) {
            usuario.setActivo(request.activo());
        }
        if (request.password() != null && !request.password().isBlank()) {
            usuario.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        usuarioRepository.save(usuario);

        return UsuarioAdminResponse.desde(usuario, nombreEmpresa(usuario.getTenantId()));
    }

    @Transactional
    public UsuarioAdminResponse cambiarEstado(Long id, boolean activo, String motivo) {
        Usuario usuario = usuarioGestionable(id);
        usuario.setActivo(activo);
        usuarioRepository.save(usuario);

        String accion = activo ? "USER_ACTIVATED" : "USER_DISABLED";
        String motivoFinal = motivo == null || motivo.isBlank()
                ? (activo ? "Activación desde consola" : "Desactivación desde consola")
                : motivo;
        auditService.registrar(accion, "usuarios", tenantDe(usuario), "usuario", usuario.getId(),
                "{ \"activo\": " + !activo + " }",
                "{ \"activo\": " + activo + ", \"motivo\": \"" + motivoFinal + "\" }");
        return UsuarioAdminResponse.desde(usuario, nombreEmpresa(usuario.getTenantId()));
    }

    @Transactional
    public UsuarioAdminResponse bloquear(Long id, String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("El motivo del bloqueo es obligatorio");
        }
        Usuario usuario = usuarioGestionable(id);
        boolean estabaActivo = usuario.isActivo();
        usuario.setActivo(false);
        usuarioRepository.save(usuario);

        auditService.registrar("USER_BLOCKED", "usuarios", tenantDe(usuario), "usuario", usuario.getId(),
                "{\"activo\": " + estabaActivo + " }",
                "{\"activo\": false, \"motivo\": \"" + motivo + "\" }");
        return UsuarioAdminResponse.desde(usuario, nombreEmpresa(usuario.getTenantId()));
    }

    @Transactional
    public void revocarSesiones(Long id, String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("El motivo de la revocación es obligatorio");
        }
        Usuario usuario = usuarioGestionable(id);
        auditService.registrar("SESSION_REVOKED", "usuarios", tenantDe(usuario), "usuario", usuario.getId(),
                null, "{\"motivo\": \"" + motivo + "\" }");
    }

    @Transactional
    public void resetearPassword(Long id, String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("La nueva contraseña es obligatoria");
        }
        Usuario usuario = usuarioGestionable(id);
        usuario.setPasswordHash(passwordEncoder.encode(password));
        usuarioRepository.save(usuario);
    }

    private void validarRolAsignable(Rol rol) {
        if (rol == Rol.SUPER_ADMIN) {
            throw new IllegalArgumentException("No se puede asignar el rol SUPER_ADMIN desde la consola");
        }
    }

    private Tenant tenantGestionable(Long tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Debe indicar la empresa del usuario");
        }
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Empresa no encontrada"));
        if (PLAN_PLATAFORMA.equals(tenant.getPlan())) {
            throw new IllegalArgumentException("Los usuarios de la plataforma no pueden administrarse aquí");
        }
        return tenant;
    }

    private Usuario usuarioGestionable(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        Tenant tenant = tenantRepository.findById(usuario.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Empresa no encontrada"));
        if (PLAN_PLATAFORMA.equals(tenant.getPlan())) {
            throw new IllegalArgumentException("Los usuarios de la plataforma no pueden administrarse aquí");
        }
        return usuario;
    }

    private String nombreEmpresa(Long tenantId) {
        return tenantRepository.findById(tenantId).map(Tenant::getNombre).orElse("");
    }

    private Tenant tenantDe(Usuario usuario) {
        return tenantRepository.findById(usuario.getTenantId()).orElse(null);
    }
}