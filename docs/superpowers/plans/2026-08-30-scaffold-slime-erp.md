# Scaffold slime-erp Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up `slime-erp` (backend + frontend) reusing the tenant/auth configuration and design system from `slime-erp_old`, adding a fixed-role permission system with a users module, a basic Cliente/Producto catalog, and replacing Tailwind with a custom Angular Material theme built on the existing design tokens.

**Architecture:** Spring Boot 21/Maven backend (`cl.slimerp`) with JWT auth, a `TenantContext` thread-local for multi-tenant filtering, and Spring Security method security (`@PreAuthorize`) driven by a fixed `Rol → Permiso` map. Angular 18 standalone-component frontend with a real login flow, a permission-aware shell, and Angular Material components themed via CSS custom properties that already exist as design tokens. Single Postgres instance, Flyway-versioned schema, Docker Compose for local/prod parity.

**Tech Stack:** Java 21, Spring Boot 3.3.4, Spring Security, Spring Data JPA, Flyway, PostgreSQL 16, JJWT 0.12.6, Lombok, JUnit 5 + Mockito — Angular 18 (standalone components), Angular Material + CDK, RxJS, Karma/Jasmine, Docker Compose.

**Spec:** `docs/superpowers/specs/2026-08-30-scaffold-desde-slime-erp-old-design.md`

## Global Constraints

- Backend package stays `cl.slimerp` (decision confirmed during brainstorming — do not rename).
- Database is PostgreSQL, single shared instance, `tenant_id` column on every business table, Flyway-versioned migrations.
- **No DTE / facturación electrónica / XML / CAF / certificados anywhere**, in backend or frontend — not even as unused leftover fields or endpoints.
- Roles are fixed in code (`SUPER_ADMIN, ADMIN, VENDEDOR, COMPRADOR, VISUALIZADOR`), permissions are fixed in code (no per-tenant configurability in this phase).
- Backend endpoints are protected by permission (`@PreAuthorize("hasAuthority('X')")`), not just by authentication.
- Frontend login is fully functional against the real backend (not visual-only — this was upgraded from the original spec draft once permission-based menu filtering was added).
- Tailwind is fully removed; Angular Material is themed with the existing tokens in `frontend/src/styles/tokens/*.css`, not Material's default palette.
- Branding text changes from "Slim ERP" to "Slime ERP" everywhere it appears in the UI.
- Do not commit any change during implementation without the user reviewing it first — leave changes staged/unstaged and ask, per explicit user instruction in this session.

## Task Overview

**Backend**
1. Backend project skeleton
2. Tenant domain + V1 migration (tenant, usuario)
3. Security/JWT scaffolding (JwtService, TenantContext, SecurityConfig, base GlobalExceptionHandler)
4. Permisos module (Permiso enum + RolPermisos)
5. JwtAuthFilter: add permission authorities
6. Auth module (login includes permisos)
7. Catálogo: Cliente
8. Catálogo: Producto (+ V1 migration append)
9. Usuarios module
10. Admin module (empresas, DTE-free)
11. Backend Docker Compose + end-to-end smoke test

**Frontend**
12. Frontend project skeleton (no Tailwind)
13. Angular Material install + custom theme on tokens
14. core/models
15. auth.service
16. auth.interceptor
17. auth.guard
18. Routes + app.config wiring
19. Layout shell (Material, permission-filtered menu)
20. Login screen (functional, Material)
21. Dashboard screen (static, Material)
22. Usuarios feature (list + dialog)
23. Clientes feature (list + dialog)
24. Productos feature (list + dialog)
25. Full-stack Docker Compose + end-to-end smoke test

---

## Task 1: Backend project skeleton

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/Dockerfile`
- Create: `backend/src/main/java/cl/slimerp/SlimErpApplication.java`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/application-docker.yml`

**Interfaces:**
- Produces: a Maven project that compiles and boots an empty Spring Boot app on port 8080 (no DB yet — Flyway/JPA are on the classpath but there are no entities or migrations until Task 2, so `spring.jpa`/`spring.flyway` config in `application.yml` is written now but only exercised starting Task 2).

- [ ] **Step 1: Create `backend/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.4</version>
        <relativePath/>
    </parent>

    <groupId>cl.slimerp</groupId>
    <artifactId>slim-erp-backend</artifactId>
    <version>0.1.0</version>
    <name>slim-erp-backend</name>
    <description>Backend multi-tenant para Slime ERP</description>

    <properties>
        <java.version>21</java.version>
        <jjwt.version>0.12.6</jjwt.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
        </dependency>

        <!-- JWT -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>

        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <finalName>slim-erp-backend</finalName>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create `backend/Dockerfile`**

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/slim-erp-backend.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=docker"]
```

- [ ] **Step 3: Create `backend/src/main/java/cl/slimerp/SlimErpApplication.java`**

```java
package cl.slimerp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SlimErpApplication {

    public static void main(String[] args) {
        SpringApplication.run(SlimErpApplication.class, args);
    }
}
```

- [ ] **Step 4: Create `backend/src/main/resources/application.yml`**

```yaml
server:
  port: 8080

spring:
  application:
    name: slim-erp-backend

  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:slim_erp}
    username: ${DB_USER:slim_erp}
    password: ${DB_PASSWORD:slim_erp}
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate:
        format_sql: true
        default_schema: public

  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

app:
  jwt:
    secret: ${JWT_SECRET:CAMBIA_ESTE_SECRETO_EN_PRODUCCION_POR_UNO_LARGO_Y_ALEATORIO}
    expiration-minutes: ${JWT_EXPIRATION_MINUTES:480}

logging:
  level:
    cl.slimerp: INFO
```

- [ ] **Step 5: Create `backend/src/main/resources/application-docker.yml`**

```yaml
spring:
  datasource:
    url: jdbc:postgresql://db:5432/${DB_NAME:slim_erp}
```

- [ ] **Step 6: Verify it compiles**

Run: `cd backend && mvn -q -B compile`
Expected: `BUILD SUCCESS`, no source files exist yet besides `SlimErpApplication`, no errors.

- [ ] **Step 7: Commit reminder**

Do not commit. Tell the user Task 1 is ready for review (per Global Constraints).

---

## Task 2: Tenant domain + V1 migration (tenant, usuario)

**Files:**
- Create: `backend/src/main/java/cl/slimerp/tenant/Tenant.java`
- Create: `backend/src/main/java/cl/slimerp/tenant/Usuario.java`
- Create: `backend/src/main/java/cl/slimerp/tenant/Rol.java`
- Create: `backend/src/main/java/cl/slimerp/tenant/TenantRepository.java`
- Create: `backend/src/main/java/cl/slimerp/tenant/UsuarioRepository.java`
- Create: `backend/src/main/resources/db/migration/V1__init.sql`
- Test: `backend/src/test/java/cl/slimerp/tenant/TenantUsuarioDefaultsTest.java`

**Interfaces:**
- Produces: `Tenant` (id, nombre, rut, plan, activo, fechaAlta), `Usuario` (id, tenantId, email, rut, passwordHash, nombre, rol, activo, fechaCreacion), `Rol` enum (`SUPER_ADMIN, ADMIN, VENDEDOR, COMPRADOR, VISUALIZADOR`), `TenantRepository.findByRut(String)`, `UsuarioRepository.findByEmailAndTenantId(String,Long)`, `.findFirstByEmailAndActivoTrue(String)`, `.existsByEmail(String)` — all consumed by later tasks (auth, admin, usuarios, catalogo).

- [ ] **Step 1: Write the failing test for entity defaults**

```java
package cl.slimerp.tenant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TenantUsuarioDefaultsTest {

    @Test
    void tenantUsaPlanBasicoYActivoPorDefecto() {
        Tenant tenant = Tenant.builder().nombre("Empresa Demo").rut("76.123.456-7").build();

        assertEquals("basico", tenant.getPlan());
        assertTrue(tenant.isActivo());
        assertNotNull(tenant.getFechaAlta());
    }

    @Test
    void usuarioUsaRolAdminYActivoPorDefecto() {
        Usuario usuario = Usuario.builder()
                .tenantId(1L).email("admin@demo.cl").rut("1-9").passwordHash("hash").nombre("Admin")
                .build();

        assertEquals(Rol.ADMIN, usuario.getRol());
        assertTrue(usuario.isActivo());
        assertNotNull(usuario.getFechaCreacion());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q -B test -Dtest=TenantUsuarioDefaultsTest`
Expected: compilation failure — `Tenant`, `Usuario`, `Rol` don't exist yet.

- [ ] **Step 3: Create `backend/src/main/java/cl/slimerp/tenant/Rol.java`**

```java
package cl.slimerp.tenant;

public enum Rol {
    SUPER_ADMIN,
    ADMIN,
    VENDEDOR,
    COMPRADOR,
    VISUALIZADOR
}
```

- [ ] **Step 4: Create `backend/src/main/java/cl/slimerp/tenant/Tenant.java`**

```java
package cl.slimerp.tenant;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tenant")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(nullable = false, unique = true, length = 20)
    private String rut;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String plan = "basico";

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;

    @Column(name = "fecha_alta", nullable = false)
    @Builder.Default
    private LocalDateTime fechaAlta = LocalDateTime.now();
}
```

- [ ] **Step 5: Create `backend/src/main/java/cl/slimerp/tenant/Usuario.java`**

```java
package cl.slimerp.tenant;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(nullable = false, length = 20)
    private String rut;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private Rol rol = Rol.ADMIN;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;

    @Column(name = "fecha_creacion", nullable = false)
    @Builder.Default
    private LocalDateTime fechaCreacion = LocalDateTime.now();
}
```

- [ ] **Step 6: Create `backend/src/main/java/cl/slimerp/tenant/TenantRepository.java`**

```java
package cl.slimerp.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findByRut(String rut);
}
```

- [ ] **Step 7: Create `backend/src/main/java/cl/slimerp/tenant/UsuarioRepository.java`**

```java
package cl.slimerp.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmailAndTenantId(String email, Long tenantId);

    // Login: el email es único a nivel global de la app para simplificar el flujo de autenticación
    // (el usuario no necesita saber su tenant_id de antemano).
    Optional<Usuario> findFirstByEmailAndActivoTrue(String email);

    boolean existsByEmail(String email);

    List<Usuario> findByTenantId(Long tenantId);

    Optional<Usuario> findByIdAndTenantId(Long id, Long tenantId);
}
```

- [ ] **Step 8: Run test to verify it passes**

Run: `cd backend && mvn -q -B test -Dtest=TenantUsuarioDefaultsTest`
Expected: `BUILD SUCCESS`, 2 tests pass.

- [ ] **Step 9: Create `backend/src/main/resources/db/migration/V1__init.sql`**

```sql
-- Slime ERP - esquema inicial (multi-tenant: shared schema + tenant_id)

CREATE TABLE tenant (
    id              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    nombre          VARCHAR(150) NOT NULL,
    rut             VARCHAR(20)  NOT NULL UNIQUE,
    plan            VARCHAR(30)  NOT NULL DEFAULT 'basico',
    activo          BOOLEAN      NOT NULL DEFAULT TRUE,
    fecha_alta      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE usuario (
    id              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    tenant_id       BIGINT NOT NULL REFERENCES tenant(id),
    email           VARCHAR(150) NOT NULL,
    rut             VARCHAR(20)  NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    nombre          VARCHAR(150) NOT NULL,
    rol             VARCHAR(30)  NOT NULL DEFAULT 'ADMIN',
    activo          BOOLEAN      NOT NULL DEFAULT TRUE,
    fecha_creacion  TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_usuario_tenant_email UNIQUE (tenant_id, email),
    CONSTRAINT uq_usuario_tenant_rut UNIQUE (tenant_id, rut)
);
CREATE INDEX idx_usuario_tenant ON usuario(tenant_id);

-- Datos de ejemplo para desarrollo local
INSERT INTO tenant (id, nombre, rut, plan)
VALUES (1, 'Empresa Demo', '76.123.456-7', 'basico');

INSERT INTO usuario (tenant_id, email, rut, password_hash, nombre, rol)
VALUES (
    1,
    'admin@demo.cl',
    '15.234.567-8',
    '$2b$10$az9N.tCdIMXWdCSHij.frexWWh0pFR/uyIw60Fda1A6eZi1u2LSce', -- password: admin123
    'Administrador Demo',
    'ADMIN'
);
```

*(This file gets `cliente` and `producto` tables appended in Tasks 7 and 8 — it stays a single `V1__init.sql` since nothing has run it against a real database yet.)*

- [ ] **Step 10: Compile check**

Run: `cd backend && mvn -q -B compile`
Expected: `BUILD SUCCESS`.

---

## Task 3: Security/JWT scaffolding

**Files:**
- Create: `backend/src/main/java/cl/slimerp/config/JwtService.java`
- Create: `backend/src/main/java/cl/slimerp/config/TenantContext.java`
- Create: `backend/src/main/java/cl/slimerp/config/GlobalExceptionHandler.java`
- Test: `backend/src/test/java/cl/slimerp/config/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Consumes: none new (uses only Spring/Security/JJWT libraries).
- Produces: `JwtService.generarToken(Long,Long,String,String)` → `String`, `.parseClaims(String)` → `Claims` (consumed by Task 5's `JwtAuthFilter` and Task 6's `AuthController`); `TenantContext.setTenantId/getTenantId/clear` (consumed everywhere business logic filters by tenant); `GlobalExceptionHandler` gets new `@ExceptionHandler` methods appended in Tasks 9 and 10.

*(`SecurityConfig` is deliberately NOT created in this task — it depends on `JwtAuthFilter`, which doesn't exist until Task 5. Creating it here would break `mvn compile` for the whole module, since Maven compiles all of `src/main/java` together — a single unresolved reference fails every test in the project, not just the file that has it. `SecurityConfig` is created in Task 5, right after `JwtAuthFilter`, so the module compiles cleanly at every step.)*

- [ ] **Step 1: Create `backend/src/main/java/cl/slimerp/config/JwtService.java`**

```java
package cl.slimerp.config;

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
import java.util.Map;

/**
 * Emite y valida los JWT. El token lleva el tenant_id y el rol como claims,
 * de forma que cada request pueda resolver el tenant sin ir a la base de datos.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMinutes;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    public String generarToken(Long usuarioId, Long tenantId, String email, String rol) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(usuarioId.toString())
                .claims(Map.of(
                        "tenantId", tenantId.toString(),
                        "email", email,
                        "rol", rol
                ))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
```

- [ ] **Step 2: Create `backend/src/main/java/cl/slimerp/config/TenantContext.java`**

```java
package cl.slimerp.config;

/**
 * Contiene el tenant_id del request actual (extraído del JWT por {@link JwtAuthFilter}).
 * Se usa en toda la capa de servicio para filtrar siempre por tenant.
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantId(Long tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static Long getTenantId() {
        Long tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            throw new IllegalStateException("No hay tenant_id en el contexto actual. ¿Falta autenticación?");
        }
        return tenantId;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
```

- [ ] **Step 3: Write the failing test for `GlobalExceptionHandler`**

```java
package cl.slimerp.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void accessDeniedDevuelve403ConMensajeGenerico() {
        ResponseEntity<Map<String, Object>> respuesta = handler.handleAccessDenied(new AccessDeniedException("no importa"));

        assertEquals(HttpStatus.FORBIDDEN, respuesta.getStatusCode());
        assertEquals("No tiene permisos para realizar esta acción", respuesta.getBody().get("error"));
    }

    @Test
    void badCredentialsDevuelve401ConElMensajeOriginal() {
        ResponseEntity<Map<String, Object>> respuesta = handler.handleBadCredentials(new BadCredentialsException("Credenciales inválidas"));

        assertEquals(HttpStatus.UNAUTHORIZED, respuesta.getStatusCode());
        assertEquals("Credenciales inválidas", respuesta.getBody().get("error"));
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `cd backend && mvn -q -B test -Dtest=GlobalExceptionHandlerTest`
Expected: compilation failure — `GlobalExceptionHandler` doesn't exist yet.

- [ ] **Step 5: Create `backend/src/main/java/cl/slimerp/config/GlobalExceptionHandler.java`**

```java
package cl.slimerp.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
        return error(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "No tiene permisos para realizar esta acción");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        return error(HttpStatus.BAD_REQUEST, "Datos inválidos: " + ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        return error(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Error inesperado: " + ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", message
        ));
    }
}
```

*(Task 9 appends a `UsuarioConflictException` handler here; Task 10 appends an `EmpresaConflictException` handler here.)*

- [ ] **Step 6: Run test to verify it passes**

Run: `cd backend && mvn -q -B test -Dtest=GlobalExceptionHandlerTest`
Expected: `BUILD SUCCESS`, 2 tests pass.

---

## Task 4: Permisos module

**Files:**
- Create: `backend/src/main/java/cl/slimerp/permisos/Permiso.java`
- Create: `backend/src/main/java/cl/slimerp/permisos/RolPermisos.java`
- Test: `backend/src/test/java/cl/slimerp/permisos/RolPermisosTest.java`

**Interfaces:**
- Consumes: `cl.slimerp.tenant.Rol` (Task 2).
- Produces: `Permiso` enum (`CLIENTES_VER, CLIENTES_EDITAR, PRODUCTOS_VER, PRODUCTOS_EDITAR, USUARIOS_VER, USUARIOS_EDITAR, EMPRESAS_ADMINISTRAR`), `RolPermisos.permisosDe(Rol)` → `Set<Permiso>` — consumed by Task 5 (`JwtAuthFilter`) and Task 6 (`AuthController`/`LoginResponse`).

- [ ] **Step 1: Write the failing test**

```java
package cl.slimerp.permisos;

import cl.slimerp.tenant.Rol;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RolPermisosTest {

    @Test
    void superAdminTieneTodosLosPermisos() {
        assertEquals(Set.of(Permiso.values()), RolPermisos.permisosDe(Rol.SUPER_ADMIN));
    }

    @Test
    void adminTieneClientesProductosYUsuarios() {
        assertEquals(
                Set.of(Permiso.CLIENTES_VER, Permiso.CLIENTES_EDITAR,
                        Permiso.PRODUCTOS_VER, Permiso.PRODUCTOS_EDITAR,
                        Permiso.USUARIOS_VER, Permiso.USUARIOS_EDITAR),
                RolPermisos.permisosDe(Rol.ADMIN));
    }

    @Test
    void vendedorSoloVeProductosYGestionaClientes() {
        assertEquals(
                Set.of(Permiso.CLIENTES_VER, Permiso.CLIENTES_EDITAR, Permiso.PRODUCTOS_VER),
                RolPermisos.permisosDe(Rol.VENDEDOR));
    }

    @Test
    void compradorGestionaProductos() {
        assertEquals(
                Set.of(Permiso.PRODUCTOS_VER, Permiso.PRODUCTOS_EDITAR),
                RolPermisos.permisosDe(Rol.COMPRADOR));
    }

    @Test
    void visualizadorSoloLee() {
        assertEquals(
                Set.of(Permiso.CLIENTES_VER, Permiso.PRODUCTOS_VER),
                RolPermisos.permisosDe(Rol.VISUALIZADOR));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q -B test -Dtest=RolPermisosTest`
Expected: compilation failure — `Permiso`, `RolPermisos` don't exist yet.

- [ ] **Step 3: Create `backend/src/main/java/cl/slimerp/permisos/Permiso.java`**

```java
package cl.slimerp.permisos;

public enum Permiso {
    CLIENTES_VER,
    CLIENTES_EDITAR,
    PRODUCTOS_VER,
    PRODUCTOS_EDITAR,
    USUARIOS_VER,
    USUARIOS_EDITAR,
    EMPRESAS_ADMINISTRAR
}
```

- [ ] **Step 4: Create `backend/src/main/java/cl/slimerp/permisos/RolPermisos.java`**

```java
package cl.slimerp.permisos;

import cl.slimerp.tenant.Rol;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Mapeo fijo Rol -> Permisos. No configurable por tenant en esta etapa
 * (ver spec: docs/superpowers/specs/2026-08-30-scaffold-desde-slime-erp-old-design.md).
 */
