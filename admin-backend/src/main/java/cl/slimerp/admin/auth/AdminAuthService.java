package cl.slimerp.admin.auth;

import cl.slimerp.admin.config.JwtService;
import cl.slimerp.admin.rbac.AdminPermisos;
import cl.slimerp.admin.rbac.AdminRol;
import cl.slimerp.admin.usuario.AdminSesion;
import cl.slimerp.admin.usuario.AdminSesionRepository;
import cl.slimerp.admin.usuario.AdminUsuario;
import cl.slimerp.admin.usuario.AdminUsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AdminAuthService {

    static final int MAX_INTENTOS = 5;

    private final AdminUsuarioRepository adminUsuarioRepository;
    private final AdminSesionRepository adminSesionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long expirationMinutes;

    private final ConcurrentHashMap<String, AtomicInteger> intentosFallidos = new ConcurrentHashMap<>();

    public AdminAuthService(AdminUsuarioRepository adminUsuarioRepository,
                            AdminSesionRepository adminSesionRepository,
                            PasswordEncoder passwordEncoder,
                            JwtService jwtService,
                            @Value("${app.jwt.expiration-minutes:480}") long expirationMinutes) {
        this.adminUsuarioRepository = adminUsuarioRepository;
        this.adminSesionRepository = adminSesionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.expirationMinutes = expirationMinutes;
    }

    @Transactional
    public AdminAuthResponse login(String email, String password, String ip, String userAgent) {
        if (bloqueado(email)) {
            throw new IllegalStateException("Cuenta temporalmente bloqueada por demasiados intentos fallidos");
        }

        AdminUsuario usuario = adminUsuarioRepository.findByEmail(email.trim())
                .filter(AdminUsuario::isActivo)
                .orElseThrow(() -> falloLogin(email));

        if (!passwordEncoder.matches(password, usuario.getPasswordHash())) {
            throw falloLogin(email);
        }

        intentosFallidos.remove(email);
        usuario.setUltimoAcceso(LocalDateTime.now());
        adminUsuarioRepository.save(usuario);

        String token = jwtService.generarTokenAdmin(usuario.getId(), usuario.getEmail(), usuario.getRol());
        String hash = sha256(token);
        adminSesionRepository.save(AdminSesion.builder()
                .adminUsuarioId(usuario.getId())
                .tokenHash(hash)
                .ip(ip)
                .userAgent(userAgent)
                .expiraEn(LocalDateTime.now().plusMinutes(expirationMinutes))
                .build());

        return new AdminAuthResponse(
                token,
                usuario.getId(),
                usuario.getNombre(),
                usuario.getEmail(),
                usuario.getRol().name(),
                AdminPermisos.permisosDe(usuario.getRol()).stream().sorted().toList());
    }

    @Transactional
    public boolean logout(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return adminSesionRepository.findByTokenHash(sha256(token))
                .map(sesion -> {
                    if (sesion.getRevocadaEn() == null) {
                        sesion.setRevocadaEn(LocalDateTime.now());
                        adminSesionRepository.save(sesion);
                    }
                    return true;
                })
                .orElse(false);
    }

    private boolean bloqueado(String email) {
        AtomicInteger contador = intentosFallidos.get(email);
        return contador != null && contador.get() >= MAX_INTENTOS;
    }

    private IllegalStateException falloLogin(String email) {
        intentosFallidos.computeIfAbsent(email, k -> new AtomicInteger()).incrementAndGet();
        return new IllegalStateException("Credenciales inválidas");
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }
}