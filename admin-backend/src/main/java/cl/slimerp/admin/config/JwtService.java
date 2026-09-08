package cl.slimerp.admin.config;

import cl.slimerp.admin.rbac.AdminRol;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Valida los JWT emitidos por el backend de negocio y emite tokens admin.
 * Ambos servicios comparten el mismo {@code JWT_SECRET}.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMinutes;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-minutes:480}") long expirationMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String generarTokenAdmin(Long adminId, String email, AdminRol rol) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .claim("adminId", adminId)
                .claim("adminEmail", email)
                .claim("adminRol", rol.name())
                .claim("email", email)
                .claim("rol", rol.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }
}