public final class RolPermisos {

    private static final Map<Rol, Set<Permiso>> MAPA = new EnumMap<>(Rol.class);

    static {
        MAPA.put(Rol.SUPER_ADMIN, EnumSet.allOf(Permiso.class));
        MAPA.put(Rol.ADMIN, EnumSet.of(
                Permiso.CLIENTES_VER, Permiso.CLIENTES_EDITAR,
                Permiso.PRODUCTOS_VER, Permiso.PRODUCTOS_EDITAR,
                Permiso.USUARIOS_VER, Permiso.USUARIOS_EDITAR));
        MAPA.put(Rol.VENDEDOR, EnumSet.of(
                Permiso.CLIENTES_VER, Permiso.CLIENTES_EDITAR, Permiso.PRODUCTOS_VER));
        MAPA.put(Rol.COMPRADOR, EnumSet.of(
                Permiso.PRODUCTOS_VER, Permiso.PRODUCTOS_EDITAR));
        MAPA.put(Rol.VISUALIZADOR, EnumSet.of(
                Permiso.CLIENTES_VER, Permiso.PRODUCTOS_VER));
    }

    private RolPermisos() {
    }

    public static Set<Permiso> permisosDe(Rol rol) {
        return MAPA.getOrDefault(rol, Set.of());
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && mvn -q -B test -Dtest=RolPermisosTest`
Expected: `BUILD SUCCESS`, 5 tests pass.

---

## Task 5: JwtAuthFilter — add permission authorities

**Files:**
- Create: `backend/src/main/java/cl/slimerp/config/JwtAuthFilter.java`
- Create: `backend/src/main/java/cl/slimerp/config/SecurityConfig.java`
- Test: `backend/src/test/java/cl/slimerp/config/JwtAuthFilterTest.java`

**Interfaces:**
- Consumes: `JwtService.parseClaims` (Task 3), `RolPermisos.permisosDe` (Task 4), `TenantContext.setTenantId/clear` (Task 3).
- Produces: an authenticated `SecurityContextHolder` whose authorities are `ROLE_<rol>` plus one `GrantedAuthority` per permission of that role — consumed by every `@PreAuthorize` in Tasks 7–10. `SecurityConfig` is created here (not in Task 3) precisely because it needs `JwtAuthFilter` to exist first — see Task 3's note.

- [ ] **Step 1: Write the failing test**

```java
package cl.slimerp.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthFilterTest {

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void construyeAutoridadesDeRolYPermisosParaUnTokenValido() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        Claims claims = mock(Claims.class);
        when(claims.get("tenantId", String.class)).thenReturn("10");
        when(claims.get("email", String.class)).thenReturn("v1@demo.cl");
        when(claims.get("rol", String.class)).thenReturn("VENDEDOR");
        when(jwtService.parseClaims("token-valido")).thenReturn(claims);

        JwtAuthFilter filter = new JwtAuthFilter(jwtService);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer token-valido");
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        List<String> autoridades = auth.getAuthorities().stream()
                .map(Object::toString)
                .collect(Collectors.toList());

        assertTrue(autoridades.contains("ROLE_VENDEDOR"));
        assertTrue(autoridades.contains("CLIENTES_VER"));
        assertTrue(autoridades.contains("CLIENTES_EDITAR"));
        assertTrue(autoridades.contains("PRODUCTOS_VER"));
        assertEquals(4, autoridades.size());
        verify(chain).doFilter(request, response);
    }

    @Test
    void sinHeaderAuthorizationNoAutentica() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        JwtAuthFilter filter = new JwtAuthFilter(jwtService);

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q -B test -Dtest=JwtAuthFilterTest`
Expected: compilation failure — `JwtAuthFilter` doesn't exist yet.

- [ ] **Step 3: Create `backend/src/main/java/cl/slimerp/config/JwtAuthFilter.java`**

```java
package cl.slimerp.config;

import cl.slimerp.permisos.RolPermisos;
import cl.slimerp.tenant.Rol;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Valida el JWT en cada request, autentica al usuario ante Spring Security con
 * el rol y los permisos de ese rol como autoridades, y deja el tenant_id
 * disponible en {@link TenantContext} para el resto del pipeline.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtService.parseClaims(token);
                Long tenantId = Long.parseLong(claims.get("tenantId", String.class));
                String rol = claims.get("rol", String.class);
                String email = claims.get("email", String.class);

                TenantContext.setTenantId(tenantId);

                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                authorities.add(new SimpleGrantedAuthority("ROLE_" + rol));
                RolPermisos.permisosDe(Rol.valueOf(rol))
                        .forEach(permiso -> authorities.add(new SimpleGrantedAuthority(permiso.name())));

                var authentication = new UsernamePasswordAuthenticationToken(email, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException ex) {
                // Token inválido o expirado: se deja sin autenticar, Spring Security responderá 401/403.
                SecurityContextHolder.clearContext();
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn -q -B test -Dtest=JwtAuthFilterTest`
Expected: `BUILD SUCCESS`, 2 tests pass.

- [ ] **Step 5: Create `backend/src/main/java/cl/slimerp/config/SecurityConfig.java`**

```java
package cl.slimerp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/actuator/health").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
```

- [ ] **Step 6: Full compile check**

Run: `cd backend && mvn -q -B compile`
Expected: `BUILD SUCCESS` — this is the first point in the plan where the whole `config` package compiles together.

---

## Task 6: Auth module (login includes permisos)

**Files:**
- Create: `backend/src/main/java/cl/slimerp/auth/LoginRequest.java`
- Create: `backend/src/main/java/cl/slimerp/auth/LoginResponse.java`
- Create: `backend/src/main/java/cl/slimerp/auth/AuthController.java`
- Test: `backend/src/test/java/cl/slimerp/auth/AuthControllerTest.java`

**Interfaces:**
- Consumes: `UsuarioRepository`, `TenantRepository` (Task 2), `JwtService` (Task 3), `RolPermisos` (Task 4), `PasswordEncoder` bean (Task 3).
- Produces: `POST /api/auth/login` → `LoginResponse(token, usuarioId, tenantId, tenantNombre, nombre, email, rut, rol, permisos: List<String>)` — consumed by the frontend's `AuthService` (Task 15).

- [ ] **Step 1: Write the failing test**

```java
package cl.slimerp.auth;

import cl.slimerp.config.JwtService;
import cl.slimerp.permisos.RolPermisos;
import cl.slimerp.tenant.Rol;
import cl.slimerp.tenant.Tenant;
import cl.slimerp.tenant.TenantRepository;
import cl.slimerp.tenant.Usuario;
import cl.slimerp.tenant.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthControllerTest {

    private UsuarioRepository usuarioRepository;
    private TenantRepository tenantRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuthController authController;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        tenantRepository = mock(TenantRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        authController = new AuthController(usuarioRepository, tenantRepository, passwordEncoder, jwtService);
    }

    private Usuario usuario() {
        return Usuario.builder()
                .id(1L).tenantId(10L).email("user@demo.cl").passwordHash("hash")
                .rut("1.111.111-1").nombre("Usuario Demo").rol(Rol.VENDEDOR).activo(true)
                .build();
    }

    private LoginRequest request() {
        return new LoginRequest("user@demo.cl", "clave");
    }

    @Test
    void loginRechazaCuandoTenantEstaInactivo() {
        when(usuarioRepository.findFirstByEmailAndActivoTrue("user@demo.cl")).thenReturn(Optional.of(usuario()));
        when(passwordEncoder.matches("clave", "hash")).thenReturn(true);
        Tenant tenantInactivo = Tenant.builder().id(10L).nombre("Empresa X").rut("1-9").activo(false).build();
        when(tenantRepository.findById(10L)).thenReturn(Optional.of(tenantInactivo));

        assertThrows(BadCredentialsException.class, () -> authController.login(request()));
    }

    @Test
    void loginIncluyeLosPermisosDelRolDelUsuario() {
        when(usuarioRepository.findFirstByEmailAndActivoTrue("user@demo.cl")).thenReturn(Optional.of(usuario()));
        when(passwordEncoder.matches("clave", "hash")).thenReturn(true);
        Tenant tenantActivo = Tenant.builder().id(10L).nombre("Empresa X").rut("1-9").activo(true).build();
        when(tenantRepository.findById(10L)).thenReturn(Optional.of(tenantActivo));
        when(jwtService.generarToken(1L, 10L, "user@demo.cl", "VENDEDOR")).thenReturn("token-123");

        var response = authController.login(request());

        assertEquals(200, response.getStatusCode().value());
        assertEquals("token-123", response.getBody().token());
        Set<String> permisosEsperados = Set.of("CLIENTES_VER", "CLIENTES_EDITAR", "PRODUCTOS_VER");
        assertEquals(permisosEsperados, Set.copyOf(response.getBody().permisos()));
        assertEquals(RolPermisos.permisosDe(Rol.VENDEDOR).size(), response.getBody().permisos().size());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q -B test -Dtest=AuthControllerTest`
Expected: compilation failure — `LoginRequest`, `LoginResponse`, `AuthController` don't exist yet.

- [ ] **Step 3: Create `backend/src/main/java/cl/slimerp/auth/LoginRequest.java`**

```java
package cl.slimerp.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {
}
```

- [ ] **Step 4: Create `backend/src/main/java/cl/slimerp/auth/LoginResponse.java`**

```java
package cl.slimerp.auth;

import java.util.List;

public record LoginResponse(
        String token,
        Long usuarioId,
        Long tenantId,
        String tenantNombre,
        String nombre,
        String email,
        String rut,
        String rol,
        List<String> permisos
) {
}
```

- [ ] **Step 5: Create `backend/src/main/java/cl/slimerp/auth/AuthController.java`**

```java
package cl.slimerp.auth;

import cl.slimerp.config.JwtService;
import cl.slimerp.permisos.Permiso;
import cl.slimerp.permisos.RolPermisos;
import cl.slimerp.tenant.Tenant;
import cl.slimerp.tenant.TenantRepository;
import cl.slimerp.tenant.Usuario;
import cl.slimerp.tenant.UsuarioRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UsuarioRepository usuarioRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(UsuarioRepository usuarioRepository, TenantRepository tenantRepository,
                           PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        Usuario usuario = usuarioRepository.findFirstByEmailAndActivoTrue(request.email())
                .orElseThrow(() -> new BadCredentialsException("Credenciales inválidas"));

        if (!passwordEncoder.matches(request.password(), usuario.getPasswordHash())) {
            throw new BadCredentialsException("Credenciales inválidas");
        }

        Tenant tenant = tenantRepository.findById(usuario.getTenantId())
                .orElseThrow(() -> new BadCredentialsException("Credenciales inválidas"));

        if (!tenant.isActivo()) {
            throw new BadCredentialsException("Credenciales inválidas");
        }

        String token = jwtService.generarToken(
                usuario.getId(), usuario.getTenantId(), usuario.getEmail(), usuario.getRol().name());

        var permisos = RolPermisos.permisosDe(usuario.getRol()).stream().map(Permiso::name).toList();

        return ResponseEntity.ok(new LoginResponse(
                token, usuario.getId(), usuario.getTenantId(), tenant.getNombre(), usuario.getNombre(),
                usuario.getEmail(), usuario.getRut(), usuario.getRol().name(), permisos));
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd backend && mvn -q -B test -Dtest=AuthControllerTest`
Expected: `BUILD SUCCESS`, 2 tests pass.

---

## Task 7: Catálogo — Cliente

**Files:**
- Create: `backend/src/main/java/cl/slimerp/catalogo/Cliente.java`
- Create: `backend/src/main/java/cl/slimerp/catalogo/ClienteRepository.java`
- Create: `backend/src/main/java/cl/slimerp/catalogo/ClienteRequest.java`
- Create: `backend/src/main/java/cl/slimerp/catalogo/ClienteController.java`
- Modify: `backend/src/main/resources/db/migration/V1__init.sql` (append `cliente` table)
- Test: `backend/src/test/java/cl/slimerp/catalogo/ClienteControllerTest.java`
- Test: `backend/src/test/java/cl/slimerp/catalogo/ClienteControllerPermissionTest.java`

**Interfaces:**
- Consumes: `TenantContext.getTenantId()` (Task 3).
- Produces: `Cliente(id, tenantId, nombre, rut, email, telefono, direccion, activo, fechaCreacion)`, `GET/POST/PUT/DELETE /api/clientes` — no other task depends on this one.

- [ ] **Step 1: Write the failing behavior test**

```java
package cl.slimerp.catalogo;

import cl.slimerp.config.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ClienteControllerTest {

    private ClienteRepository clienteRepository;
    private ClienteController controller;

    @BeforeEach
    void setUp() {
        clienteRepository = mock(ClienteRepository.class);
        controller = new ClienteController(clienteRepository);
        TenantContext.setTenantId(1L);
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void crearAsociaElClienteAlTenantDelContexto() {
        var request = new ClienteRequest("Cliente Uno", "1-9", "c1@demo.cl", "+56911111111", "Calle 1");

        var response = controller.crear(request);

        assertEquals(1L, response.getBody().getTenantId());
        assertEquals("Cliente Uno", response.getBody().getNombre());
    }

    @Test
    void eliminarHaceSoftDeleteEnVezDeBorrarFisicamente() {
        Cliente existente = Cliente.builder().id(5L).tenantId(1L).nombre("Cliente Dos").activo(true).build();
        when(clienteRepository.findByIdAndTenantIdAndActivoTrue(5L, 1L)).thenReturn(Optional.of(existente));

        var response = controller.eliminar(5L);

        assertEquals(204, response.getStatusCode().value());
        assertFalse(existente.isActivo());
        verify(clienteRepository).save(existente);
    }

    @Test
    void actualizarDevuelve404SiNoExisteEnElTenant() {
        when(clienteRepository.findByIdAndTenantIdAndActivoTrue(99L, 1L)).thenReturn(Optional.empty());

        var response = controller.actualizar(99L, new ClienteRequest("X", null, null, null, null));

        assertEquals(404, response.getStatusCode().value());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q -B test -Dtest=ClienteControllerTest`
Expected: compilation failure — `catalogo` package doesn't exist yet.

- [ ] **Step 3: Create `backend/src/main/java/cl/slimerp/catalogo/Cliente.java`**

```java
package cl.slimerp.catalogo;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "cliente")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 20)
    private String rut;

    @Column(length = 150)
    private String email;

    @Column(length = 30)
    private String telefono;

    @Column(length = 255)
    private String direccion;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;

    @Column(name = "fecha_creacion", nullable = false)
    @Builder.Default
    private LocalDateTime fechaCreacion = LocalDateTime.now();
}
```

- [ ] **Step 4: Create `backend/src/main/java/cl/slimerp/catalogo/ClienteRepository.java`**

```java
package cl.slimerp.catalogo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    List<Cliente> findByTenantIdAndActivoTrue(Long tenantId);

    Optional<Cliente> findByIdAndTenantIdAndActivoTrue(Long id, Long tenantId);
}
```

- [ ] **Step 5: Create `backend/src/main/java/cl/slimerp/catalogo/ClienteRequest.java`**

```java
package cl.slimerp.catalogo;

import jakarta.validation.constraints.NotBlank;

public record ClienteRequest(
        @NotBlank String nombre,
        String rut,
        String email,
        String telefono,
        String direccion
) {
}
```

- [ ] **Step 6: Create `backend/src/main/java/cl/slimerp/catalogo/ClienteController.java`**

```java
package cl.slimerp.catalogo;

import cl.slimerp.config.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    private final ClienteRepository clienteRepository;

    public ClienteController(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CLIENTES_VER')")
    public List<Cliente> listar() {
        return clienteRepository.findByTenantIdAndActivoTrue(TenantContext.getTenantId());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CLIENTES_VER')")
    public ResponseEntity<Cliente> obtener(@PathVariable Long id) {
        return clienteRepository.findByIdAndTenantIdAndActivoTrue(id, TenantContext.getTenantId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CLIENTES_EDITAR')")
    public ResponseEntity<Cliente> crear(@Valid @RequestBody ClienteRequest request) {
        Cliente cliente = Cliente.builder()
                .tenantId(TenantContext.getTenantId())
                .nombre(request.nombre())
                .rut(request.rut())
                .email(request.email())
                .telefono(request.telefono())
                .direccion(request.direccion())
                .build();
        return ResponseEntity.ok(clienteRepository.save(cliente));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CLIENTES_EDITAR')")
    public ResponseEntity<Cliente> actualizar(@PathVariable Long id, @Valid @RequestBody ClienteRequest request) {
        return clienteRepository.findByIdAndTenantIdAndActivoTrue(id, TenantContext.getTenantId())
                .map(cliente -> {
                    cliente.setNombre(request.nombre());
                    cliente.setRut(request.rut());
                    cliente.setEmail(request.email());
                    cliente.setTelefono(request.telefono());
                    cliente.setDireccion(request.direccion());
                    return ResponseEntity.ok(clienteRepository.save(cliente));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CLIENTES_EDITAR')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        return clienteRepository.findByIdAndTenantIdAndActivoTrue(id, TenantContext.getTenantId())
                .map(cliente -> {
                    cliente.setActivo(false);
                    clienteRepository.save(cliente);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `cd backend && mvn -q -B test -Dtest=ClienteControllerTest`
Expected: `BUILD SUCCESS`, 3 tests pass.

- [ ] **Step 8: Write the permission-enforcement smoke test (representative for the whole `@PreAuthorize` mechanism used across Tasks 7–10)**

```java
package cl.slimerp.catalogo;

import cl.slimerp.config.JwtAuthFilter;
import cl.slimerp.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClienteController.class)
@org.springframework.context.annotation.Import(SecurityConfig.class)
class ClienteControllerPermissionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClienteRepository clienteRepository;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    @Test
    @WithMockUser(authorities = "CLIENTES_VER")
    void permiteListarConElPermisoCorrecto() throws Exception {
        when(clienteRepository.findByTenantIdAndActivoTrue(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("No hay tenant_id en el contexto actual. ¿Falta autenticación?"));
        // Llega hasta el controlador (pasa la autorización) y falla más adentro por falta de TenantContext,
        // lo que confirma que @PreAuthorize dejó pasar la request — un 401/500 aquí es la señal esperada,
        // NO un 403.
        mockMvc.perform(get("/api/clientes"))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertNotEquals(403, result.getResponse().getStatus()));
    }

    @Test
    @WithMockUser(authorities = "PRODUCTOS_VER")
    void rechazaListarSinElPermisoCorrecto() throws Exception {
        mockMvc.perform(get("/api/clientes"))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 9: Run the permission test**

Run: `cd backend && mvn -q -B test -Dtest=ClienteControllerPermissionTest`
Expected: `BUILD SUCCESS`, 2 tests pass — this proves `@PreAuthorize("hasAuthority(...)")` is correctly wired through `SecurityConfig`'s method security for every controller in this plan that carries the same annotation.

- [ ] **Step 10: Append the `cliente` table to `backend/src/main/resources/db/migration/V1__init.sql`**

Add this block right after the `usuario` table's `CREATE INDEX` line and before the `INSERT INTO tenant` seed block:

```sql
CREATE TABLE cliente (
    id              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    tenant_id       BIGINT NOT NULL REFERENCES tenant(id),
    nombre          VARCHAR(150) NOT NULL,
    rut             VARCHAR(20),
    email           VARCHAR(150),
    telefono        VARCHAR(30),
    direccion       VARCHAR(255),
    activo          BOOLEAN      NOT NULL DEFAULT TRUE,
    fecha_creacion  TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_cliente_tenant ON cliente(tenant_id);
```

- [ ] **Step 11: Compile check**

Run: `cd backend && mvn -q -B compile`
Expected: `BUILD SUCCESS`.

---

## Task 8: Catálogo — Producto (+ V1 migration append)

**Files:**
- Create: `backend/src/main/java/cl/slimerp/catalogo/Producto.java`
- Create: `backend/src/main/java/cl/slimerp/catalogo/ProductoRepository.java`
- Create: `backend/src/main/java/cl/slimerp/catalogo/ProductoRequest.java`
- Create: `backend/src/main/java/cl/slimerp/catalogo/ProductoController.java`
- Modify: `backend/src/main/resources/db/migration/V1__init.sql` (append `producto` table)
- Test: `backend/src/test/java/cl/slimerp/catalogo/ProductoControllerTest.java`

**Interfaces:**
- Consumes: `TenantContext.getTenantId()` (Task 3).
- Produces: `Producto(id, tenantId, sku, nombre, descripcion, precioVenta, precioCompra, stock, controlaStock, activo, fechaCreacion)`, `GET/POST/PUT/DELETE /api/productos` — no other task depends on this one.

*(Note: this is a fresh, simplified `Producto` — NOT a copy of `slime-erp_old`'s `common/Producto.java`, which is entangled with categorías/subcategorías/bodegas/inventario that are out of scope. Fields here match the original `V1__init.sql` draft: sku, nombre, descripcion, precio_venta, precio_compra, stock, controla_stock.)*

- [ ] **Step 1: Write the failing behavior test**

```java
package cl.slimerp.catalogo;

import cl.slimerp.config.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductoControllerTest {

    private ProductoRepository productoRepository;
    private ProductoController controller;

    @BeforeEach
    void setUp() {
        productoRepository = mock(ProductoRepository.class);
        controller = new ProductoController(productoRepository);
        TenantContext.setTenantId(1L);
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void crearUsaCerosPorDefectoParaCamposOpcionalesNulos() {
        var request = new ProductoRequest("SKU-1", "Producto Uno", "desc", new BigDecimal("1000"), null, null, true);

        var response = controller.crear(request);

        assertEquals(BigDecimal.ZERO, response.getBody().getPrecioCompra());
        assertEquals(BigDecimal.ZERO, response.getBody().getStock());
        assertEquals(1L, response.getBody().getTenantId());
    }

    @Test
    void eliminarHaceSoftDelete() {
        Producto existente = Producto.builder().id(7L).tenantId(1L).nombre("Producto Dos").activo(true).build();
        when(productoRepository.findByIdAndTenantIdAndActivoTrue(7L, 1L)).thenReturn(Optional.of(existente));

        var response = controller.eliminar(7L);

        assertEquals(204, response.getStatusCode().value());
        assertFalse(existente.isActivo());
    }

    @Test
    void actualizarDevuelve404SiNoExisteEnElTenant() {
        when(productoRepository.findByIdAndTenantIdAndActivoTrue(99L, 1L)).thenReturn(Optional.empty());

        var response = controller.actualizar(99L,
                new ProductoRequest("SKU-X", "X", null, BigDecimal.TEN, null, null, true));

        assertEquals(404, response.getStatusCode().value());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q -B test -Dtest=ProductoControllerTest`
Expected: compilation failure — `Producto`, `ProductoRepository`, `ProductoRequest`, `ProductoController` don't exist yet.

- [ ] **Step 3: Create `backend/src/main/java/cl/slimerp/catalogo/Producto.java`**

```java
package cl.slimerp.catalogo;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "producto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(length = 50)
    private String sku;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 500)
    private String descripcion;

    @Column(name = "precio_venta", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal precioVenta = BigDecimal.ZERO;

    @Column(name = "precio_compra", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal precioCompra = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal stock = BigDecimal.ZERO;

    @Column(name = "controla_stock", nullable = false)
    @Builder.Default
    private boolean controlaStock = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;

    @Column(name = "fecha_creacion", nullable = false)
    @Builder.Default
    private LocalDateTime fechaCreacion = LocalDateTime.now();
}
```

- [ ] **Step 4: Create `backend/src/main/java/cl/slimerp/catalogo/ProductoRepository.java`**

```java
package cl.slimerp.catalogo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductoRepository extends JpaRepository<Producto, Long> {
    List<Producto> findByTenantIdAndActivoTrue(Long tenantId);

    Optional<Producto> findByIdAndTenantIdAndActivoTrue(Long id, Long tenantId);

    Optional<Producto> findFirstByTenantIdAndSku(Long tenantId, String sku);
}
```

- [ ] **Step 5: Create `backend/src/main/java/cl/slimerp/catalogo/ProductoRequest.java`**

```java
package cl.slimerp.catalogo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ProductoRequest(
        String sku,
        @NotBlank String nombre,
        String descripcion,
        @NotNull BigDecimal precioVenta,
        BigDecimal precioCompra,
        BigDecimal stock,
        boolean controlaStock
) {
}
```

- [ ] **Step 6: Create `backend/src/main/java/cl/slimerp/catalogo/ProductoController.java`**

```java
package cl.slimerp.catalogo;

import cl.slimerp.config.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    private final ProductoRepository productoRepository;

    public ProductoController(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCTOS_VER')")
    public List<Producto> listar() {
        return productoRepository.findByTenantIdAndActivoTrue(TenantContext.getTenantId());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCTOS_VER')")
    public ResponseEntity<Producto> obtener(@PathVariable Long id) {
        return productoRepository.findByIdAndTenantIdAndActivoTrue(id, TenantContext.getTenantId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PRODUCTOS_EDITAR')")
    public ResponseEntity<Producto> crear(@Valid @RequestBody ProductoRequest request) {
        Producto producto = Producto.builder()
                .tenantId(TenantContext.getTenantId())
                .sku(request.sku())
                .nombre(request.nombre())
                .descripcion(request.descripcion())
                .precioVenta(request.precioVenta())
                .precioCompra(request.precioCompra() != null ? request.precioCompra() : BigDecimal.ZERO)
                .stock(request.stock() != null ? request.stock() : BigDecimal.ZERO)
                .controlaStock(request.controlaStock())
                .build();
        return ResponseEntity.ok(productoRepository.save(producto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCTOS_EDITAR')")
    public ResponseEntity<Producto> actualizar(@PathVariable Long id, @Valid @RequestBody ProductoRequest request) {
        return productoRepository.findByIdAndTenantIdAndActivoTrue(id, TenantContext.getTenantId())
                .map(producto -> {
                    producto.setSku(request.sku());
                    producto.setNombre(request.nombre());
                    producto.setDescripcion(request.descripcion());
                    producto.setPrecioVenta(request.precioVenta());
                    producto.setPrecioCompra(request.precioCompra() != null ? request.precioCompra() : BigDecimal.ZERO);
                    producto.setStock(request.stock() != null ? request.stock() : BigDecimal.ZERO);
                    producto.setControlaStock(request.controlaStock());
                    return ResponseEntity.ok(productoRepository.save(producto));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCTOS_EDITAR')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        return productoRepository.findByIdAndTenantIdAndActivoTrue(id, TenantContext.getTenantId())
                .map(producto -> {
                    producto.setActivo(false);
                    productoRepository.save(producto);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `cd backend && mvn -q -B test -Dtest=ProductoControllerTest`
Expected: `BUILD SUCCESS`, 3 tests pass.

- [ ] **Step 8: Append the `producto` table to `backend/src/main/resources/db/migration/V1__init.sql`**

Add this block right after the `cliente` table's `CREATE INDEX` line and before the `INSERT INTO tenant` seed block:

```sql
CREATE TABLE producto (
    id              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    tenant_id       BIGINT NOT NULL REFERENCES tenant(id),
    sku             VARCHAR(50),
    nombre          VARCHAR(150) NOT NULL,
    descripcion     VARCHAR(500),
    precio_venta    NUMERIC(14,2) NOT NULL DEFAULT 0,
    precio_compra   NUMERIC(14,2) NOT NULL DEFAULT 0,
    stock           NUMERIC(14,2) NOT NULL DEFAULT 0,
    controla_stock  BOOLEAN      NOT NULL DEFAULT TRUE,
    activo          BOOLEAN      NOT NULL DEFAULT TRUE,
    fecha_creacion  TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_producto_tenant_sku UNIQUE (tenant_id, sku)
);
CREATE INDEX idx_producto_tenant ON producto(tenant_id);
```

- [ ] **Step 9: Compile check**

Run: `cd backend && mvn -q -B compile`
Expected: `BUILD SUCCESS`.

---

## Task 9: Usuarios module

**Files:**
- Create: `backend/src/main/java/cl/slimerp/usuarios/UsuarioRequest.java`
- Create: `backend/src/main/java/cl/slimerp/usuarios/UsuarioResponse.java`
- Create: `backend/src/main/java/cl/slimerp/usuarios/UsuarioConflictException.java`
- Create: `backend/src/main/java/cl/slimerp/usuarios/UsuarioController.java`
- Modify: `backend/src/main/java/cl/slimerp/config/GlobalExceptionHandler.java` (append `UsuarioConflictException` handler)
- Test: `backend/src/test/java/cl/slimerp/usuarios/UsuarioControllerTest.java`

**Interfaces:**
- Consumes: `UsuarioRepository` (Task 2, already has `findByTenantId`/`findByIdAndTenantId`), `TenantContext.getTenantId()` (Task 3), `PasswordEncoder` bean (Task 3), `Rol` (Task 2).
- Produces: `GET/POST/PUT/DELETE /api/usuarios` — no other task depends on this one.

- [ ] **Step 1: Write the failing behavior test**

```java
package cl.slimerp.usuarios;

import cl.slimerp.config.TenantContext;
import cl.slimerp.tenant.Rol;
import cl.slimerp.tenant.Usuario;
import cl.slimerp.tenant.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UsuarioControllerTest {

    private UsuarioRepository usuarioRepository;
    private PasswordEncoder passwordEncoder;
    private UsuarioController controller;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        controller = new UsuarioController(usuarioRepository, passwordEncoder);
        TenantContext.setTenantId(1L);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(passwordEncoder.encode(any())).thenReturn("hash-cifrado");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private UsuarioRequest request(Rol rol, String password) {
        return new UsuarioRequest("Vendedor Uno", "1-9", "v1@demo.cl", password, rol, null);
    }

    @Test
    void crearRechazaElRolSuperAdmin() {
        assertThrows(IllegalArgumentException.class,
                () -> controller.crear(request(Rol.SUPER_ADMIN, "clave123")));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void crearRechazaEmailDuplicado() {
        when(usuarioRepository.existsByEmail("v1@demo.cl")).thenReturn(true);

        assertThrows(UsuarioConflictException.class, () -> controller.crear(request(Rol.VENDEDOR, "clave123")));
    }

    @Test
    void crearRechazaPasswordVacia() {
        when(usuarioRepository.existsByEmail("v1@demo.cl")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> controller.crear(request(Rol.VENDEDOR, "")));
    }

    @Test
    void crearGuardaConPasswordCifradaYTenantDelContexto() {
        when(usuarioRepository.existsByEmail("v1@demo.cl")).thenReturn(false);

        var response = controller.crear(request(Rol.VENDEDOR, "clave123"));

        assertEquals(1L, response.getBody().id() == null ? 1L : 1L); // tenant no viaja en la respuesta
        verify(usuarioRepository).save(argThat(u ->
                u.getTenantId().equals(1L) && u.getPasswordHash().equals("hash-cifrado") && u.getRol() == Rol.VENDEDOR));
    }

    @Test
    void actualizarSoloRecifraPasswordSiSeEnvioUnaNueva() {
        Usuario existente = Usuario.builder().id(3L).tenantId(1L).email("v1@demo.cl").rut("1-9")
                .passwordHash("hash-anterior").nombre("Vendedor Uno").rol(Rol.VENDEDOR).activo(true).build();
        when(usuarioRepository.findByIdAndTenantId(3L, 1L)).thenReturn(Optional.of(existente));

        controller.actualizar(3L, request(Rol.VENDEDOR, null));

        assertEquals("hash-anterior", existente.getPasswordHash());
    }

    @Test
    void desactivarHaceSoftDelete() {
        Usuario existente = Usuario.builder().id(4L).tenantId(1L).activo(true).build();
        when(usuarioRepository.findByIdAndTenantId(4L, 1L)).thenReturn(Optional.of(existente));

        var response = controller.desactivar(4L);

        assertEquals(204, response.getStatusCode().value());
        assertFalse(existente.isActivo());
    }

    @Test
    void listarMapeaSoloLosUsuariosDelTenant() {
        Usuario u = Usuario.builder().id(1L).tenantId(1L).nombre("Admin").email("a@demo.cl")
                .rut("1-9").rol(Rol.ADMIN).activo(true).build();
        when(usuarioRepository.findByTenantId(1L)).thenReturn(List.of(u));

        List<UsuarioResponse> resultado = controller.listar();

        assertEquals(1, resultado.size());
        assertEquals("Admin", resultado.get(0).nombre());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q -B test -Dtest=UsuarioControllerTest`
Expected: compilation failure — `usuarios` package doesn't exist yet.

- [ ] **Step 3: Create `backend/src/main/java/cl/slimerp/usuarios/UsuarioRequest.java`**

```java
package cl.slimerp.usuarios;

import cl.slimerp.tenant.Rol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UsuarioRequest(
        @NotBlank String nombre,
        @NotBlank String rut,
        @NotBlank @Email String email,
        String password,
        @NotNull Rol rol,
        Boolean activo
) {
}
```

- [ ] **Step 4: Create `backend/src/main/java/cl/slimerp/usuarios/UsuarioResponse.java`**

```java
package cl.slimerp.usuarios;

import cl.slimerp.tenant.Rol;
import cl.slimerp.tenant.Usuario;

import java.time.LocalDateTime;

public record UsuarioResponse(
        Long id,
        String nombre,
        String rut,
        String email,
        Rol rol,
        boolean activo,
        LocalDateTime fechaCreacion
) {
    public static UsuarioResponse desde(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNombre(), usuario.getRut(),
                usuario.getEmail(), usuario.getRol(), usuario.isActivo(), usuario.getFechaCreacion());
    }
}
```

- [ ] **Step 5: Create `backend/src/main/java/cl/slimerp/usuarios/UsuarioConflictException.java`**

```java
package cl.slimerp.usuarios;

// Conflictos al administrar usuarios del tenant (email ya usado) → HTTP 409
public class UsuarioConflictException extends RuntimeException {
    public UsuarioConflictException(String message) {
        super(message);
    }
}
```

- [ ] **Step 6: Create `backend/src/main/java/cl/slimerp/usuarios/UsuarioController.java`**

```java
package cl.slimerp.usuarios;

import cl.slimerp.config.TenantContext;
import cl.slimerp.tenant.Rol;
import cl.slimerp.tenant.Usuario;
import cl.slimerp.tenant.UsuarioRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioController(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USUARIOS_VER')")
    public List<UsuarioResponse> listar() {
        return usuarioRepository.findByTenantId(TenantContext.getTenantId()).stream()
                .map(UsuarioResponse::desde)
                .toList();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USUARIOS_EDITAR')")
    public ResponseEntity<UsuarioResponse> crear(@Valid @RequestBody UsuarioRequest request) {
        validarRolAsignable(request.rol());
        if (usuarioRepository.existsByEmail(request.email())) {
            throw new UsuarioConflictException("Ya existe un usuario con el email " + request.email());
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new IllegalArgumentException("La contraseña es obligatoria al crear un usuario");
        }

        Usuario usuario = Usuario.builder()
                .tenantId(TenantContext.getTenantId())
                .nombre(request.nombre())
                .rut(request.rut())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .rol(request.rol())
                .activo(request.activo() == null || request.activo())
                .build();
        return ResponseEntity.ok(UsuarioResponse.desde(usuarioRepository.save(usuario)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USUARIOS_EDITAR')")
    public ResponseEntity<UsuarioResponse> actualizar(@PathVariable Long id, @Valid @RequestBody UsuarioRequest request) {
        validarRolAsignable(request.rol());
        return usuarioRepository.findByIdAndTenantId(id, TenantContext.getTenantId())
                .map(usuario -> {
                    usuario.setNombre(request.nombre());
                    usuario.setRut(request.rut());
                    usuario.setRol(request.rol());
                    if (request.activo() != null) {
                        usuario.setActivo(request.activo());
                    }
                    if (request.password() != null && !request.password().isBlank()) {
                        usuario.setPasswordHash(passwordEncoder.encode(request.password()));
                    }
                    return ResponseEntity.ok(UsuarioResponse.desde(usuarioRepository.save(usuario)));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USUARIOS_EDITAR')")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        return usuarioRepository.findByIdAndTenantId(id, TenantContext.getTenantId())
                .map(usuario -> {
                    usuario.setActivo(false);
                    usuarioRepository.save(usuario);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private void validarRolAsignable(Rol rol) {
        if (rol == Rol.SUPER_ADMIN) {
            throw new IllegalArgumentException("No se puede asignar el rol SUPER_ADMIN desde este módulo");
        }
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `cd backend && mvn -q -B test -Dtest=UsuarioControllerTest`
Expected: `BUILD SUCCESS`, 6 tests pass.

- [ ] **Step 8: Modify `backend/src/main/java/cl/slimerp/config/GlobalExceptionHandler.java` — append the `UsuarioConflictException` handler**

Add the import `cl.slimerp.usuarios.UsuarioConflictException;` next to the existing imports, and add this method right after `handleIllegalArgument`:

```java
    @ExceptionHandler(UsuarioConflictException.class)
    public ResponseEntity<Map<String, Object>> handleUsuarioConflict(UsuarioConflictException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }
```

- [ ] **Step 9: Compile check**

Run: `cd backend && mvn -q -B compile`
Expected: `BUILD SUCCESS`.

---

## Task 10: Admin module (empresas, DTE-free)

**Files:**
- Create: `backend/src/main/java/cl/slimerp/admin/CrearEmpresaRequest.java`
- Create: `backend/src/main/java/cl/slimerp/admin/EmpresaResponse.java`
- Create: `backend/src/main/java/cl/slimerp/admin/EmpresaConflictException.java`
- Create: `backend/src/main/java/cl/slimerp/admin/EmpresaAdminService.java`
- Create: `backend/src/main/java/cl/slimerp/admin/EmpresaAdminController.java`
- Modify: `backend/src/main/java/cl/slimerp/config/GlobalExceptionHandler.java` (append `EmpresaConflictException` handler)
- Test: `backend/src/test/java/cl/slimerp/admin/EmpresaAdminServiceTest.java`

**Interfaces:**
- Consumes: `TenantRepository`, `UsuarioRepository`, `Rol`, `Tenant`, `Usuario` (Task 2), `PasswordEncoder` bean (Task 3).
- Produces: `GET/POST /api/admin/empresas`, `PATCH /api/admin/empresas/{id}/activar|desactivar`, restricted to `hasRole('SUPER_ADMIN')` — no other task depends on this one.

*(`EmpresaAdminService`, `CrearEmpresaRequest`, `EmpresaResponse`, `EmpresaConflictException`, and the test are copied verbatim from `slime-erp_old` — they have no DTE dependency. `EmpresaAdminController` is trimmed: the original also exposed `/emisor`, `/caf`, `/certificados` endpoints backed by the `dte` package, which are dropped entirely per the "no DTE anywhere" constraint.)*

- [ ] **Step 1: Copy the service and DTOs verbatim from the old project**

Run:
```bash
cp "C:/Users/ivana/Documents/slime-erp_old/backend/src/main/java/cl/slimerp/admin/EmpresaAdminService.java" \
   "backend/src/main/java/cl/slimerp/admin/EmpresaAdminService.java"
cp "C:/Users/ivana/Documents/slime-erp_old/backend/src/main/java/cl/slimerp/admin/CrearEmpresaRequest.java" \
   "backend/src/main/java/cl/slimerp/admin/CrearEmpresaRequest.java"
cp "C:/Users/ivana/Documents/slime-erp_old/backend/src/main/java/cl/slimerp/admin/EmpresaResponse.java" \
   "backend/src/main/java/cl/slimerp/admin/EmpresaResponse.java"
cp "C:/Users/ivana/Documents/slime-erp_old/backend/src/main/java/cl/slimerp/admin/EmpresaConflictException.java" \
   "backend/src/main/java/cl/slimerp/admin/EmpresaConflictException.java"
cp "C:/Users/ivana/Documents/slime-erp_old/backend/src/test/java/cl/slimerp/admin/EmpresaAdminServiceTest.java" \
   "backend/src/test/java/cl/slimerp/admin/EmpresaAdminServiceTest.java"
```

These four files are, verbatim:

`EmpresaAdminService.java`:
```java
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
```

`CrearEmpresaRequest.java`:
```java
package cl.slimerp.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CrearEmpresaRequest(
        @NotBlank String nombre,
        @NotBlank String rut,
        String plan,
        @NotBlank String adminNombre,
        @NotBlank String adminRut,
        @NotBlank @Email String adminEmail,
        @NotBlank String adminPassword
) {}
```

`EmpresaResponse.java`:
```java
package cl.slimerp.admin;

import cl.slimerp.tenant.Tenant;

import java.time.LocalDateTime;

public record EmpresaResponse(
        Long id,
        String nombre,
        String rut,
        String plan,
        boolean activo,
        LocalDateTime fechaAlta
) {
    public static EmpresaResponse desde(Tenant tenant) {
        return new EmpresaResponse(
                tenant.getId(), tenant.getNombre(), tenant.getRut(),
                tenant.getPlan(), tenant.isActivo(), tenant.getFechaAlta());
    }
}
```

`EmpresaConflictException.java`:
```java
package cl.slimerp.admin;

// Conflictos al administrar empresas (RUT o email de administrador ya usados) → HTTP 409
public class EmpresaConflictException extends RuntimeException {
    public EmpresaConflictException(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: Create the trimmed `backend/src/main/java/cl/slimerp/admin/EmpresaAdminController.java` (no DTE endpoints)**

```java
package cl.slimerp.admin;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/empresas")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class EmpresaAdminController {

    private final EmpresaAdminService empresaAdminService;

    public EmpresaAdminController(EmpresaAdminService empresaAdminService) {
        this.empresaAdminService = empresaAdminService;
    }

    @GetMapping
    public List<EmpresaResponse> listar() {
        return empresaAdminService.listar().stream().map(EmpresaResponse::desde).toList();
    }

    @PostMapping
    public ResponseEntity<EmpresaResponse> crear(@Valid @RequestBody CrearEmpresaRequest request) {
        return ResponseEntity.ok(EmpresaResponse.desde(empresaAdminService.crear(request)));
    }

    @PatchMapping("/{id}/activar")
    public EmpresaResponse activar(@PathVariable Long id) {
        return EmpresaResponse.desde(empresaAdminService.activar(id));
    }

    @PatchMapping("/{id}/desactivar")
    public EmpresaResponse desactivar(@PathVariable Long id) {
        return EmpresaResponse.desde(empresaAdminService.desactivar(id));
    }
}
```

- [ ] **Step 3: Run the copied test to confirm the unchanged service still behaves correctly**

Run: `cd backend && mvn -q -B test -Dtest=EmpresaAdminServiceTest`
Expected: `BUILD SUCCESS`, all 10 tests from the original suite pass unchanged (the service itself was not modified — only the controller lost its DTE endpoints).

- [ ] **Step 4: Modify `backend/src/main/java/cl/slimerp/config/GlobalExceptionHandler.java` — append the `EmpresaConflictException` handler**

Add the import `cl.slimerp.admin.EmpresaConflictException;` next to the existing imports, and add this method (order relative to the `UsuarioConflictException` handler from Task 9 doesn't matter):

```java
    @ExceptionHandler(EmpresaConflictException.class)
    public ResponseEntity<Map<String, Object>> handleEmpresaConflict(EmpresaConflictException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }
```

- [ ] **Step 5: Full backend compile + test run**

Run: `cd backend && mvn -q -B verify`
Expected: `BUILD SUCCESS`, all tests across every package pass.

---

## Task 11: Backend Docker Compose + end-to-end smoke test

**Files:**
- Create: `docker-compose.yml`

**Interfaces:**
- Consumes: everything from Tasks 1–10.
- Produces: a running backend + Postgres stack, manually verified — this is the first point where `V1__init.sql` actually executes against a real database. Task 25 later adds the `frontend` service to this same file.

- [ ] **Step 1: Create `docker-compose.yml` (backend + db only — frontend service added in Task 25)**

```yaml
services:
  db:
    image: postgres:16-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: ${DB_NAME:-slim_erp}
      POSTGRES_USER: ${DB_USER:-slim_erp}
      POSTGRES_PASSWORD: ${DB_PASSWORD:-slim_erp}
    volumes:
      - slime_erp_db_data:/var/lib/postgresql/data
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER:-slim_erp}"]
      interval: 5s
      timeout: 5s
      retries: 10

  backend:
    build: ./backend
    restart: unless-stopped
    depends_on:
      db:
        condition: service_healthy
    environment:
      DB_NAME: ${DB_NAME:-slim_erp}
      DB_USER: ${DB_USER:-slim_erp}
      DB_PASSWORD: ${DB_PASSWORD:-slim_erp}
      JWT_SECRET: ${JWT_SECRET:-CAMBIA_ESTE_SECRETO_EN_PRODUCCION_POR_UNO_LARGO_Y_ALEATORIO}
    ports:
      - "8080:8080"
    develop:
      watch:
        - action: rebuild
          path: ./backend/src
        - action: rebuild
          path: ./backend/pom.xml

volumes:
  slime_erp_db_data:
```

- [ ] **Step 2: Boot the stack**

Run: `docker compose up --build -d db backend`
Expected: both containers reach a healthy/running state; `docker compose logs backend` shows Flyway applying `V1__init.sql` with no errors and Tomcat starting on port 8080.

- [ ] **Step 3: Verify login works end-to-end**

Run:
```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@demo.cl","password":"admin123"}'
```
Expected: HTTP 200 with a JSON body containing a `token` and `"permisos":["CLIENTES_VER","CLIENTES_EDITAR","PRODUCTOS_VER","PRODUCTOS_EDITAR","USUARIOS_VER","USUARIOS_EDITAR"]` (order may vary).

- [ ] **Step 4: Verify permission enforcement with a real token**

Run (replace `$TOKEN` with the `token` value from Step 3):
```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/clientes -H "Authorization: Bearer $TOKEN"
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/admin/empresas -H "Authorization: Bearer $TOKEN"
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/clientes
```
Expected: first call `200` (admin has `CLIENTES_VER`), second call `403` (admin is not `SUPER_ADMIN`), third call (no token) `401` or `403` depending on Spring Security's default for an unauthenticated request to an `authenticated()` route — either is acceptable, just confirm it is not `200`.

- [ ] **Step 5: Tear down**

Run: `docker compose down`

- [ ] **Step 6: Commit reminder**

Do not commit. Tell the user the backend (Tasks 1–11) is ready for review.

---

## Task 12: Frontend project skeleton (no Tailwind)

**Files:**
- Create: `frontend/angular.json`
- Create: `frontend/tsconfig.json`
- Create: `frontend/tsconfig.app.json`
- Create: `frontend/tsconfig.spec.json`
- Create: `frontend/package.json`
- Create: `frontend/src/index.html`
- Create: `frontend/src/main.ts`
- Create: `frontend/src/app/app.component.ts`
- Create: `frontend/src/app/app.component.html`
- Create: `frontend/src/app/app.component.scss`
- Create: `frontend/src/app/app.routes.ts` (placeholder, replaced in Task 18)
- Create: `frontend/src/app/app.config.ts` (placeholder, replaced in Task 18)
- Create: `frontend/src/environments/environment.ts`
- Create: `frontend/src/environments/environment.development.ts`
- Create: `frontend/src/styles/tokens/colors.css`
- Create: `frontend/src/styles/tokens/typography.css`
- Create: `frontend/src/styles/tokens/spacing.css`
- Create: `frontend/src/styles/tokens/radius-border.css`
- Create: `frontend/src/styles/tokens/shadow.css`
- Create: `frontend/src/styles/_components.scss`
- Create: `frontend/src/styles.scss`

**Interfaces:**
- Produces: a buildable, empty Angular 18 app with the full design-token system loaded globally — every later frontend task builds on this. `app.routes.ts`/`app.config.ts` are intentionally minimal here and get their real content in Task 18 once the guard/interceptor exist.

- [ ] **Step 1: Copy the token CSS files verbatim from the old project (unchanged — they are the source of truth for the whole design system, including the Material theme in Task 13)**

Run:
```bash
mkdir -p frontend/src/styles/tokens
cp "C:/Users/ivana/Documents/slime-erp_old/frontend/src/styles/tokens/colors.css" frontend/src/styles/tokens/colors.css
cp "C:/Users/ivana/Documents/slime-erp_old/frontend/src/styles/tokens/typography.css" frontend/src/styles/tokens/typography.css
cp "C:/Users/ivana/Documents/slime-erp_old/frontend/src/styles/tokens/spacing.css" frontend/src/styles/tokens/spacing.css
cp "C:/Users/ivana/Documents/slime-erp_old/frontend/src/styles/tokens/radius-border.css" frontend/src/styles/tokens/radius-border.css
cp "C:/Users/ivana/Documents/slime-erp_old/frontend/src/styles/tokens/shadow.css" frontend/src/styles/tokens/shadow.css
```

- [ ] **Step 2: Create `frontend/package.json` (Tailwind, jspdf, jspdf-autotable removed — Material added in Task 13 via `ng add`)**

```json
{
  "name": "frontend",
  "version": "0.0.0",
  "scripts": {
    "ng": "ng",
    "start": "ng serve",
    "build": "ng build",
    "watch": "ng build --watch --configuration development",
    "test": "ng test"
  },
  "private": true,
  "dependencies": {
    "@angular/animations": "^18.2.0",
    "@angular/common": "^18.2.0",
    "@angular/compiler": "^18.2.0",
    "@angular/core": "^18.2.0",
    "@angular/forms": "^18.2.0",
    "@angular/platform-browser": "^18.2.0",
    "@angular/platform-browser-dynamic": "^18.2.0",
    "@angular/router": "^18.2.0",
    "rxjs": "~7.8.0",
    "tslib": "^2.3.0",
    "zone.js": "~0.14.10"
  },
  "devDependencies": {
    "@angular-devkit/build-angular": "^18.2.21",
    "@angular/cli": "^18.2.21",
    "@angular/compiler-cli": "^18.2.0",
    "@types/jasmine": "~5.1.0",
    "jasmine-core": "~5.2.0",
    "karma": "~6.4.0",
    "karma-chrome-launcher": "~3.2.0",
    "karma-coverage": "~2.2.0",
    "karma-jasmine": "~5.1.0",
    "karma-jasmine-html-reporter": "~2.1.0",
    "typescript": "~5.5.2"
  }
}
```

- [ ] **Step 3: Create `frontend/angular.json`**

```json
{
  "$schema": "./node_modules/@angular/cli/lib/config/schema.json",
  "version": 1,
  "cli": {
    "packageManager": "npm"
  },
  "newProjectRoot": "projects",
  "projects": {
    "frontend": {
      "projectType": "application",
      "schematics": {
        "@schematics/angular:component": {
          "style": "scss"
        }
      },
      "root": "",
      "sourceRoot": "src",
      "prefix": "app",
      "architect": {
        "build": {
          "builder": "@angular-devkit/build-angular:application",
          "options": {
            "outputPath": "dist/frontend",
            "index": "src/index.html",
            "browser": "src/main.ts",
            "polyfills": [
              "zone.js"
            ],
            "tsConfig": "tsconfig.app.json",
            "inlineStyleLanguage": "scss",
            "assets": [
              {
                "glob": "**/*",
                "input": "public"
              }
            ],
            "styles": [
              "src/styles/tokens/colors.css",
              "src/styles/tokens/typography.css",
              "src/styles/tokens/spacing.css",
              "src/styles/tokens/radius-border.css",
              "src/styles/tokens/shadow.css",
              "src/styles.scss"
            ],
            "scripts": []
          },
          "configurations": {
            "production": {
              "budgets": [
                {
                  "type": "initial",
                  "maximumWarning": "500kB",
                  "maximumError": "1MB"
                },
                {
                  "type": "anyComponentStyle",
                  "maximumWarning": "3kB",
                  "maximumError": "8kB"
                }
              ],
              "outputHashing": "all"
            },
            "development": {
              "optimization": false,
              "extractLicenses": false,
              "sourceMap": true
            }
          },
          "defaultConfiguration": "production"
        },
        "serve": {
          "builder": "@angular-devkit/build-angular:dev-server",
          "configurations": {
            "production": {
              "buildTarget": "frontend:build:production"
            },
            "development": {
              "buildTarget": "frontend:build:development"
            }
          },
          "defaultConfiguration": "development"
        },
        "extract-i18n": {
          "builder": "@angular-devkit/build-angular:extract-i18n"
        },
        "test": {
          "builder": "@angular-devkit/build-angular:karma",
          "options": {
            "polyfills": [
              "zone.js",
              "zone.js/testing"
            ],
            "tsConfig": "tsconfig.spec.json",
            "inlineStyleLanguage": "scss",
            "assets": [
              {
                "glob": "**/*",
                "input": "public"
              }
            ],
            "styles": [
              "src/styles/tokens/colors.css",
              "src/styles/tokens/typography.css",
              "src/styles/tokens/spacing.css",
              "src/styles/tokens/radius-border.css",
              "src/styles/tokens/shadow.css",
              "src/styles.scss"
            ],
            "scripts": []
          }
        }
      }
    }
  }
}
```

- [ ] **Step 4: Create `frontend/tsconfig.json`**

```json
{
  "compileOnSave": false,
  "compilerOptions": {
    "outDir": "./dist/out-tsc",
    "strict": true,
    "noImplicitOverride": true,
    "noPropertyAccessFromIndexSignature": true,
    "noImplicitReturns": true,
    "noFallthroughCasesInSwitch": true,
    "skipLibCheck": true,
    "isolatedModules": true,
    "esModuleInterop": true,
    "sourceMap": true,
    "declaration": false,
    "experimentalDecorators": true,
    "moduleResolution": "bundler",
    "importHelpers": true,
    "target": "ES2022",
    "module": "ES2022",
    "lib": [
      "ES2022",
      "dom"
    ]
  },
  "angularCompilerOptions": {
    "enableI18nLegacyMessageIdFormat": false,
    "strictInjectionParameters": true,
    "strictInputAccessModifiers": true,
    "strictTemplates": true
  }
}
```

- [ ] **Step 5: Create `frontend/tsconfig.app.json`**

```json
{
  "extends": "./tsconfig.json",
  "compilerOptions": {
    "outDir": "./out-tsc/app",
    "types": []
  },
  "files": [
    "src/main.ts"
  ],
  "include": [
    "src/**/*.d.ts"
  ]
}
```

- [ ] **Step 6: Create `frontend/tsconfig.spec.json`**

```json
{
  "extends": "./tsconfig.json",
  "compilerOptions": {
    "outDir": "./out-tsc/spec",
    "types": [
      "jasmine"
    ]
  },
  "include": [
    "src/**/*.spec.ts",
    "src/**/*.d.ts"
  ]
}
```

- [ ] **Step 7: Create `frontend/src/index.html`**

```html
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <title>Slime ERP</title>
  <base href="/">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:opsz,wght,FILL,GRAD@20,400,0,0" rel="stylesheet">
</head>
<body>
  <app-root></app-root>
</body>
</html>
```

*(The favicon `<link>` from the old project is dropped since no `public/favicon.ico` is being copied — add one later if desired.)*

- [ ] **Step 8: Create `frontend/src/main.ts`**

```typescript
import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';

bootstrapApplication(AppComponent, appConfig)
  .catch((err) => console.error(err));
```

- [ ] **Step 9: Create `frontend/src/app/app.component.ts`**

```typescript
import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {
  title = 'frontend';
}
```

- [ ] **Step 10: Create `frontend/src/app/app.component.html`**

```html
<router-outlet></router-outlet>
```

- [ ] **Step 11: Create `frontend/src/app/app.component.scss` (empty placeholder)**

```scss
```

- [ ] **Step 12: Create `frontend/src/app/app.routes.ts` (placeholder — replaced in Task 18)**

```typescript
import { Routes } from '@angular/router';

export const routes: Routes = [];
```

- [ ] **Step 13: Create `frontend/src/app/app.config.ts` (placeholder — replaced in Task 18)**

```typescript
import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
  ]
};
```

- [ ] **Step 14: Create `frontend/src/environments/environment.ts`**

```typescript
export const environment = {
  production: true,
  apiUrl: '/api',
};
```

- [ ] **Step 15: Create `frontend/src/environments/environment.development.ts`**

```typescript
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api',
};
```

- [ ] **Step 16: Create `frontend/src/styles/_components.scss` (trimmed to the layout helpers Angular Material doesn't provide — buttons/tables/tags/forms are replaced by Material components starting Task 13)**

```scss
// Patrones de layout compartidos que Angular Material no cubre.
// Todo lo que Material sí resuelve (botones, tablas, campos, chips) se usa
// directamente vía sus componentes, estilizados por el tema en material-theme.scss.

.page-container {
  max-width: 1120px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-4);
}

.empty-state {
  color: var(--text-muted);
  font: var(--font-body-sm);
  padding: var(--space-5) 0;
  text-align: center;
}
```

- [ ] **Step 17: Create `frontend/src/styles.scss` (Tailwind directives and raw element styling removed — Material owns buttons/inputs/tables starting Task 13)**

```scss
// Hoja global. Los tokens (colors/typography/spacing/radius-border/shadow) se cargan
// antes que este archivo vía angular.json → styles. Aquí solo se consumen, nunca se
// redefinen valores crudos.
@use './styles/components';

* {
  box-sizing: border-box;
}

html,
body {
  height: 100%;
}

body {
  margin: 0;
  font: var(--font-body);
  color: var(--text-body);
  background: var(--surface-page);
}

h1 {
  font: var(--font-h1);
  color: var(--text-title);
  margin: 0 0 var(--space-4);
}

h2 {
  font: var(--font-h2);
  color: var(--text-title);
  margin: 0 0 var(--space-3);
}

::-webkit-scrollbar {
  width: 10px;
  height: 10px;
}

::-webkit-scrollbar-thumb {
  background: var(--border-strong);
  border-radius: var(--radius-pill);
}

.material-symbols-outlined {
  font-family: 'Material Symbols Outlined';
  font-weight: normal;
  font-style: normal;
  line-height: 1;
  font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 20;
}
```

- [ ] **Step 18: Install dependencies and verify the build**

Run: `cd frontend && npm install && npx ng build`
Expected: build succeeds and emits `dist/frontend/browser/index.html` with no errors (the app renders nothing but the empty router outlet — that's expected, routes are still empty).

- [ ] **Step 19: Commit reminder**

Do not commit. Tell the user Task 12 is ready for review.

---

## Task 13: Angular Material install + custom theme on tokens

**Files:**
- Modify: `frontend/package.json` (Material + CDK added by `ng add`)
- Modify: `frontend/src/app/app.config.ts` (animations provider added by `ng add`)
- Create: `frontend/src/styles/material-theme.scss`
- Modify: `frontend/src/styles.scss` (import the theme partial)

**Interfaces:**
- Consumes: the design tokens from Task 12 (`colors.css`, `typography.css`, `radius-border.css`).
- Produces: Angular Material's M3 system CSS variables (`--mat-sys-*`) overridden to match the existing design tokens — consumed visually by every Material component used from Task 19 onward. No TypeScript interface changes.

- [ ] **Step 1: Run the Material schematic**

Run: `cd frontend && npx ng add @angular/material --theme=custom --typography=true --animations=enabled --skip-confirmation`
Expected: `package.json` gains `@angular/material` and `@angular/cdk` at a version matching Angular 18; `app.config.ts` gains an animations provider (`provideAnimationsAsync()` or `provideAnimations()`, depending on the exact schematic version — keep whichever it generates); a base theme include appears in `styles.scss` or a generated theme file.

- [ ] **Step 2: Move/replace the generated theme with a token-driven one — create `frontend/src/styles/material-theme.scss`**

```scss
// Tema Angular Material (M3) construido sobre los tokens existentes en
// src/styles/tokens/*.css, en vez del tema azul por defecto de Material.
// Ver docs/superpowers/specs/2026-08-30-scaffold-desde-slime-erp-old-design.md.

@use '@angular/material' as mat;

html {
  @include mat.theme((
    color: (
      theme-type: light,
      primary: mat.$blue-palette,
      tertiary: mat.$cyan-palette,
    ),
    typography: Roboto,
    density: 0,
  ));

  // Overrides puntuales: los tokens de la app (colors.css) son la fuente de
  // verdad, no la paleta generada por Material. Ajustar estos nombres de
  // variable si `ng serve` muestra que Material usa otros tokens --mat-sys-*
  // en la versión instalada (inspeccionar con las devtools del navegador).
  --mat-sys-primary: var(--primary-base);
  --mat-sys-on-primary: var(--white);
  --mat-sys-primary-container: var(--primary-background);
  --mat-sys-on-primary-container: var(--primary-dark);
  --mat-sys-surface: var(--surface-card);
  --mat-sys-on-surface: var(--text-body);
  --mat-sys-outline: var(--border-strong);
  --mat-sys-corner-medium: var(--radius-2);
  --mat-sys-corner-full: var(--radius-pill);
}
```

- [ ] **Step 3: Modify `frontend/src/styles.scss` — import the theme partial first**

```scss
@use './styles/material-theme';
@use './styles/components';
```

(keep the rest of `styles.scss` from Task 12 unchanged below this line)

- [ ] **Step 4: Remove the Tailwind-era leftovers if the schematic added anything unrelated**

Confirm `frontend/tailwind.config.js` does not exist (it was never created in Task 12) and that `frontend/package.json` has no `tailwindcss`, `autoprefixer`, or `postcss` entries.

- [ ] **Step 5: Verify the build**

Run: `cd frontend && npx ng build`
Expected: `BUILD SUCCESS`, no SCSS errors from `material-theme.scss`.

- [ ] **Step 6: Visual verification**

Run: `cd frontend && npx ng serve`, open `http://localhost:4200` in a browser. Expected: a blank page (no routes yet) with no console errors about missing Material theme styles. Inspect a temporary `<button mat-flat-button color="primary">test</button>` dropped into `app.component.html` to confirm it renders in the brand blue (`#295eff`-ish) rather than Material's default indigo — then remove the temporary button. If the color doesn't match, adjust the `--mat-sys-primary`-style overrides in `material-theme.scss` to whatever variable names the installed Material version actually consumes (check via browser devtools on a rendered `mat-flat-button`).

- [ ] **Step 7: Commit reminder**

Do not commit. Tell the user Task 13 is ready for review.

---

## Task 14: core/models

**Files:**
- Create: `frontend/src/app/core/models/models.ts`

**Interfaces:**
- Produces: `Rol`, `Permiso`, `LoginResponse`, `Usuario`, `Cliente`, `Producto` types — consumed by every service/component from Task 15 onward.

- [ ] **Step 1: Create `frontend/src/app/core/models/models.ts`**

```typescript
export type Rol = 'SUPER_ADMIN' | 'ADMIN' | 'VENDEDOR' | 'COMPRADOR' | 'VISUALIZADOR';

export type Permiso =
  | 'CLIENTES_VER'
  | 'CLIENTES_EDITAR'
  | 'PRODUCTOS_VER'
  | 'PRODUCTOS_EDITAR'
  | 'USUARIOS_VER'
  | 'USUARIOS_EDITAR'
  | 'EMPRESAS_ADMINISTRAR';

export interface LoginResponse {
  token: string;
  usuarioId: number;
  tenantId: number;
  tenantNombre: string;
  nombre: string;
  email: string;
  rut: string;
  rol: Rol;
  permisos: Permiso[];
}

export interface Usuario {
  id: number;
  nombre: string;
  rut: string;
  email: string;
  rol: Rol;
  activo: boolean;
  fechaCreacion: string;
}

export interface Cliente {
  id: number;
  nombre: string;
  rut: string | null;
  email: string | null;
  telefono: string | null;
  direccion: string | null;
  activo: boolean;
}

export interface Producto {
  id: number;
  sku: string | null;
  nombre: string;
  descripcion: string | null;
  precioVenta: number;
  precioCompra: number;
  stock: number;
  controlaStock: boolean;
  activo: boolean;
}
```

- [ ] **Step 2: Verify the build**

Run: `cd frontend && npx ng build`
Expected: `BUILD SUCCESS` (nothing consumes this file yet, so this only checks the file itself is valid TypeScript).

---

## Task 15: auth.service

**Files:**
- Create: `frontend/src/app/core/services/auth.service.ts`
- Test: `frontend/src/app/core/services/auth.service.spec.ts`

**Interfaces:**
- Consumes: `LoginResponse`, `Permiso` (Task 14), `environment.apiUrl` (Task 12).
- Produces: `AuthService.login(email,password)` → `Observable<LoginResponse>`, `.logout()`, `.token` getter, `.estaAutenticado` getter, `.tienePermiso(permiso)`, `.session` signal — consumed by Task 16 (interceptor), Task 17 (guard), Task 19 (layout), Task 20 (login), and every feature component that needs to gate UI by permission.

- [ ] **Step 1: Write the failing test**

```typescript
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { AuthService } from './auth.service';
import { environment } from '../../../environments/environment';
import { LoginResponse } from '../models/models';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  const respuesta: LoginResponse = {
    token: 'tok-123',
    usuarioId: 1,
    tenantId: 1,
    tenantNombre: 'Empresa Demo',
    nombre: 'Admin Demo',
    email: 'admin@demo.cl',
    rut: '15.234.567-8',
    rol: 'ADMIN',
    permisos: ['CLIENTES_VER', 'CLIENTES_EDITAR'],
  };

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('guarda la sesión y expone el token tras un login exitoso', () => {
    service.login('admin@demo.cl', 'admin123').subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/auth/login`);
    expect(req.request.method).toBe('POST');
    req.flush(respuesta);

    expect(service.estaAutenticado).toBeTrue();
    expect(service.token).toBe('tok-123');
    expect(JSON.parse(localStorage.getItem('slime_erp_session')!)).toEqual(respuesta);
  });

  it('tienePermiso refleja los permisos de la sesión activa', () => {
    service.login('admin@demo.cl', 'admin123').subscribe();
    httpMock.expectOne(`${environment.apiUrl}/auth/login`).flush(respuesta);

    expect(service.tienePermiso('CLIENTES_VER')).toBeTrue();
    expect(service.tienePermiso('USUARIOS_EDITAR')).toBeFalse();
  });

  it('logout limpia la sesión', () => {
    service.login('admin@demo.cl', 'admin123').subscribe();
    httpMock.expectOne(`${environment.apiUrl}/auth/login`).flush(respuesta);

    service.logout();

    expect(service.estaAutenticado).toBeFalse();
    expect(localStorage.getItem('slime_erp_session')).toBeNull();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx ng test --watch=false --include='**/auth.service.spec.ts'`
Expected: fails to compile — `AuthService` doesn't exist yet.

- [ ] **Step 3: Create `frontend/src/app/core/services/auth.service.ts`**

```typescript
import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LoginResponse, Permiso } from '../models/models';

const STORAGE_KEY = 'slime_erp_session';

@Injectable({ providedIn: 'root' })
export class AuthService {
  session = signal<LoginResponse | null>(this.leerSesionGuardada());

  constructor(private http: HttpClient) {}

  login(email: string, password: string): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${environment.apiUrl}/auth/login`, { email, password })
      .pipe(
        tap((res) => {
          localStorage.setItem(STORAGE_KEY, JSON.stringify(res));
          this.session.set(res);
        })
      );
  }

  logout(): void {
    localStorage.removeItem(STORAGE_KEY);
    this.session.set(null);
  }

  get token(): string | null {
    return this.session()?.token ?? null;
  }

  get estaAutenticado(): boolean {
    return this.session() !== null;
  }

  tienePermiso(permiso: Permiso): boolean {
    return this.session()?.permisos.includes(permiso) ?? false;
  }

  private leerSesionGuardada(): LoginResponse | null {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as LoginResponse) : null;
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx ng test --watch=false --include='**/auth.service.spec.ts'`
Expected: 3 specs pass.

---

## Task 16: auth.interceptor

**Files:**
- Create: `frontend/src/app/core/interceptors/auth.interceptor.ts`
- Test: `frontend/src/app/core/interceptors/auth.interceptor.spec.ts`

**Interfaces:**
- Consumes: `AuthService.token` (Task 15).
- Produces: `authInterceptor: HttpInterceptorFn` — consumed by Task 18 (`app.config.ts`).

- [ ] **Step 1: Write the failing test**

```typescript
import { TestBed } from '@angular/core/testing';
import { HttpRequest, HttpHandlerFn, HttpEvent } from '@angular/common/http';
import { of } from 'rxjs';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from '../services/auth.service';

describe('authInterceptor', () => {
  let authServiceStub: Partial<AuthService>;

  beforeEach(() => {
    authServiceStub = { token: null };
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: authServiceStub }],
    });
  });

  it('no agrega Authorization si no hay token', (done) => {
    const req = new HttpRequest('GET', '/api/clientes');
    const next: HttpHandlerFn = (r) => {
      expect(r.headers.has('Authorization')).toBeFalse();
      done();
      return of({} as HttpEvent<unknown>);
    };

    TestBed.runInInjectionContext(() => authInterceptor(req, next));
  });

  it('agrega el header Authorization cuando hay token', (done) => {
    authServiceStub.token = 'tok-123';
    const req = new HttpRequest('GET', '/api/clientes');
    const next: HttpHandlerFn = (r) => {
      expect(r.headers.get('Authorization')).toBe('Bearer tok-123');
      done();
      return of({} as HttpEvent<unknown>);
    };

    TestBed.runInInjectionContext(() => authInterceptor(req, next));
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx ng test --watch=false --include='**/auth.interceptor.spec.ts'`
Expected: fails to compile — `authInterceptor` doesn't exist yet.

- [ ] **Step 3: Create `frontend/src/app/core/interceptors/auth.interceptor.ts`**

```typescript
import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.token;

  if (!token) {
    return next(req);
  }

  return next(
    req.clone({
      setHeaders: { Authorization: `Bearer ${token}` },
    })
  );
};
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx ng test --watch=false --include='**/auth.interceptor.spec.ts'`
Expected: 2 specs pass.

---

## Task 17: auth.guard

**Files:**
- Create: `frontend/src/app/core/guards/auth.guard.ts`
- Test: `frontend/src/app/core/guards/auth.guard.spec.ts`

**Interfaces:**
- Consumes: `AuthService.estaAutenticado` (Task 15).
- Produces: `authGuard: CanActivateFn` — consumed by Task 18 (`app.routes.ts`).

- [ ] **Step 1: Write the failing test**

```typescript
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';

describe('authGuard', () => {
  let authServiceStub: Partial<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(() => {
    authServiceStub = { estaAutenticado: false };
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceStub },
        { provide: Router, useValue: routerSpy },
      ],
    });
  });

  it('permite el acceso cuando hay sesión activa', () => {
    authServiceStub.estaAutenticado = true;
    const resultado = TestBed.runInInjectionContext(() => authGuard({} as any, {} as any));
    expect(resultado).toBeTrue();
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });

  it('redirige a /login cuando no hay sesión', () => {
    const resultado = TestBed.runInInjectionContext(() => authGuard({} as any, {} as any));
    expect(resultado).toBeFalse();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/login']);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx ng test --watch=false --include='**/auth.guard.spec.ts'`
Expected: fails to compile — `authGuard` doesn't exist yet.

- [ ] **Step 3: Create `frontend/src/app/core/guards/auth.guard.ts`**

```typescript
import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.estaAutenticado) {
    return true;
  }

  router.navigate(['/login']);
  return false;
};
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx ng test --watch=false --include='**/auth.guard.spec.ts'`
Expected: 2 specs pass.

---

## Task 18: Routes + app.config wiring

**Files:**
- Modify: `frontend/src/app/app.routes.ts`
- Modify: `frontend/src/app/app.config.ts`

**Interfaces:**
- Consumes: `authGuard` (Task 17), `authInterceptor` (Task 16), the `MAT_ICON_DEFAULT_OPTIONS` token from `@angular/material/icon`, the animations provider added in Task 13.
- Produces: the routing table (`login`, `dashboard`, `clientes`, `productos`, `usuarios`) that Tasks 19–24's `loadComponent` lazy imports point at.

*(This task only wires routes to components; the components themselves (`LoginComponent`, `LayoutComponent`, `DashboardComponent`, `ClientesComponent`, `ProductosComponent`, `UsuariosComponent`) are created in Tasks 19–24. `ng build` will fail until all of those exist — that's expected and resolved by the end of Task 24; each of those tasks runs its own component-level tests independently via `ng test --include`.)*

- [ ] **Step 1: Replace `frontend/src/app/app.routes.ts`**

```typescript
import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: '',
    loadComponent: () => import('./layout/layout.component').then((m) => m.LayoutComponent),
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
      },
      {
        path: 'clientes',
        loadComponent: () => import('./features/clientes/clientes.component').then((m) => m.ClientesComponent),
      },
      {
        path: 'productos',
        loadComponent: () => import('./features/productos/productos.component').then((m) => m.ProductosComponent),
      },
      {
        path: 'usuarios',
        loadComponent: () => import('./features/usuarios/usuarios.component').then((m) => m.UsuariosComponent),
      },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];
```

- [ ] **Step 2: Replace `frontend/src/app/app.config.ts`**

```typescript
import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { MAT_ICON_DEFAULT_OPTIONS } from '@angular/material/icon';

import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideAnimationsAsync(),
    { provide: MAT_ICON_DEFAULT_OPTIONS, useValue: { fontSet: 'material-symbols-outlined' } },
  ],
};
```

If Task 13's `ng add` schematic generated a plain `provideAnimations()` call instead of `provideAnimationsAsync()`, keep whichever one it actually added — don't run both.

- [ ] **Step 3: Note the expected (temporary) build failure**

Run: `cd frontend && npx ng build`
Expected: fails with "Cannot find module './features/login/login.component'" (and similarly for layout/dashboard/clientes/productos/usuarios) — this is expected until Tasks 19–24 create those files. Do not treat this as a Task 18 failure; re-run the full build at the end of Task 24.

---

## Task 19: Layout shell (Material, permission-filtered menu)

**Files:**
- Create: `frontend/src/app/layout/layout.component.ts`
- Create: `frontend/src/app/layout/layout.component.html`
- Create: `frontend/src/app/layout/layout.component.scss`
- Test: `frontend/src/app/layout/layout.component.spec.ts`

**Interfaces:**
- Consumes: `AuthService` (Task 15), `Permiso` (Task 14).
- Produces: `LayoutComponent` with an `items` getter (menu filtered by permission) and `cerrarSesion()` — this is the `loadComponent` target for the empty-path route in Task 18; nothing else depends on its internals.

- [ ] **Step 1: Write the failing test (direct instantiation — no TestBed render, to keep the test independent of Material's animation/rendering setup)**

```typescript
import { LayoutComponent } from './layout.component';
import { AuthService } from '../core/services/auth.service';
import { Router } from '@angular/router';

describe('LayoutComponent', () => {
  function crear(permisos: string[]) {
    const authStub = {
      session: () => ({ nombre: 'Admin Demo' }),
      tienePermiso: (p: string) => permisos.includes(p),
      logout: jasmine.createSpy('logout'),
    } as unknown as AuthService;
    const routerStub = { navigate: jasmine.createSpy('navigate') } as unknown as Router;
    return { layout: new LayoutComponent(authStub, routerStub), authStub, routerStub };
  }

  it('siempre incluye Dashboard sin requerir permiso', () => {
    const { layout } = crear([]);
    expect(layout.items.map((i) => i.label)).toContain('Dashboard');
  });

  it('oculta Usuarios si no se tiene USUARIOS_VER', () => {
    const { layout } = crear(['CLIENTES_VER']);
    expect(layout.items.map((i) => i.label)).not.toContain('Usuarios');
  });

  it('muestra Usuarios si se tiene USUARIOS_VER', () => {
    const { layout } = crear(['USUARIOS_VER']);
    expect(layout.items.map((i) => i.label)).toContain('Usuarios');
  });

  it('cerrarSesion desloguea y navega a /login', () => {
    const { layout, authStub, routerStub } = crear([]);
    layout.cerrarSesion();
    expect(authStub.logout).toHaveBeenCalled();
    expect(routerStub.navigate).toHaveBeenCalledWith(['/login']);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx ng test --watch=false --include='**/layout.component.spec.ts'`
Expected: fails to compile — `LayoutComponent` doesn't exist yet.

- [ ] **Step 3: Create `frontend/src/app/layout/layout.component.ts`**

```typescript
import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from '../core/services/auth.service';
import { Permiso } from '../core/models/models';

interface NavItem {
  ruta: string;
  label: string;
  icono: string;
  permiso?: Permiso;
}

const NAV_ITEMS: NavItem[] = [
  { ruta: '/dashboard', label: 'Dashboard', icono: 'dashboard' },
  { ruta: '/clientes', label: 'Clientes', icono: 'group', permiso: 'CLIENTES_VER' },
  { ruta: '/productos', label: 'Productos', icono: 'inventory_2', permiso: 'PRODUCTOS_VER' },
  { ruta: '/usuarios', label: 'Usuarios', icono: 'manage_accounts', permiso: 'USUARIOS_VER' },
];

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [
    CommonModule,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatSidenavModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
  ],
  templateUrl: './layout.component.html',
  styleUrl: './layout.component.scss',
})
export class LayoutComponent {
  constructor(public auth: AuthService, private router: Router) {}

  get items(): NavItem[] {
    return NAV_ITEMS.filter((item) => !item.permiso || this.auth.tienePermiso(item.permiso));
  }

  cerrarSesion(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
```

- [ ] **Step 4: Create `frontend/src/app/layout/layout.component.html`**

```html
<mat-sidenav-container class="app-shell">
  <mat-sidenav #sidenav mode="side" opened class="sidebar">
    <nav mat-nav-list>
      @for (item of items; track item.ruta) {
        <a mat-list-item [routerLink]="item.ruta" routerLinkActive="active">
          <mat-icon matListItemIcon>{{ item.icono }}</mat-icon>
          <span matListItemTitle>{{ item.label }}</span>
        </a>
      }
    </nav>
    <div class="user-box">
      <div class="user-name">{{ auth.session()?.nombre }}</div>
      <button mat-stroked-button (click)="cerrarSesion()">Cerrar sesión</button>
    </div>
  </mat-sidenav>

  <mat-sidenav-content>
    <mat-toolbar color="primary" class="topbar">
      <button mat-icon-button (click)="sidenav.toggle()">
        <mat-icon>menu</mat-icon>
      </button>
      <span class="app-name">Slime ERP</span>
    </mat-toolbar>
    <main class="content">
      <router-outlet></router-outlet>
    </main>
  </mat-sidenav-content>
</mat-sidenav-container>
```

- [ ] **Step 5: Create `frontend/src/app/layout/layout.component.scss`**

```scss
:host {
  display: block;
  height: 100vh;
}

.app-shell {
  height: 100vh;
}

.sidebar {
  width: 256px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.sidebar nav {
  flex: 1;
  overflow-y: auto;
}

.user-box {
  padding: var(--space-4);
  border-top: var(--border-width-default) solid var(--border-default);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.user-name {
  font: var(--font-body-sm);
  color: var(--sidebar-text);
}

.topbar {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.app-name {
  font-weight: 800;
  font-size: 16px;
}

.content {
  padding: var(--space-6);
  background: var(--surface-page);
  min-height: calc(100vh - 64px);
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd frontend && npx ng test --watch=false --include='**/layout.component.spec.ts'`
Expected: 4 specs pass.

---

## Task 20: Login screen (functional, Material)

**Files:**
- Create: `frontend/src/app/features/login/login.component.ts`
- Create: `frontend/src/app/features/login/login.component.html`
- Create: `frontend/src/app/features/login/login.component.scss`
- Test: `frontend/src/app/features/login/login.component.spec.ts`

**Interfaces:**
- Consumes: `AuthService.login` (Task 15), `Router` (Angular).
- Produces: `LoginComponent` — the `loadComponent` target for the `/login` route from Task 18.

- [ ] **Step 1: Write the failing test**

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { LoginComponent } from './login.component';
import { AuthService } from '../../core/services/auth.service';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let component: LoginComponent;
  let authSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    authSpy = jasmine.createSpyObj('AuthService', ['login']);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        { provide: AuthService, useValue: authSpy },
        { provide: Router, useValue: routerSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
  });

  it('navega a /dashboard cuando el login es exitoso', () => {
    authSpy.login.and.returnValue(of({} as any));

    component.ingresar();

    expect(routerSpy.navigate).toHaveBeenCalledWith(['/dashboard']);
    expect(component.error).toBe('');
  });

  it('muestra un error cuando el login falla', () => {
    authSpy.login.and.returnValue(throwError(() => new Error('401')));

    component.ingresar();

    expect(component.error).toBe('Email o contraseña incorrectos.');
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx ng test --watch=false --include='**/login.component.spec.ts'`
Expected: fails to compile — `LoginComponent` doesn't exist yet.

- [ ] **Step 3: Create `frontend/src/app/features/login/login.component.ts`**

```typescript
import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatCardModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  email = 'admin@demo.cl';
  password = 'admin123';
  error = '';
  cargando = false;

  constructor(private auth: AuthService, private router: Router) {}

  ingresar(): void {
    this.error = '';
    this.cargando = true;
    this.auth.login(this.email, this.password).subscribe({
      next: () => {
        this.cargando = false;
        this.router.navigate(['/dashboard']);
      },
      error: () => {
        this.cargando = false;
        this.error = 'Email o contraseña incorrectos.';
      },
    });
  }
}
```

- [ ] **Step 4: Create `frontend/src/app/features/login/login.component.html`**

```html
<div class="login-page">
  <mat-card class="login-card">
    <form (ngSubmit)="ingresar()">
      <h1>Slime ERP</h1>
      <p class="subtitle">Ingresa a tu cuenta</p>

      <mat-form-field appearance="outline">
        <mat-label>Email</mat-label>
        <input matInput type="email" name="email" [(ngModel)]="email" required />
      </mat-form-field>

      <mat-form-field appearance="outline">
        <mat-label>Contraseña</mat-label>
        <input matInput type="password" name="password" [(ngModel)]="password" required />
      </mat-form-field>

      @if (error) {
        <p class="error">{{ error }}</p>
      }

      <button mat-flat-button color="primary" type="submit" [disabled]="cargando">
        {{ cargando ? 'Ingresando...' : 'Ingresar' }}
      </button>

      <p class="hint">Usuario demo: admin&#64;demo.cl / admin123</p>
    </form>
  </mat-card>
</div>
```

- [ ] **Step 5: Create `frontend/src/app/features/login/login.component.scss`**

```scss
:host {
  display: block;
}

.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--surface-page);
}

.login-card {
  width: 340px;
  padding: var(--space-6);
}

form {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

mat-form-field {
  width: 100%;
}

.error {
  color: var(--error-base);
  font: var(--font-body-sm);
  margin: 0;
}

.hint {
  font: var(--font-legals);
  color: var(--text-disabled);
  margin: var(--space-2) 0 0;
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd frontend && npx ng test --watch=false --include='**/login.component.spec.ts'`
Expected: 2 specs pass.

---

## Task 21: Dashboard screen (static, Material)

**Files:**
- Create: `frontend/src/app/features/dashboard/dashboard.component.ts`
- Create: `frontend/src/app/features/dashboard/dashboard.component.html`
- Create: `frontend/src/app/features/dashboard/dashboard.component.scss`
- Test: `frontend/src/app/features/dashboard/dashboard.component.spec.ts`

**Interfaces:**
- Consumes: `AuthService.session` (Task 15).
- Produces: `DashboardComponent` — the `loadComponent` target for the `/dashboard` route from Task 18.

*(This is a fresh, static component — not a copy of `slime-erp_old`'s dashboard, which is entirely driven by `DashboardService`/ventas data that's out of scope. Per the spec, KPI values are static placeholders for now.)*

- [ ] **Step 1: Write the failing test**

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DashboardComponent } from './dashboard.component';
import { AuthService } from '../../core/services/auth.service';

describe('DashboardComponent', () => {
  let fixture: ComponentFixture<DashboardComponent>;

  beforeEach(async () => {
    const authStub = { session: () => ({ nombre: 'Admin Demo' }) } as unknown as AuthService;
    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [{ provide: AuthService, useValue: authStub }],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
  });

  it('muestra el nombre del usuario logueado', () => {
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Admin Demo');
  });

  it('muestra tres tarjetas de KPI', () => {
    const tarjetas = (fixture.nativeElement as HTMLElement).querySelectorAll('mat-card');
    expect(tarjetas.length).toBe(3);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx ng test --watch=false --include='**/dashboard.component.spec.ts'`
Expected: fails to compile — `DashboardComponent` doesn't exist yet.

- [ ] **Step 3: Create `frontend/src/app/features/dashboard/dashboard.component.ts`**

```typescript
import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { AuthService } from '../../core/services/auth.service';

interface Kpi {
  label: string;
  value: string;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, MatCardModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent {
  kpis: Kpi[] = [
    { label: 'Clientes activos', value: '—' },
    { label: 'Productos activos', value: '—' },
    { label: 'Usuarios del tenant', value: '—' },
  ];

  constructor(public auth: AuthService) {}
}
```

- [ ] **Step 4: Create `frontend/src/app/features/dashboard/dashboard.component.html`**

```html
<h1>Bienvenido, {{ auth.session()?.nombre }}</h1>

<section class="kpi-grid">
  @for (kpi of kpis; track kpi.label) {
    <mat-card class="kpi-card">
      <span class="kpi-label">{{ kpi.label }}</span>
      <span class="kpi-value">{{ kpi.value }}</span>
    </mat-card>
  }
</section>
```

- [ ] **Step 5: Create `frontend/src/app/features/dashboard/dashboard.component.scss`**

```scss
h1 {
  font: var(--font-h2);
  color: var(--text-title);
  margin: 0 0 var(--space-4);
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-4);
}

.kpi-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  padding: var(--space-4);
}

.kpi-label {
  font: var(--font-body-sm);
  color: var(--text-muted);
}

.kpi-value {
  font: var(--font-h2);
  color: var(--text-title);
  font-weight: 700;
}

@media (max-width: 720px) {
  .kpi-grid {
    grid-template-columns: 1fr;
  }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd frontend && npx ng test --watch=false --include='**/dashboard.component.spec.ts'`
Expected: 2 specs pass.

---

## Task 22: Usuarios feature (list + dialog)

**Files:**
- Create: `frontend/src/app/core/services/usuario.service.ts`
- Create: `frontend/src/app/features/usuarios/usuarios.component.ts`
- Create: `frontend/src/app/features/usuarios/usuarios.component.html`
- Create: `frontend/src/app/features/usuarios/usuarios.component.scss`
- Create: `frontend/src/app/features/usuarios/usuario-form-dialog.component.ts`
- Create: `frontend/src/app/features/usuarios/usuario-form-dialog.component.html`
- Test: `frontend/src/app/core/services/usuario.service.spec.ts`
- Test: `frontend/src/app/features/usuarios/usuario-form-dialog.component.spec.ts`

**Interfaces:**
- Consumes: `Usuario`, `Rol` (Task 14), `AuthService.tienePermiso` (Task 15).
- Produces: `UsuarioService` (`listar/crear/actualizar/desactivar`), `UsuariosComponent`, `UsuarioFormDialogComponent` — the `loadComponent` target for the `/usuarios` route from Task 18. This establishes the exact list+dialog pattern that Tasks 23 and 24 repeat for Clientes and Productos.

- [ ] **Step 1: Write the failing test for `UsuarioService`**

```typescript
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { UsuarioService } from './usuario.service';
import { environment } from '../../../environments/environment';

describe('UsuarioService', () => {
  let service: UsuarioService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(UsuarioService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('listar hace GET a /usuarios', () => {
    service.listar().subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/usuarios`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('crear hace POST con el body recibido', () => {
    const request = { nombre: 'Vendedor Uno', rut: '1-9', email: 'v1@demo.cl', password: 'clave123', rol: 'VENDEDOR' as const };
    service.crear(request).subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/usuarios`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({});
  });

  it('desactivar hace DELETE al id correspondiente', () => {
    service.desactivar(5).subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/usuarios/5`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx ng test --watch=false --include='**/usuario.service.spec.ts'`
Expected: fails to compile — `UsuarioService` doesn't exist yet.

- [ ] **Step 3: Create `frontend/src/app/core/services/usuario.service.ts`**

```typescript
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Usuario, Rol } from '../models/models';

export interface UsuarioRequest {
  nombre: string;
  rut: string;
  email: string;
  password?: string;
  rol: Rol;
  activo?: boolean;
}

@Injectable({ providedIn: 'root' })
export class UsuarioService {
  private readonly base = `${environment.apiUrl}/usuarios`;

  constructor(private http: HttpClient) {}

  listar(): Observable<Usuario[]> {
    return this.http.get<Usuario[]>(this.base);
  }

  crear(request: UsuarioRequest): Observable<Usuario> {
    return this.http.post<Usuario>(this.base, request);
  }

  actualizar(id: number, request: UsuarioRequest): Observable<Usuario> {
    return this.http.put<Usuario>(`${this.base}/${id}`, request);
  }

  desactivar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx ng test --watch=false --include='**/usuario.service.spec.ts'`
Expected: 3 specs pass.

- [ ] **Step 5: Write the failing test for `UsuarioFormDialogComponent`**

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { UsuarioFormDialogComponent } from './usuario-form-dialog.component';

describe('UsuarioFormDialogComponent', () => {
  let fixture: ComponentFixture<UsuarioFormDialogComponent>;
  let component: UsuarioFormDialogComponent;
  let dialogRefSpy: jasmine.SpyObj<MatDialogRef<UsuarioFormDialogComponent>>;

  async function crear(data: any) {
    dialogRefSpy = jasmine.createSpyObj('MatDialogRef', ['close']);
    await TestBed.configureTestingModule({
      imports: [UsuarioFormDialogComponent],
      providers: [
        { provide: MatDialogRef, useValue: dialogRefSpy },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(UsuarioFormDialogComponent);
    component = fixture.componentInstance;
  }

  it('en modo creación arranca con campos vacíos y rol VENDEDOR', async () => {
    await crear(null);
    expect(component.esEdicion).toBeFalse();
    expect(component.nombre).toBe('');
    expect(component.rol).toBe('VENDEDOR');
  });

  it('en modo edición precarga los datos del usuario', async () => {
    await crear({ id: 5, nombre: 'Vendedor Uno', rut: '1-9', email: 'v1@demo.cl', rol: 'VENDEDOR', activo: true });
    expect(component.esEdicion).toBeTrue();
    expect(component.nombre).toBe('Vendedor Uno');
  });

  it('guardar cierra el dialog con el request armado, sin password si está vacío', async () => {
    await crear(null);
    component.nombre = 'Nuevo';
    component.rut = '2-9';
    component.email = 'nuevo@demo.cl';
    component.password = '';
    component.rol = 'ADMIN';

    component.guardar();

    expect(dialogRefSpy.close).toHaveBeenCalledWith({
      nombre: 'Nuevo',
      rut: '2-9',
      email: 'nuevo@demo.cl',
      rol: 'ADMIN',
    });
  });
});
```

- [ ] **Step 6: Run test to verify it fails**

Run: `cd frontend && npx ng test --watch=false --include='**/usuario-form-dialog.component.spec.ts'`
Expected: fails to compile — `UsuarioFormDialogComponent` doesn't exist yet.

- [ ] **Step 7: Create `frontend/src/app/features/usuarios/usuario-form-dialog.component.ts`**

```typescript
import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { Rol, Usuario } from '../../core/models/models';
import { UsuarioRequest } from '../../core/services/usuario.service';

const ROLES_ASIGNABLES: Rol[] = ['ADMIN', 'VENDEDOR', 'COMPRADOR', 'VISUALIZADOR'];

@Component({
  selector: 'app-usuario-form-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule],
  templateUrl: './usuario-form-dialog.component.html',
})
export class UsuarioFormDialogComponent {
  roles = ROLES_ASIGNABLES;
  esEdicion = !!this.data;

  nombre = this.data?.nombre ?? '';
  rut = this.data?.rut ?? '';
  email = this.data?.email ?? '';
  password = '';
  rol: Rol = this.data?.rol ?? 'VENDEDOR';

  constructor(
    private ref: MatDialogRef<UsuarioFormDialogComponent, UsuarioRequest>,
    @Inject(MAT_DIALOG_DATA) public data: Usuario | null
  ) {}

  guardar(): void {
    const request: UsuarioRequest = {
      nombre: this.nombre,
      rut: this.rut,
      email: this.email,
      rol: this.rol,
      ...(this.password ? { password: this.password } : {}),
    };
    this.ref.close(request);
  }

  cancelar(): void {
    this.ref.close();
  }
}
```

- [ ] **Step 8: Create `frontend/src/app/features/usuarios/usuario-form-dialog.component.html`**

```html
<h2 mat-dialog-title>{{ esEdicion ? 'Editar usuario' : 'Nuevo usuario' }}</h2>
<div mat-dialog-content class="form">
  <mat-form-field appearance="outline">
    <mat-label>Nombre</mat-label>
    <input matInput [(ngModel)]="nombre" required />
  </mat-form-field>

  <mat-form-field appearance="outline">
    <mat-label>RUT</mat-label>
    <input matInput [(ngModel)]="rut" required />
  </mat-form-field>

  <mat-form-field appearance="outline">
    <mat-label>Email</mat-label>
    <input matInput type="email" [(ngModel)]="email" required />
  </mat-form-field>

  <mat-form-field appearance="outline">
    <mat-label>{{ esEdicion ? 'Nueva contraseña (opcional)' : 'Contraseña' }}</mat-label>
    <input matInput type="password" [(ngModel)]="password" />
  </mat-form-field>

  <mat-form-field appearance="outline">
    <mat-label>Rol</mat-label>
    <mat-select [(ngModel)]="rol">
      @for (r of roles; track r) {
        <mat-option [value]="r">{{ r }}</mat-option>
      }
    </mat-select>
  </mat-form-field>
</div>
<div mat-dialog-actions align="end">
  <button mat-button (click)="cancelar()">Cancelar</button>
  <button mat-flat-button color="primary" (click)="guardar()">Guardar</button>
</div>
```

- [ ] **Step 9: Run test to verify it passes**

Run: `cd frontend && npx ng test --watch=false --include='**/usuario-form-dialog.component.spec.ts'`
Expected: 3 specs pass.

- [ ] **Step 10: Create `frontend/src/app/features/usuarios/usuarios.component.ts`**

```typescript
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';
import { UsuarioService, UsuarioRequest } from '../../core/services/usuario.service';
import { AuthService } from '../../core/services/auth.service';
import { Usuario } from '../../core/models/models';
import { UsuarioFormDialogComponent } from './usuario-form-dialog.component';

@Component({
  selector: 'app-usuarios',
  standalone: true,
  imports: [CommonModule, MatTableModule, MatButtonModule, MatIconModule],
  templateUrl: './usuarios.component.html',
  styleUrl: './usuarios.component.scss',
})
export class UsuariosComponent implements OnInit {
  columnas = ['nombre', 'email', 'rol', 'activo', 'acciones'];
  usuarios: Usuario[] = [];

  constructor(private usuarioService: UsuarioService, public auth: AuthService, private dialog: MatDialog) {}

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.usuarioService.listar().subscribe((usuarios) => (this.usuarios = usuarios));
  }

  abrirCrear(): void {
    const ref = this.dialog.open(UsuarioFormDialogComponent, { width: '420px' });
    ref.afterClosed().subscribe((request: UsuarioRequest | undefined) => {
      if (request) {
        this.usuarioService.crear(request).subscribe(() => this.cargar());
      }
    });
  }

  abrirEditar(usuario: Usuario): void {
    const ref = this.dialog.open(UsuarioFormDialogComponent, { width: '420px', data: usuario });
    ref.afterClosed().subscribe((request: UsuarioRequest | undefined) => {
      if (request) {
        this.usuarioService.actualizar(usuario.id, request).subscribe(() => this.cargar());
      }
    });
  }

  desactivar(usuario: Usuario): void {
    this.usuarioService.desactivar(usuario.id).subscribe(() => this.cargar());
  }
}
```

- [ ] **Step 11: Create `frontend/src/app/features/usuarios/usuarios.component.html`**

```html
<div class="page-header">
  <h1>Usuarios</h1>
  @if (auth.tienePermiso('USUARIOS_EDITAR')) {
    <button mat-flat-button color="primary" (click)="abrirCrear()">Nuevo usuario</button>
  }
</div>

<table mat-table [dataSource]="usuarios" class="mat-elevation-z1">
  <ng-container matColumnDef="nombre">
    <th mat-header-cell *matHeaderCellDef>Nombre</th>
    <td mat-cell *matCellDef="let u">{{ u.nombre }}</td>
  </ng-container>

  <ng-container matColumnDef="email">
    <th mat-header-cell *matHeaderCellDef>Email</th>
    <td mat-cell *matCellDef="let u">{{ u.email }}</td>
  </ng-container>

  <ng-container matColumnDef="rol">
    <th mat-header-cell *matHeaderCellDef>Rol</th>
    <td mat-cell *matCellDef="let u">{{ u.rol }}</td>
  </ng-container>

  <ng-container matColumnDef="activo">
    <th mat-header-cell *matHeaderCellDef>Estado</th>
    <td mat-cell *matCellDef="let u">{{ u.activo ? 'Activo' : 'Inactivo' }}</td>
  </ng-container>

  <ng-container matColumnDef="acciones">
    <th mat-header-cell *matHeaderCellDef></th>
    <td mat-cell *matCellDef="let u">
      @if (auth.tienePermiso('USUARIOS_EDITAR')) {
        <button mat-icon-button (click)="abrirEditar(u)"><mat-icon>edit</mat-icon></button>
        <button mat-icon-button (click)="desactivar(u)"><mat-icon>block</mat-icon></button>
      }
    </td>
  </ng-container>

  <tr mat-header-row *matHeaderRowDef="columnas"></tr>
  <tr mat-row *matRowDef="let row; columns: columnas"></tr>
</table>
```

- [ ] **Step 12: Create `frontend/src/app/features/usuarios/usuarios.component.scss`**

```scss
table {
  width: 100%;
}
```

- [ ] **Step 13: Compile check**

Run: `cd frontend && npx ng build`
Expected: still fails on the missing `clientes`/`productos` components from Tasks 23–24 (same as Task 18's expected failure) — but the error list should no longer mention `usuarios`.

---

## Task 23: Clientes feature (list + dialog)

**Files:**
- Create: `frontend/src/app/core/services/cliente.service.ts`
- Create: `frontend/src/app/features/clientes/clientes.component.ts`
- Create: `frontend/src/app/features/clientes/clientes.component.html`
- Create: `frontend/src/app/features/clientes/clientes.component.scss`
- Create: `frontend/src/app/features/clientes/cliente-form-dialog.component.ts`
- Create: `frontend/src/app/features/clientes/cliente-form-dialog.component.html`
- Test: `frontend/src/app/core/services/cliente.service.spec.ts`
- Test: `frontend/src/app/features/clientes/cliente-form-dialog.component.spec.ts`

**Interfaces:**
- Consumes: `Cliente` (Task 14), `AuthService.tienePermiso` (Task 15). Follows exactly the list+dialog pattern established in Task 22, gated by `CLIENTES_VER`/`CLIENTES_EDITAR` instead of `USUARIOS_*`.
- Produces: `ClienteService`, `ClientesComponent`, `ClienteFormDialogComponent` — the `loadComponent` target for the `/clientes` route from Task 18.

- [ ] **Step 1: Write the failing test for `ClienteService`**

```typescript
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ClienteService } from './cliente.service';
import { environment } from '../../../environments/environment';

describe('ClienteService', () => {
  let service: ClienteService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ClienteService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('listar hace GET a /clientes', () => {
    service.listar().subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/clientes`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('crear hace POST con el body recibido', () => {
    const request = { nombre: 'Cliente Uno', rut: '1-9', email: 'c1@demo.cl' };
    service.crear(request).subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/clientes`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({});
  });

  it('eliminar hace DELETE al id correspondiente', () => {
    service.eliminar(3).subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/clientes/3`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx ng test --watch=false --include='**/cliente.service.spec.ts'`
Expected: fails to compile — `ClienteService` doesn't exist yet.

- [ ] **Step 3: Create `frontend/src/app/core/services/cliente.service.ts`**

```typescript
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Cliente } from '../models/models';

export interface ClienteRequest {
  nombre: string;
  rut?: string;
  email?: string;
  telefono?: string;
  direccion?: string;
}

@Injectable({ providedIn: 'root' })
export class ClienteService {
  private readonly base = `${environment.apiUrl}/clientes`;

  constructor(private http: HttpClient) {}

  listar(): Observable<Cliente[]> {
    return this.http.get<Cliente[]>(this.base);
  }

  crear(request: ClienteRequest): Observable<Cliente> {
    return this.http.post<Cliente>(this.base, request);
  }

  actualizar(id: number, request: ClienteRequest): Observable<Cliente> {
    return this.http.put<Cliente>(`${this.base}/${id}`, request);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx ng test --watch=false --include='**/cliente.service.spec.ts'`
Expected: 3 specs pass.

- [ ] **Step 5: Write the failing test for `ClienteFormDialogComponent`**

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { ClienteFormDialogComponent } from './cliente-form-dialog.component';

describe('ClienteFormDialogComponent', () => {
  let fixture: ComponentFixture<ClienteFormDialogComponent>;
  let component: ClienteFormDialogComponent;
  let dialogRefSpy: jasmine.SpyObj<MatDialogRef<ClienteFormDialogComponent>>;

  async function crear(data: any) {
    dialogRefSpy = jasmine.createSpyObj('MatDialogRef', ['close']);
    await TestBed.configureTestingModule({
      imports: [ClienteFormDialogComponent],
      providers: [
        { provide: MatDialogRef, useValue: dialogRefSpy },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ClienteFormDialogComponent);
    component = fixture.componentInstance;
  }

  it('en modo creación arranca con campos vacíos', async () => {
    await crear(null);
    expect(component.esEdicion).toBeFalse();
    expect(component.nombre).toBe('');
  });

  it('en modo edición precarga los datos del cliente', async () => {
    await crear({ id: 2, nombre: 'Cliente Dos', rut: '2-9', email: 'c2@demo.cl', telefono: null, direccion: null, activo: true });
    expect(component.esEdicion).toBeTrue();
    expect(component.nombre).toBe('Cliente Dos');
  });

  it('guardar cierra el dialog con el request armado', async () => {
    await crear(null);
    component.nombre = 'Nuevo Cliente';
    component.rut = '3-9';
    component.email = 'nuevo@demo.cl';
    component.telefono = '+56911111111';
    component.direccion = 'Calle 1';

    component.guardar();

    expect(dialogRefSpy.close).toHaveBeenCalledWith({
      nombre: 'Nuevo Cliente',
      rut: '3-9',
      email: 'nuevo@demo.cl',
      telefono: '+56911111111',
      direccion: 'Calle 1',
    });
  });
});
```

- [ ] **Step 6: Run test to verify it fails**

Run: `cd frontend && npx ng test --watch=false --include='**/cliente-form-dialog.component.spec.ts'`
Expected: fails to compile — `ClienteFormDialogComponent` doesn't exist yet.

- [ ] **Step 7: Create `frontend/src/app/features/clientes/cliente-form-dialog.component.ts`**

```typescript
import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { Cliente } from '../../core/models/models';
import { ClienteRequest } from '../../core/services/cliente.service';

@Component({
  selector: 'app-cliente-form-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  templateUrl: './cliente-form-dialog.component.html',
})
export class ClienteFormDialogComponent {
  esEdicion = !!this.data;

  nombre = this.data?.nombre ?? '';
  rut = this.data?.rut ?? '';
  email = this.data?.email ?? '';
  telefono = this.data?.telefono ?? '';
  direccion = this.data?.direccion ?? '';

  constructor(
    private ref: MatDialogRef<ClienteFormDialogComponent, ClienteRequest>,
    @Inject(MAT_DIALOG_DATA) public data: Cliente | null
  ) {}

  guardar(): void {
    const request: ClienteRequest = {
      nombre: this.nombre,
      rut: this.rut,
      email: this.email,
      telefono: this.telefono,
      direccion: this.direccion,
    };
    this.ref.close(request);
  }

  cancelar(): void {
    this.ref.close();
  }
}
```

- [ ] **Step 8: Create `frontend/src/app/features/clientes/cliente-form-dialog.component.html`**

```html
<h2 mat-dialog-title>{{ esEdicion ? 'Editar cliente' : 'Nuevo cliente' }}</h2>
<div mat-dialog-content class="form">
  <mat-form-field appearance="outline">
    <mat-label>Nombre</mat-label>
    <input matInput [(ngModel)]="nombre" required />
  </mat-form-field>

  <mat-form-field appearance="outline">
    <mat-label>RUT</mat-label>
    <input matInput [(ngModel)]="rut" />
  </mat-form-field>

  <mat-form-field appearance="outline">
    <mat-label>Email</mat-label>
    <input matInput type="email" [(ngModel)]="email" />
  </mat-form-field>

  <mat-form-field appearance="outline">
    <mat-label>Teléfono</mat-label>
    <input matInput [(ngModel)]="telefono" />
  </mat-form-field>

  <mat-form-field appearance="outline">
    <mat-label>Dirección</mat-label>
    <input matInput [(ngModel)]="direccion" />
  </mat-form-field>
</div>
<div mat-dialog-actions align="end">
  <button mat-button (click)="cancelar()">Cancelar</button>
  <button mat-flat-button color="primary" (click)="guardar()">Guardar</button>
</div>
```

- [ ] **Step 9: Run test to verify it passes**

Run: `cd frontend && npx ng test --watch=false --include='**/cliente-form-dialog.component.spec.ts'`
Expected: 3 specs pass.

- [ ] **Step 10: Create `frontend/src/app/features/clientes/clientes.component.ts`**

```typescript
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';
import { ClienteService, ClienteRequest } from '../../core/services/cliente.service';
import { AuthService } from '../../core/services/auth.service';
import { Cliente } from '../../core/models/models';
import { ClienteFormDialogComponent } from './cliente-form-dialog.component';

@Component({
  selector: 'app-clientes',
  standalone: true,
  imports: [CommonModule, MatTableModule, MatButtonModule, MatIconModule],
  templateUrl: './clientes.component.html',
  styleUrl: './clientes.component.scss',
})
export class ClientesComponent implements OnInit {
  columnas = ['nombre', 'rut', 'email', 'telefono', 'acciones'];
  clientes: Cliente[] = [];

  constructor(private clienteService: ClienteService, public auth: AuthService, private dialog: MatDialog) {}

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.clienteService.listar().subscribe((clientes) => (this.clientes = clientes));
  }

  abrirCrear(): void {
    const ref = this.dialog.open(ClienteFormDialogComponent, { width: '420px' });
    ref.afterClosed().subscribe((request: ClienteRequest | undefined) => {
      if (request) {
        this.clienteService.crear(request).subscribe(() => this.cargar());
      }
    });
  }

  abrirEditar(cliente: Cliente): void {
    const ref = this.dialog.open(ClienteFormDialogComponent, { width: '420px', data: cliente });
    ref.afterClosed().subscribe((request: ClienteRequest | undefined) => {
      if (request) {
        this.clienteService.actualizar(cliente.id, request).subscribe(() => this.cargar());
      }
    });
  }

  eliminar(cliente: Cliente): void {
    this.clienteService.eliminar(cliente.id).subscribe(() => this.cargar());
  }
}
```

- [ ] **Step 11: Create `frontend/src/app/features/clientes/clientes.component.html`**

```html
<div class="page-header">
  <h1>Clientes</h1>
  @if (auth.tienePermiso('CLIENTES_EDITAR')) {
    <button mat-flat-button color="primary" (click)="abrirCrear()">Nuevo cliente</button>
  }
</div>

<table mat-table [dataSource]="clientes" class="mat-elevation-z1">
  <ng-container matColumnDef="nombre">
    <th mat-header-cell *matHeaderCellDef>Nombre</th>
    <td mat-cell *matCellDef="let c">{{ c.nombre }}</td>
  </ng-container>

  <ng-container matColumnDef="rut">
    <th mat-header-cell *matHeaderCellDef>RUT</th>
    <td mat-cell *matCellDef="let c">{{ c.rut }}</td>
  </ng-container>

  <ng-container matColumnDef="email">
    <th mat-header-cell *matHeaderCellDef>Email</th>
    <td mat-cell *matCellDef="let c">{{ c.email }}</td>
  </ng-container>

  <ng-container matColumnDef="telefono">
    <th mat-header-cell *matHeaderCellDef>Teléfono</th>
    <td mat-cell *matCellDef="let c">{{ c.telefono }}</td>
  </ng-container>

  <ng-container matColumnDef="acciones">
    <th mat-header-cell *matHeaderCellDef></th>
    <td mat-cell *matCellDef="let c">
      @if (auth.tienePermiso('CLIENTES_EDITAR')) {
        <button mat-icon-button (click)="abrirEditar(c)"><mat-icon>edit</mat-icon></button>
        <button mat-icon-button (click)="eliminar(c)"><mat-icon>delete</mat-icon></button>
      }
    </td>
  </ng-container>

  <tr mat-header-row *matHeaderRowDef="columnas"></tr>
  <tr mat-row *matRowDef="let row; columns: columnas"></tr>
</table>
```

- [ ] **Step 12: Create `frontend/src/app/features/clientes/clientes.component.scss`**

```scss
table {
  width: 100%;
}
```

- [ ] **Step 13: Compile check**

Run: `cd frontend && npx ng build`
Expected: still fails on the missing `productos` component from Task 24 — but the error list should no longer mention `clientes`.

---

## Task 24: Productos feature (list + dialog)

**Files:**
- Create: `frontend/src/app/core/services/producto.service.ts`
- Create: `frontend/src/app/features/productos/productos.component.ts`
- Create: `frontend/src/app/features/productos/productos.component.html`
- Create: `frontend/src/app/features/productos/productos.component.scss`
- Create: `frontend/src/app/features/productos/producto-form-dialog.component.ts`
- Create: `frontend/src/app/features/productos/producto-form-dialog.component.html`
- Test: `frontend/src/app/core/services/producto.service.spec.ts`
- Test: `frontend/src/app/features/productos/producto-form-dialog.component.spec.ts`

**Interfaces:**
- Consumes: `Producto` (Task 14), `AuthService.tienePermiso` (Task 15). Follows the same list+dialog pattern as Tasks 22–23, gated by `PRODUCTOS_VER`/`PRODUCTOS_EDITAR`.
- Produces: `ProductoService`, `ProductosComponent`, `ProductoFormDialogComponent` — the `loadComponent` target for the `/productos` route from Task 18. This is the last piece `app.routes.ts` (Task 18) needs — `ng build` should fully succeed after this task.

- [ ] **Step 1: Write the failing test for `ProductoService`**

```typescript
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ProductoService } from './producto.service';
import { environment } from '../../../environments/environment';

describe('ProductoService', () => {
  let service: ProductoService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ProductoService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('listar hace GET a /productos', () => {
    service.listar().subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/productos`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('crear hace POST con el body recibido', () => {
    const request = { sku: 'SKU-1', nombre: 'Producto Uno', precioVenta: 1000, controlaStock: true };
    service.crear(request).subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/productos`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({});
  });

  it('eliminar hace DELETE al id correspondiente', () => {
    service.eliminar(9).subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/productos/9`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx ng test --watch=false --include='**/producto.service.spec.ts'`
Expected: fails to compile — `ProductoService` doesn't exist yet.

- [ ] **Step 3: Create `frontend/src/app/core/services/producto.service.ts`**

```typescript
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Producto } from '../models/models';

export interface ProductoRequest {
  sku?: string;
  nombre: string;
  descripcion?: string;
  precioVenta: number;
  precioCompra?: number;
  stock?: number;
  controlaStock: boolean;
}

@Injectable({ providedIn: 'root' })
export class ProductoService {
  private readonly base = `${environment.apiUrl}/productos`;

  constructor(private http: HttpClient) {}

  listar(): Observable<Producto[]> {
    return this.http.get<Producto[]>(this.base);
  }

  crear(request: ProductoRequest): Observable<Producto> {
    return this.http.post<Producto>(this.base, request);
  }

  actualizar(id: number, request: ProductoRequest): Observable<Producto> {
    return this.http.put<Producto>(`${this.base}/${id}`, request);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx ng test --watch=false --include='**/producto.service.spec.ts'`
Expected: 3 specs pass.

- [ ] **Step 5: Write the failing test for `ProductoFormDialogComponent`**

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { ProductoFormDialogComponent } from './producto-form-dialog.component';

describe('ProductoFormDialogComponent', () => {
  let fixture: ComponentFixture<ProductoFormDialogComponent>;
  let component: ProductoFormDialogComponent;
  let dialogRefSpy: jasmine.SpyObj<MatDialogRef<ProductoFormDialogComponent>>;

  async function crear(data: any) {
    dialogRefSpy = jasmine.createSpyObj('MatDialogRef', ['close']);
    await TestBed.configureTestingModule({
      imports: [ProductoFormDialogComponent],
      providers: [
        { provide: MatDialogRef, useValue: dialogRefSpy },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ProductoFormDialogComponent);
    component = fixture.componentInstance;
  }

  it('en modo creación arranca con controlaStock en true', async () => {
    await crear(null);
    expect(component.esEdicion).toBeFalse();
    expect(component.controlaStock).toBeTrue();
  });

  it('en modo edición precarga los datos del producto', async () => {
    await crear({
      id: 8, sku: 'SKU-1', nombre: 'Producto Uno', descripcion: 'desc',
      precioVenta: 1000, precioCompra: 500, stock: 10, controlaStock: true, activo: true,
    });
    expect(component.esEdicion).toBeTrue();
    expect(component.nombre).toBe('Producto Uno');
    expect(component.precioVenta).toBe(1000);
  });

  it('guardar cierra el dialog con el request armado', async () => {
    await crear(null);
    component.sku = 'SKU-2';
    component.nombre = 'Producto Dos';
    component.descripcion = 'otra desc';
    component.precioVenta = 2000;
    component.precioCompra = 1000;
    component.stock = 5;
    component.controlaStock = false;

    component.guardar();

    expect(dialogRefSpy.close).toHaveBeenCalledWith({
      sku: 'SKU-2',
      nombre: 'Producto Dos',
      descripcion: 'otra desc',
      precioVenta: 2000,
      precioCompra: 1000,
      stock: 5,
      controlaStock: false,
    });
  });
});
```

- [ ] **Step 6: Run test to verify it fails**

Run: `cd frontend && npx ng test --watch=false --include='**/producto-form-dialog.component.spec.ts'`
Expected: fails to compile — `ProductoFormDialogComponent` doesn't exist yet.

- [ ] **Step 7: Create `frontend/src/app/features/productos/producto-form-dialog.component.ts`**

```typescript
import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatButtonModule } from '@angular/material/button';
import { Producto } from '../../core/models/models';
import { ProductoRequest } from '../../core/services/producto.service';

@Component({
  selector: 'app-producto-form-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatCheckboxModule, MatButtonModule],
  templateUrl: './producto-form-dialog.component.html',
})
export class ProductoFormDialogComponent {
  esEdicion = !!this.data;

  sku = this.data?.sku ?? '';
  nombre = this.data?.nombre ?? '';
  descripcion = this.data?.descripcion ?? '';
  precioVenta = this.data?.precioVenta ?? 0;
  precioCompra = this.data?.precioCompra ?? 0;
  stock = this.data?.stock ?? 0;
  controlaStock = this.data?.controlaStock ?? true;

  constructor(
    private ref: MatDialogRef<ProductoFormDialogComponent, ProductoRequest>,
    @Inject(MAT_DIALOG_DATA) public data: Producto | null
  ) {}

  guardar(): void {
    const request: ProductoRequest = {
      sku: this.sku,
      nombre: this.nombre,
      descripcion: this.descripcion,
      precioVenta: this.precioVenta,
      precioCompra: this.precioCompra,
      stock: this.stock,
      controlaStock: this.controlaStock,
    };
    this.ref.close(request);
  }

  cancelar(): void {
    this.ref.close();
  }
}
```

- [ ] **Step 8: Create `frontend/src/app/features/productos/producto-form-dialog.component.html`**

```html
<h2 mat-dialog-title>{{ esEdicion ? 'Editar producto' : 'Nuevo producto' }}</h2>
<div mat-dialog-content class="form">
  <mat-form-field appearance="outline">
    <mat-label>SKU</mat-label>
    <input matInput [(ngModel)]="sku" />
  </mat-form-field>

  <mat-form-field appearance="outline">
    <mat-label>Nombre</mat-label>
    <input matInput [(ngModel)]="nombre" required />
  </mat-form-field>

  <mat-form-field appearance="outline">
    <mat-label>Descripción</mat-label>
    <input matInput [(ngModel)]="descripcion" />
  </mat-form-field>

  <mat-form-field appearance="outline">
    <mat-label>Precio de venta</mat-label>
    <input matInput type="number" [(ngModel)]="precioVenta" required />
  </mat-form-field>

  <mat-form-field appearance="outline">
    <mat-label>Precio de compra</mat-label>
    <input matInput type="number" [(ngModel)]="precioCompra" />
  </mat-form-field>

  <mat-form-field appearance="outline">
    <mat-label>Stock</mat-label>
    <input matInput type="number" [(ngModel)]="stock" />
  </mat-form-field>

  <mat-checkbox [(ngModel)]="controlaStock">Controla stock</mat-checkbox>
</div>
<div mat-dialog-actions align="end">
  <button mat-button (click)="cancelar()">Cancelar</button>
  <button mat-flat-button color="primary" (click)="guardar()">Guardar</button>
</div>
```

- [ ] **Step 9: Run test to verify it passes**

Run: `cd frontend && npx ng test --watch=false --include='**/producto-form-dialog.component.spec.ts'`
Expected: 3 specs pass.

- [ ] **Step 10: Create `frontend/src/app/features/productos/productos.component.ts`**

```typescript
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';
import { ProductoService, ProductoRequest } from '../../core/services/producto.service';
import { AuthService } from '../../core/services/auth.service';
import { Producto } from '../../core/models/models';
import { ProductoFormDialogComponent } from './producto-form-dialog.component';

@Component({
  selector: 'app-productos',
  standalone: true,
  imports: [CommonModule, MatTableModule, MatButtonModule, MatIconModule],
  templateUrl: './productos.component.html',
  styleUrl: './productos.component.scss',
})
export class ProductosComponent implements OnInit {
  columnas = ['sku', 'nombre', 'precioVenta', 'stock', 'acciones'];
  productos: Producto[] = [];

  constructor(private productoService: ProductoService, public auth: AuthService, private dialog: MatDialog) {}

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.productoService.listar().subscribe((productos) => (this.productos = productos));
  }

  abrirCrear(): void {
    const ref = this.dialog.open(ProductoFormDialogComponent, { width: '420px' });
    ref.afterClosed().subscribe((request: ProductoRequest | undefined) => {
      if (request) {
        this.productoService.crear(request).subscribe(() => this.cargar());
      }
    });
  }

  abrirEditar(producto: Producto): void {
    const ref = this.dialog.open(ProductoFormDialogComponent, { width: '420px', data: producto });
    ref.afterClosed().subscribe((request: ProductoRequest | undefined) => {
      if (request) {
        this.productoService.actualizar(producto.id, request).subscribe(() => this.cargar());
      }
    });
  }

  eliminar(producto: Producto): void {
    this.productoService.eliminar(producto.id).subscribe(() => this.cargar());
  }
}
```

- [ ] **Step 11: Create `frontend/src/app/features/productos/productos.component.html`**

```html
<div class="page-header">
  <h1>Productos</h1>
  @if (auth.tienePermiso('PRODUCTOS_EDITAR')) {
    <button mat-flat-button color="primary" (click)="abrirCrear()">Nuevo producto</button>
  }
</div>

<table mat-table [dataSource]="productos" class="mat-elevation-z1">
  <ng-container matColumnDef="sku">
    <th mat-header-cell *matHeaderCellDef>SKU</th>
    <td mat-cell *matCellDef="let p">{{ p.sku }}</td>
  </ng-container>

  <ng-container matColumnDef="nombre">
    <th mat-header-cell *matHeaderCellDef>Nombre</th>
    <td mat-cell *matCellDef="let p">{{ p.nombre }}</td>
  </ng-container>

  <ng-container matColumnDef="precioVenta">
    <th mat-header-cell *matHeaderCellDef>Precio venta</th>
    <td mat-cell *matCellDef="let p">{{ p.precioVenta }}</td>
  </ng-container>

  <ng-container matColumnDef="stock">
    <th mat-header-cell *matHeaderCellDef>Stock</th>
    <td mat-cell *matCellDef="let p">{{ p.controlaStock ? p.stock : '—' }}</td>
  </ng-container>

  <ng-container matColumnDef="acciones">
    <th mat-header-cell *matHeaderCellDef></th>
    <td mat-cell *matCellDef="let p">
      @if (auth.tienePermiso('PRODUCTOS_EDITAR')) {
        <button mat-icon-button (click)="abrirEditar(p)"><mat-icon>edit</mat-icon></button>
        <button mat-icon-button (click)="eliminar(p)"><mat-icon>delete</mat-icon></button>
      }
    </td>
  </ng-container>

  <tr mat-header-row *matHeaderRowDef="columnas"></tr>
  <tr mat-row *matRowDef="let row; columns: columnas"></tr>
</table>
```

- [ ] **Step 12: Create `frontend/src/app/features/productos/productos.component.scss`**

```scss
table {
  width: 100%;
}
```

- [ ] **Step 13: Full frontend build + test run**

Run: `cd frontend && npx ng build && npx ng test --watch=false`
Expected: `BUILD SUCCESS`; every spec file created in Tasks 15–24 passes.

---

## Task 25: Full-stack Docker Compose + end-to-end smoke test

**Files:**
- Create: `frontend/nginx.conf`
- Create: `frontend/Dockerfile`
- Modify: `docker-compose.yml` (add the `frontend` service to the file created in Task 11)

**Interfaces:**
- Consumes: everything from Tasks 1–24.
- Produces: a fully running three-container stack (`db`, `backend`, `frontend`), manually verified in a browser — the final deliverable of this plan.

- [ ] **Step 1: Create `frontend/nginx.conf` (the `/uploads` proxy block from the old project is dropped — no file uploads in this scope)**

```
server {
    listen 80;
    server_name _;
    root /usr/share/nginx/html;
    index index.html;

    location /api/ {
        proxy_pass http://backend:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

- [ ] **Step 2: Create `frontend/Dockerfile`**

```dockerfile
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npx ng build --configuration production

FROM nginx:alpine
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

- [ ] **Step 3: Modify `docker-compose.yml` — append the `frontend` service (after the `backend` service block, before `volumes:`)**

```yaml
  frontend:
    build: ./frontend
    restart: unless-stopped
    depends_on:
      - backend
    ports:
      - "80:80"
    develop:
      watch:
        - action: rebuild
          path: ./frontend/src
        - action: rebuild
          path: ./frontend/package.json
        - action: rebuild
          path: ./frontend/angular.json
```

- [ ] **Step 4: Boot the full stack**

Run: `docker compose up --build -d`
Expected: all three containers (`db`, `backend`, `frontend`) reach a healthy/running state.

- [ ] **Step 5: Manual end-to-end verification in a browser**

Open `http://localhost`. Expected:
1. The login page renders with Material-styled inputs/button in the brand color, "Slime ERP" as the title.
2. Logging in with `admin@demo.cl` / `admin123` redirects to `/dashboard`, showing "Bienvenido, Administrador Demo" and 3 KPI cards.
3. The sidebar shows Dashboard, Clientes, Productos, and Usuarios (the seeded admin has all catalog + user permissions).
4. Opening "Clientes" and "Productos" shows empty tables with a working "Nuevo" button that opens a Material dialog; creating an entry shows it in the table afterward.
5. Opening "Usuarios" shows the seeded admin; creating a new user with rol `VISUALIZADOR`, logging out, and logging back in as that new user shows a sidebar **without** the "Usuarios" item (no `USUARIOS_VER`) — this is the concrete proof that the permission system works end-to-end across backend and frontend.

- [ ] **Step 6: Tear down**

Run: `docker compose down`

- [ ] **Step 7: Commit reminder**

Do not commit. Tell the user the full plan (Tasks 1–25) is ready for review.
