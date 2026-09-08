# Saavia Admin ERP — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Construir el panel administrativo SaaS de Saavia (Backoffice de plataforma) según `docs/Especificación técnica — Saavia Admin ERP - Panel administrativo SaaS.md`: empresas/tenants, usuarios, planes, suscripciones, pagos, vencimientos, SII, DTE (monitoreo), alertas, soporte, auditoría, configuración global y salud de la plataforma, con RBAC de 5 roles administrativos (SUPER_ADMIN, ADMIN, SUPPORT, FINANCE, AUDITOR).

**Architecture:** El sistema se construye **sobre el `admin-backend` existente** (Spring Boot 3.3.4 / Java 21, puerto 8081) y la feature Angular `plataforma` del frontend; no se crea un backend nuevo. Se agrega un dominio administrativo propio en el esquema de PostgreSQL `admin` (tablas nuevas `admin.*`), manteniendo `tenant`/`usuario`/`cobranza` en `public` como fuente de verdad de empresas y datos compartidos con el ERP. El login administrativo pasa a ser propio (`POST /api/admin/auth/login` contra `admin.usuario`, JWT con claims `adminId`/`adminRol`/`adminEmail`), con RBAC vía autoridades generadas desde un mapeo fijo `AdminRol → Permisos` (mismo patrón que `RolPermisos` del backend de negocio). El frontend migra de `/plataforma` a `/admin` con el menú lateral completo del spec (§30).

**Tech Stack:** Spring Boot 3.3.4 / Java 21, Spring Data JPA, Spring Security + JWT (jjwt), PostgreSQL 16 (Flyway del backend ERP como único dueño del DDL), Angular 18 standalone, Angular Material, Highcharts (ya incluido).

**Spec:** `docs/Especificación técnica — Saavia Admin ERP - Panel administrativo SaaS.md`

## Estado actual (lo que ya existe y se reutiliza)

| Pieza | Estado | Se hace con ello |
|---|---|---|
| `admin-backend` (8081) | Spring Boot con JWT compartido, `SecurityConfig`, `JwtAuthFilter`, `JwtService`, `GlobalExceptionHandler`, `UsuarioActual` | Se reutiliza la infraestructura; el auth pasa a ser propio (Task 3) |
| `EmpresaAdminController/Service/Response` (`/api/admin/empresas`) | Lista, stats, crear, activar/desactivar tenant (filtra `PLAN_PLATAFORMA`) | Se evoluciona: estados (§7), paginación, filtros, detalle (Tasks 6–7) |
| `UsuarioAdminController/Service` (`/api/admin/usuarios`) | CRUD de usuarios de empresa + reset password | Se evoluciona: bloqueo, revocar sesiones, listado por empresa (Task 8) |
| `cobranza` (`cobranza_empresa`, `cobranza_pago`) | Cargos y pagos por plan | Base del módulo de Pagos (Task 13); no se duplica |
| `tenant`/`usuario` en `public` | Tablas compartidas con el ERP (V1…) | Fuente de verdad de empresas y sus usuarios |
| Migraciones Flyway en el **backend ERP** (`V1…V23`, incluye `V22` cobranza y `V23` seed SUPER_ADMIN) | Único dueño del DDL | Todo el DDL nuevo del dominio admin va en el backend ERP como `V24+` |
| Frontend `features/plataforma` + rutas `/plataforma` + `usuario-plataforma.service.ts` (usa `adminApiUrl`) | Empresas, usuarios, cobranza, guard `esSuperAdmin` | Se renombra a `features/admin` con rutas `/admin` y menú completo (Task 5) |
| `environment.adminApiUrl` | Ya apunta a `http://localhost:8081/api` (dev) `/admin-api` (prod) | Se mantiene |

## Decisiones de arquitectura

1. **Backend único admin:** se amplía `admin-backend`; no se crean tecnologías paralelas.
2. **Datos admin en esquema `admin`:** tablas nuevas `admin.usuario`, `admin.sesion`, `admin.plan`, `admin.suscripcion`, `admin.pago`, `admin.alerta`, `admin.audit_log`, `admin.ticket_soporte`, `admin.configuracion` (§24). Las FKs a empresas usan `company_id` que referencia `public.tenant(id)`.
3. **Empresas = `tenant`:** no se crea una tabla `company` duplicada. Se agregan columnas admin a `tenant` (`status`, `business_name`, `last_access_at`) y `activo` se mantiene sincronizado con `status`, evitando romper el ERP.
4. **Login y RBAC administrativos propios:** `POST /api/admin/auth/login` contra `admin.usuario`; JWT admin con claims `adminId`, `adminEmail`, `adminRol`. El `JwtAuthFilter` del admin-backend autentica con `adminRol` (fallback al claim `rol` legacy mientras dure la transición). Autorización por autoridades de `AdminPermisos.permisosDe(AdminRol)`, endpoints con `@PreAuthorize("hasAuthority('...')")` (§4, §20, §24).
5. **El DDL nuevo vive en el Flyway del backend ERP (`V24+`):** un solo dueño del esquema; `admin-backend` sigue con `ddl-auto: none`.
6. **DTE/SII = monitoreo read-only** (§15, §16): no se emite desde Admin; se consulta estado/documentos del ERP mediante servicios controlados y auditados.
7. **Frontend:** `/plataforma` → `/admin` (§30), layout nuevo con menú completo, guard por roles admin y `AdminAuthService` separado del login de negocio.
8. **Pagos:** se consolida con la cobranza existente (no se duplica); el Admin agrega registro manual, estados y referencia.

## Global Constraints

- No modificar la funcionalidad del ERP de los clientes. Los cambios a `tenant`/`usuario` son aditivos y no alteran contratos que usa el backend de negocio.
- El `id` de toda tabla nueva es `BIGINT GENERATED BY DEFAULT AS IDENTITY`, generado por la BD; el frontend nunca envía `id` (regla CLAUDE.md).
- No eliminar físicamente registros; usar estados y auditoría. No eliminar planes con empresas asociadas.
- Toda acción administrativa crítica genera un registro en `admin.audit_log` (§19, §43).
- Listas paginadas con el contrato del spec (§35): `?page=&limit=` → `{ data, pagination: { page, limit, total, totalPages } }`.
- Búsqueda y filtros combinables ejecutados en backend (§36), nunca descargando miles de filas al frontend.
- El backend valida permisos SIEMPRE; protección contra IDOR y acceso cross-tenant con `companyId` manipulado (§20, §40).
- No mostrar ni almacenar secretos/certificados SII en texto plano (§15).
- Backend: unit tests con JUnit 5 + Mockito sin contexto Spring (estilo `admin-backend/src/test`).
- Frontend: los componentes de pantalla no llevan `.spec.ts` (convención del repo); se verifican con `ng build` más prueba manual. Los specs existentes se conservan.

## Cómo correr los tests (verificado en esta máquina)

No hay `mvn` ni JDK en el host — los tests de backend corren en un contenedor Maven con volumen para cachear dependencias. Node sí está en el host.

```bash
# Un test específico en admin-backend
cd admin-backend
MSYS_NO_PATHCONV=1 docker run --rm \
  -v "//c/Users/ivana/Documents/slime-erp/admin-backend://app" \
  -v slime-erp-maven-repo:/root/.m2 \
  -w //app maven:3.9-eclipse-temurin-21 \
  mvn -q -B -Dtest=NombreDeLaClaseTest test

# Toda la suite de admin-backend
MSYS_NO_PATHCONV=1 docker run --rm \
  -v "//c/Users/ivana/Documents/slime-erp/admin-backend://app" \
  -v slime-erp-maven-repo:/root/.m2 \
  -w //app maven:3.9-eclipse-temurin-21 \
  mvn -q -B test
```

Verificación del DDL contra Postgres real (Flyway lo aplica el backend ERP):

```bash
docker compose up -d db backend
docker compose exec db psql -U slime_erp -d slim_erp -c "\dt admin.*"
```

Compilación del frontend (type-check de componentes nuevos):

```bash
cd frontend && npx ng build --configuration development
```

## File Structure

Migraciones (en **backend ERP**, `backend/src/main/resources/db/migration/`):
- `V24__admin_schema.sql`, `V25__admin_catalogo.sql`, `V26__admin_operacion.sql`, `V27__admin_auditoria_soporte.sql` (Task 1).

Backend `admin-backend` (paquete `cl.slimerp.admin`):
- `rbac/AdminRol.java`, `rbac/AdminPermisos.java` (Task 2).
- `usuario/AdminUsuario.java`, `AdminUsuarioRepository.java`, `AdminSesion.java`, `AdminSesionRepository.java` (Task 2).
- `auth/AdminAuthController.java`, `AdminAuthService.java`, `AdminLoginRequest.java`, `AdminAuthResponse.java` (Task 3).
- `config/JwtService.java` (se amplía para firmar tokens admin), `config/JwtAuthFilter.java` (usa `adminRol` + `AdminPermisos`) (Task 3).
- `dashboard/DashboardController.java`, `DashboardService.java`, `DashboardResponse.java` (Task 4).
- `planes/Plan.java`, `PlanRepository.java`, `PlanController.java`, `PlanService.java`, `PlanRequest.java`, `PlanResponse.java` (Task 9).
- `suscripciones/Suscripcion.java`, `SuscripcionRepository.java`, `SuscripcionController.java`, `SuscripcionService.java`, `SuscripcionRequest.java`, `ExtenderSuscripcionRequest.java` (Tasks 10, 14).
- `pagos/Pago.java`, `PagoRepository.java`, `PagoController.java`, `PagoService.java`, `RegistrarPagoRequest.java` (Task 13).
- `alertas/Alerta.java`, `AlertaRepository.java`, `AlertaController.java`, `AlertaService.java` (Task 12).
- `auditoria/AuditLog.java`, `AuditLogRepository.java`, `AuditService.java`, `AuditController.java` (Task 11).
- `soporte/TicketSoporte.java`, `TicketSoporteRepository.java`, `SoporteController.java`, `SoporteService.java` (Task 17).
- `configuracion/Configuracion.java`, `ConfiguracionRepository.java`, `ConfiguracionController.java` (Task 18).
- `dte/DteController.java`, `DteService.java` (Task 15); `sii/SiiController.java`, `SiiService.java` (Task 16).
- `sistema/HealthController.java`, `SistemaService.java` (Task 20); `notificaciones/EventoPlataforma.java`, `NotificationService.java` (Task 22).
- `empresas/**`, `usuarios/**`, `cobranza/**`: se evolucionan (Tasks 6–8, 13).

Frontend (`frontend/src/app/`):
- `features/plataforma/**` → `features/admin/**` (Task 5).
- `core/services/admin-auth.service.ts`, `core/guards/admin.guard.ts`, helpers `Paginated<T>` en `core/models/models.ts` (Task 5).
- Pantallas nuevas en `features/admin/`: `dashboard/`, `empresas-detalle/`, `planes/`, `suscripciones/`, `pagos/`, `vencimientos/`, `dte/`, `sii/`, `alertas/`, `soporte/`, `auditoria/`, `configuracion/`, `perfil/`, `seguridad/`, `sistema/`.
- `app.routes.ts`: rutas `/admin/...` (Task 5).

---

## FASE 0 — Fundación (P0: infraestructura, auth y dashboard)

### Task 1: Migraciones del dominio admin (`V24`–`V27`)

**Files:**
- Create: `backend/src/main/resources/db/migration/V24__admin_schema.sql`
- Create: `backend/src/main/resources/db/migration/V25__admin_catalogo.sql`
- Create: `backend/src/main/resources/db/migration/V26__admin_operacion.sql`
- Create: `backend/src/main/resources/db/migration/V27__admin_auditoria_soporte.sql`

**Interfaces:**
- Consumes: `tenant(id)`, `usuario(id)` ya existentes en `public`.
- Produces: esquema `admin` completo; columnas `status`, `business_name`, `last_access_at` en `tenant`; seed del SUPER_ADMIN admin y de los planes base — consumido por todas las Tasks siguientes, de modo que FASE 0+ depende de esta Task.

- [x] **Step 1: Crear `V24__admin_schema.sql`** (esquema, usuarios admin, sesiones, columnas de empresa)

```sql
CREATE SCHEMA IF NOT EXISTS admin;

CREATE TABLE admin.usuario (
    id             BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    nombre         VARCHAR(150) NOT NULL,
    email          VARCHAR(150) NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    rol            VARCHAR(30)  NOT NULL,   -- SUPER_ADMIN ADMIN SUPPORT FINANCE AUDITOR
    activo         BOOLEAN      NOT NULL DEFAULT TRUE,
    ultimo_acceso  TIMESTAMP,
    fecha_creacion TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE admin.sesion (
    id               BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    admin_usuario_id BIGINT       NOT NULL REFERENCES admin.usuario(id),
    token_hash       VARCHAR(255) NOT NULL,
    ip               VARCHAR(45),
    user_agent       VARCHAR(500),
    creada_en        TIMESTAMP    NOT NULL DEFAULT now(),
    expira_en        TIMESTAMP    NOT NULL,
    revocada_en      TIMESTAMP
);
CREATE INDEX idx_admin_sesion_usuario ON admin.sesion(admin_usuario_id);

-- Columnas administrativas sobre el tenant. El ERP solo lee activo/plan/fecha_alta.
ALTER TABLE tenant ADD COLUMN status         VARCHAR(20)  NOT NULL DEFAULT 'TRIAL';
ALTER TABLE tenant ADD COLUMN business_name  VARCHAR(150);
ALTER TABLE tenant ADD COLUMN last_access_at TIMESTAMP;
CREATE INDEX idx_tenant_status ON tenant(status);

-- Seed: SUPER_ADMIN administrativo (mismas credenciales que V23: password Super123!)
INSERT INTO admin.usuario (nombre, email, password_hash, rol, activo)
VALUES ('Super Administrador', 'super@slimerp.cl',
        '$2b$10$qoxzrj3gH5e05XaFkk5ntuCzsk8tkj82nQEkmZkjs900M0PJ6rFZm',
        'SUPER_ADMIN', TRUE);
```

> Nota: el tenant de plataforma (`plan='plataforma'`, id 2) queda fuera de la gestión de empresas (igual que hoy con `PLAN_PLATAFORMA`).

- [x] **Step 2: Crear `V25__admin_catalogo.sql`** (planes y suscripciones)

```sql
CREATE TABLE admin.plan (
    id              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    nombre          VARCHAR(100)  NOT NULL,
    descripcion     VARCHAR(500),
    precio_mensual  NUMERIC(14,2) NOT NULL,
    precio_anual    NUMERIC(14,2),
    max_usuarios    INTEGER       NOT NULL,
    max_documentos  INTEGER       NOT NULL,
    modulos         JSONB         NOT NULL,
    caracteristicas JSONB,
    estado          VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE'  -- ACTIVE | INACTIVE
);

CREATE TABLE admin.suscripcion (
    id                  BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    company_id          BIGINT       NOT NULL REFERENCES tenant(id),
    plan_id             BIGINT       NOT NULL REFERENCES admin.plan(id),
    estado              VARCHAR(30)  NOT NULL, -- TRIAL ACTIVE PAST_DUE SUSPENDED CANCELLED EXPIRED
    fecha_inicio        DATE         NOT NULL,
    fecha_vencimiento   DATE         NOT NULL,
    ciclo_facturacion   VARCHAR(20)  NOT NULL, -- MONTHLY | ANNUAL
    precio              NUMERIC(14,2) NOT NULL,
    periodo_gracia_dias INTEGER      NOT NULL DEFAULT 0
);
CREATE INDEX idx_suscripcion_company ON admin.suscripcion(company_id);
CREATE INDEX idx_suscripcion_venc   ON admin.suscripcion(estado, fecha_vencimiento);

INSERT INTO admin.plan (nombre, descripcion, precio_mensual, precio_anual, max_usuarios, max_documentos, modulos, caracteristicas, estado)
VALUES
 ('Básico',      'Para operaciones pequeñas', 15990, 159900,  3,   500,  '["ventas","compras"]'::jsonb, NULL, 'ACTIVE'),
 ('Profesional', 'Para pymes en crecimiento', 29990, 299900,  10,  1000, '["ventas","compras","inventario","contabilidad","dte"]'::jsonb, NULL, 'ACTIVE'),
 ('Empresa',     'Para organizaciones grandes', 59990, 599900, 50,  5000, '["ventas","compras","inventario","contabilidad","dte"]'::jsonb, NULL, 'ACTIVE');
```

- [x] **Step 3: Crear `V26__admin_operacion.sql`** (pagos y alertas)

```sql
CREATE TABLE admin.pago (
    id                 BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    company_id         BIGINT        NOT NULL REFERENCES tenant(id),
    suscripcion_id     BIGINT        REFERENCES admin.suscripcion(id),
    monto              NUMERIC(14,2) NOT NULL,
    moneda             VARCHAR(10)   NOT NULL DEFAULT 'CLP',
    metodo             VARCHAR(30)   NOT NULL,
    estado             VARCHAR(20)   NOT NULL, -- PENDING PAID FAILED REFUNDED CANCELLED
    referencia_externa VARCHAR(150),
    pagado_en          TIMESTAMP,
    creado_por         BIGINT        REFERENCES admin.usuario(id),
    creado_en          TIMESTAMP     NOT NULL DEFAULT now()
);
CREATE INDEX idx_pago_company ON admin.pago(company_id);

CREATE TABLE admin.alerta (
    id          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    company_id  BIGINT        REFERENCES tenant(id),
    severity    VARCHAR(20)   NOT NULL, -- CRITICAL WARNING INFO
    tipo        VARCHAR(40)   NOT NULL,
    titulo      VARCHAR(200)  NOT NULL,
    descripcion VARCHAR(1000),
    status      VARCHAR(20)   NOT NULL DEFAULT 'OPEN', -- OPEN READ RESOLVED
    creada_en   TIMESTAMP     NOT NULL DEFAULT now(),
    leida_en    TIMESTAMP,
    resuelta_en TIMESTAMP
);
CREATE INDEX idx_alerta_estado ON admin.alerta(status, severity);
```

- [x] **Step 4: Crear `V27__admin_auditoria_soporte.sql`** (auditoría, tickets y configuración)

```sql
CREATE TABLE admin.audit_log (
    id               BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    admin_usuario_id BIGINT REFERENCES admin.usuario(id),
    company_id       BIGINT REFERENCES tenant(id),
    action           VARCHAR(60) NOT NULL,
    modulo           VARCHAR(60) NOT NULL,
    entity_type      VARCHAR(60),
    entity_id        BIGINT,
    old_value        JSONB,
    new_value        JSONB,
    ip_address       VARCHAR(45),
    user_agent       VARCHAR(500),
    creado_en        TIMESTAMP   NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_fecha ON admin.audit_log(creado_en DESC);
CREATE INDEX idx_audit_company ON admin.audit_log(company_id);

CREATE TABLE admin.ticket_soporte (
    id              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    company_id      BIGINT REFERENCES tenant(id),
    user_id         BIGINT REFERENCES usuario(id),
    subject         VARCHAR(200)  NOT NULL,
    descripcion     VARCHAR(2000),
    categoria       VARCHAR(60),
    prioridad       VARCHAR(20)   NOT NULL DEFAULT 'MEDIUM', -- LOW MEDIUM HIGH CRITICAL
    status          VARCHAR(20)   NOT NULL DEFAULT 'OPEN',   -- OPEN IN_PROGRESS WAITING RESOLVED CLOSED
    assigned_to     BIGINT REFERENCES admin.usuario(id),
    creado_en       TIMESTAMP     NOT NULL DEFAULT now(),
    actualizado_en  TIMESTAMP,
    resuelto_en     TIMESTAMP
);

CREATE TABLE admin.configuracion (
    clave            VARCHAR(80) PRIMARY KEY,
    valor            JSONB        NOT NULL,
    descripcion      VARCHAR(300),
    actualizado_en   TIMESTAMP    NOT NULL DEFAULT now(),
    actualizado_por  BIGINT REFERENCES admin.usuario(id)
);

INSERT INTO admin.configuracion (clave, valor, descripcion) VALUES
 ('plataforma.nombre',           '{"nombre":"Saavia Admin"}'::jsonb, 'Nombre de la plataforma'),
 ('suscripcion.gracePeriodDays', '{"gracePeriodDays":7}'::jsonb,     'Período de gracia días'),
 ('suscripcion.trialDays',       '{"trialDays":14}'::jsonb,          'Días de prueba');
```

- [x] **Step 5: Verificar que la migración aplica**

```bash
docker compose up -d db backend
docker compose exec db psql -U slime_erp -d slim_erp -c "\dt admin.*"
```

Expected: las 8 tablas del esquema `admin` existen y `tenant` tiene `status`, `business_name`, `last_access_at`.

- [x] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/migration/V24__admin_schema.sql backend/src/main/resources/db/migration/V25__admin_catalogo.sql backend/src/main/resources/db/migration/V26__admin_operacion.sql backend/src/main/resources/db/migration/V27__admin_auditoria_soporte.sql
git commit -m "Agregar esquema admin: usuarios, sesiones, planes, suscripciones, pagos, alertas, auditoria, soporte, configuracion"
```

---

### Task 2: RBAC administrativo — `AdminRol`, `AdminPermisos`, entidades y repos

**Files:**
- Create: `admin-backend/src/main/java/cl/slimerp/admin/rbac/AdminRol.java`
- Create: `admin-backend/src/main/java/cl/slimerp/admin/rbac/AdminPermisos.java`
- Create: `admin-backend/src/main/java/cl/slimerp/admin/usuario/AdminUsuario.java`
- Create: `admin-backend/src/main/java/cl/slimerp/admin/usuario/AdminUsuarioRepository.java`
- Create: `admin-backend/src/main/java/cl/slimerp/admin/usuario/AdminSesion.java`
- Create: `admin-backend/src/main/java/cl/slimerp/admin/usuario/AdminSesionRepository.java`

**Interfaces:**
- Consumes: esquema `admin` (Task 1).
- Produces: `AdminRol` (SUPER_ADMIN, ADMIN, SUPPORT, FINANCE, AUDITOR); `AdminPermisos.permisosDe(AdminRol): Set<String>`; `AdminUsuario`/`AdminSesion` con repos — consumidos por Task 3 (filter/permisos) y Task 8/Gestión de admins.
- [x] **Step 1: Crear `AdminRol` y `AdminPermisos`**

```java
// rbac/AdminRol.java
package cl.slimerp.admin.rbac;

public enum AdminRol {
    SUPER_ADMIN, ADMIN, SUPPORT, FINANCE, AUDITOR
}
```

```java
// rbac/AdminPermisos.java
package cl.slimerp.admin.rbac;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/** Mapeo fijo rol admin → permisos (mismo patrón que RolPermisos del backend de negocio). */
public final class AdminPermisos {

    private static final Map<AdminRol, Set<String>> POR_ROL = new EnumMap<>(AdminRol.class);

    static {
        POR_ROL.put(AdminRol.SUPER_ADMIN, Set.of(
                "EMPRESAS_VER", "EMPRESAS_EDITAR", "USUARIOS_ADMIN_VER", "USUARIOS_ADMIN_EDITAR",
                "PLANES_VER", "PLANES_EDITAR", "SUSCRIPCIONES_VER", "SUSCRIPCIONES_EDITAR",
                "PAGOS_VER", "PAGOS_EDITAR", "ALERTAS_VER", "ALERTAS_EDITAR", "SOPORTE_VER",
                "SOPORTE_EDITAR", "AUDITORIA_VER", "CONFIG_GLOBAL_VER", "CONFIG_GLOBAL_EDITAR",
                "DTE_VER", "SII_VER", "SISTEMA_VER", "SESIONES_VER", "SESIONES_EDITAR"));
        POR_ROL.put(AdminRol.ADMIN, Set.of(
                "EMPRESAS_VER", "USUARIOS_ADMIN_VER", "USUARIOS_ADMIN_EDITAR", "SOPORTE_VER",
                "SOPORTE_EDITAR", "ALERTAS_VER", "AUDITORIA_VER"));
        POR_ROL.put(AdminRol.SUPPORT, Set.of(
                "EMPRESAS_VER", "USUARIOS_ADMIN_VER", "ALERTAS_VER", "SOPORTE_VER", "SOPORTE_EDITAR",
                "DTE_VER", "SII_VER"));
        POR_ROL.put(AdminRol.FINANCE, Set.of(
                "EMPRESAS_VER", "PLANES_VER", "PLANES_EDITAR", "SUSCRIPCIONES_VER",
                "SUSCRIPCIONES_EDITAR", "PAGOS_VER", "PAGOS_EDITAR"));
        POR_ROL.put(AdminRol.AUDITOR, Set.of(
                "EMPRESAS_VER", "USUARIOS_ADMIN_VER", "SUSCRIPCIONES_VER", "PAGOS_VER",
                "AUDITORIA_VER", "ALERTAS_VER", "DTE_VER", "SII_VER"));
    }

    private AdminPermisos() {}

    public static Set<String> permisosDe(AdminRol rol) {
        Set<String> permisos = POR_ROL.get(rol);
        if (permisos == null) {
            throw new IllegalArgumentException("Rol admin desconocido: " + rol);
        }
        return permisos;
    }
}
```

- [x] **Step 2: Crear entidades y repositorios** (`@Entity @Table(name="usuario", schema="admin")`, `@Table(name="sesion", schema="admin")`; `AdminUsuario`: `id`, `nombre`, `email`, `passwordHash`, `rol: AdminRol` (`@Enumerated(STRING)`), `activo`, `ultimoAcceso`, `fechaCreacion`. `AdminSesion`: `id`, `adminUsuarioId`, `tokenHash`, `ip`, `userAgent`, `creadaEn`, `expiraEn`, `revocadaEn`, con `@Builder` igual que el resto de entidades del repo).
- Repos: `AdminUsuarioRepository` con `findByEmail(String)`, `findByEmailAndActivoTrue(String)`; `AdminSesionRepository` con `findByAdminUsuarioIdAndRevocadaEnIsNull(Long)`, `findByTokenHash(String)`, `countByAdminUsuarioIdAndRevocadaEnIsNull(Long)`.

- [x] **Step 3: Escribir `AdminPermisosTest`** (verifica la matriz del spec §4: p.ej. SUPER_ADMIN contiene todo; SUPPORT NO tiene `PAGOS_EDITAR` ni `CONFIG_GLOBAL_EDITAR`; AUDITOR solo lectura → no contiene `EMPRESAS_EDITAR`). Patrón TDD: primero el test, luego la implementación ya escrita en Step 1.

- [x] **Step 4: Correr el test**

```bash
cd admin-backend
MSYS_NO_PATHCONV=1 docker run --rm -v "//c/Users/ivana/Documents/slime-erp/admin-backend://app" -v slime-erp-maven-repo:/root/.m2 -w //app maven:3.9-eclipse-temurin-21 mvn -q -B -Dtest=AdminPermisosTest test
```

- [x] **Step 5: Commit**

```bash
git add admin-backend/src/main/java/cl/slimerp/admin/rbac admin-backend/src/main/java/cl/slimerp/admin/usuario admin-backend/src/test/java/cl/slimerp/admin/rbac
git commit -m "Agregar RBAC admin: AdminRol, AdminPermisos y entidades usuario/sesion"
```

---

### Task 3: Login administrativo propio + JWT admin + filtro seguro

**Files:**
- Create: `admin-backend/src/main/java/cl/slimerp/admin/auth/AdminLoginRequest.java`, `AdminAuthResponse.java`, `AdminAuthController.java`, `AdminAuthService.java`
- Modify: `admin-backend/src/main/java/cl/slimerp/admin/config/JwtService.java`
- Modify: `admin-backend/src/main/java/cl/slimerp/admin/config/JwtAuthFilter.java`
- Modify: `admin-backend/src/main/java/cl/slimerp/admin/config/SecurityConfig.java`
- Test: `admin-backend/src/test/java/cl/slimerp/admin/auth/AdminAuthServiceTest.java`, `AdminAuthControllerTest.java`, `JwtAuthFilterTest.java`

**Interfaces:**
- Consumes: `AdminUsuarioRepository`, `AdminSesionRepository` (Task 2), `PasswordEncoder`, `JwtService`.
- Produces: `POST /api/admin/auth/login` → `AdminAuthResponse{ token, adminId, nombre, email, adminRol, permisos[] }`; `POST /api/admin/auth/logout` (revoca sesión). JWT admin con claims `adminId`, `adminEmail`, `adminRol`. El filtro genera autoridades `ROLE_<rol>` + permisos de `AdminPermisos` — consumido por todos los controladores admin.

- [x] **Step 1: Test que falla para `JwtAuthFilter`** (sustituir por versiones que validan el claim `adminRol`): un token admin válido poblado de autoridades `ROLE_SUPER_ADMIN` + todos los permisos; un token sin `adminRol` cae al fallback legacy (`rol`); sin header → no autentica.
- [x] **Step 2: Ampliar `JwtService`** con `generarTokenAdmin(Long adminId, String email, AdminRol rol)` usando el mismo secreto, con `expiration-minutes` de `app.jwt`.
- [x] **Step 3: `AdminAuthService.login(email, password, ip, userAgent)`**: valida `admin.usuario` activo, cuenta `invalidLoginAttempts` (simple, en memoria en esta fase), actualiza `ultimoAcceso`, crea fila en `admin.sesion` (token_hash = hash SHA-256 del token), retorna `AdminAuthResponse`.
- [x] **Step 4: `AdminAuthController`**: `@PostMapping("/api/admin/auth/login")` y `@PostMapping("/api/admin/auth/logout")` (este último autenticado, revoca la sesión por su token). Público solo el login; el resto del `/api/admin/**` queda autenticado por `SecurityConfig`.
- [x] **Step 5: Modificar `JwtAuthFilter`** para leer `adminId`/`adminRol`; si no existen, fallback al claim `rol` legacy (para no romper las pantallas actuales mientras dure la transición). Poblar autoridades `ROLE_<rol>` + `AdminPermisos.permisosDe(rol)`.
- [x] **Step 6: Correr tests**

```bash
MSYS_NO_PATHCONV=1 docker run --rm -v "//c/Users/ivana/Documents/slime-erp/admin-backend://app" -v slime-erp-maven-repo:/root/.m2 -w //app maven:3.9-eclipse-temurin-21 mvn -q -B test
```

- [x] **Step 7: Commit** — "Agregar login administrativo propio con JWT admin y RBAC en el filtro"

---

### Task 4: Dashboard API (`GET /api/admin/dashboard`)

**Files:**
- Create: `admin-backend/src/main/java/cl/slimerp/admin/dashboard/DashboardController.java`, `DashboardService.java`, `DashboardResponse.java`
- Test: `admin-backend/src/test/java/cl/slimerp/admin/dashboard/DashboardServiceTest.java`

**Interfaces:**
- Consumes: `TenantRepository`, `UsuarioRepository`, `CobranzaService`, `SuscripcionRepository` (Task 10), `AlertaRepository` (Task 12), `AuditLogRepository` (Task 11).
- Produces: `DashboardResponse` con KPIs + gráficos + actividad reciente (spec §37): `companies{total, active, trial, suspended, expired, cancelled, nuevasPeriodo}`, `subscriptions{active, expiring, expired, porPlan[]}`, `payments{pending, overdue}`, `dte{issued, accepted, rejected, pending, error}`, `alerts{critical, warning}`, `evolucionEmpresas[]`, `actividadReciente[]`.

- [x] **Step 1: Definir `DashboardResponse`** (record anidados, shape = §37).
- [x] **Step 2: Test que falla** para `DashboardService.obtener()` con repos mockeados: verifica que se suman los KPIs por estado, que `nuevasPeriodo` usa `fecha_alta >= inicio del mes`, que `porPlan` agrupa suscripciones activas por plan, y que la actividad reciente son los últimos `audit_log`.
- [x] **Step 3: Implementar `DashboardService`** con consultas count/group en repositorios (nada de cargar listas enteras). Para DTE en esta fase: `0` o lectura controlada desde el ERP (el Task 15 lo hace real).
- [x] **Step 4: Correr el test y la suite completa.**
- [x] **Step 5: Commit** — "Agregar Dashboard API con KPIs de plataforma"

---

### Task 5: Frontend — renombrar `plataforma` → `admin`, layout y auth propio

**Files:**
- Rename: `frontend/src/app/features/plataforma/` → `frontend/src/app/features/admin/` (ajustar `@Component` selectors `app-*`)
- Create: `frontend/src/app/core/services/admin-auth.service.ts`
- Create: `frontend/src/app/core/guards/admin.guard.ts` (borrar `plataforma.guard.ts`)
- Modify: `frontend/src/app/core/models/models.ts` (agregar `AdminSesion`, `AdminRol`, `Paginated<T>`)
- Modify: `frontend/src/app/app.routes.ts`
- Modify: `frontend/src/app/features/admin/admin-layout.component.*` (era `plataforma-layout`)

**Interfaces:**
- Consumes: `POST /api/admin/auth/login` (Task 3). Produce `AdminAuthService` (guarda token en `localStorage`, expone `session()`, `tienePermiso(p)`, `logout()`), `admin.guard.ts` que permite los 5 roles admin y redirige al login si no está autenticado.

- [x] **Step 1: Renombrar la carpeta** `features/plataforma` → `features/admin` y los selectores. Mantener durante esta Task las rutas `/plataforma` como redirect a `/admin` para no romper enlaces existentes.
- [x] **Step 2: `AdminAuthService`** con login contra `adminApiUrl` (`/admin/auth/login`), guardado de sesión, `tienePermiso`, `cerrarSesion` (llama al logout del backend).
- [x] **Step 3: `admin.guard.ts`**: permite autenticado con rol en `[SUPER_ADMIN, ADMIN, SUPPORT, FINANCE, AUDITOR]`; el login administrativo se hace desde `/login` (reutiliza `LoginComponent` con flag `modoAdmin` que llama al endpoint admin).
- [x] **Step 4: `admin-layout.component.*`**: menú lateral del spec §30, filtrando ítems por permiso (`*ngIf="auth.tienePermiso('...')"`):
  Dashboard, Empresas (con submenú de estados), Suscripciones, Planes, Pagos, DTE, SII, Alertas, Soporte, Auditoría, Configuración. Header con usuario + rol + cerrar sesión.
- [x] **Step 5: `app.routes.ts`**: rutas hijas `/admin` → `admin-layout`: `dashboard`, `empresas`, `empresas/:id`, `empresas/:id/usuarios`, `suscripciones`, `suscripciones/vencer`, `planes`, `pagos`, `dte`, `sii`, `alertas`, `soporte`, `auditoria`, `configuracion`, `perfil`, `seguridad`, `sistema`.
- [x] **Step 6: Verificar compilación**

```bash
cd frontend && npx ng build --configuration development
```

- [x] **Step 7: Commit** — "Migrar consola de plataforma a /admin con layout, guard y auth administrativo"

---

## FASE 1 — P0 core (módulos obligatorios del spec)

### Task 6: Empresas — listado, paginación, filtros, estados y detalle API

**Files:**
- Modify: `admin-backend/src/main/java/cl/slimerp/admin/empresas/EmpresaAdminService.java`, `EmpresaAdminController.java`, `EmpresaResponse.java`, `CrearEmpresaRequest.java`
- Create: `EmpresaDetalleResponse.java`, `CambiarEstadoRequest.java` (con `motivo` obligatorio), `admin/empresas/EstadoEmpresa.java`
- Test: `EmpresaAdminServiceTest.java` (ampliar), `EmpresaAdminControllerTest.java` (nuevo)

**Interfaces:**
- Consumes: `TenantRepository` (columnas `status`, `business_name`, `last_access_at` de Task 1).
- Produce: `GET /api/admin/empresas?page&limit&rut&razonSocial&estado&plan&estadoSii&fechaCreacionDesde&fechaVencimientoHasta` → `Paginated<EmpresaResponse>`; `GET /api/admin/empresas/{id}` → `EmpresaDetalleResponse`; `PATCH /api/admin/empresas/{id}/estado` body `{estado, motivo}`.

- [x] **Step 1: `EstadoEmpresa` enum** (TRIAL, ACTIVE, SUSPENDED, EXPIRED, BLOCKED, CANCELLED) y mapeo `activo = status in (TRIAL, ACTIVE)` para no romper el ERP.
- [x] **Step 2: Test que falla** (service): paginación devuelve total/totalPages; filtro por estado/plan combinados (spec §6: Búsqueda por RUT/razón social/nombre comercial/ID); `listar()` excluye `PLAN_PLATAFORMA`; `cambiarEstado` valida motivo y registra en auditoría (Task 11) y persiste en `status` manteniendo `activo` sincronizado.
- [x] **Step 3: Implementar** consultas de filtro/paginación en el repositorio (Spring Data `Page`/`Specification`) y detalle con KPIs (usuarios, doc emitidos, último acceso, último DTE, estado SII, estado suscripción, alertas — DTE/SII/suscripción a partir de Tasks 10/12/15/16).
- [x] **Step 4: `CambiarEstadoRequest`** con `motivo` requerido; toda transición emite evento de auditoría (Task 11) y genera alerta (Task 12) si aplica.
- [x] **Step 5: Correr tests + suite.** Commit — "Agregar listado paginado, filtros, estados y detalle de empresas"

---

### Task 7: Detalle de empresa — pantalla con tabs

**Files:**
- Create: `frontend/src/app/features/admin/empresas-detalle/empresas-detalle.component.{ts,html,scss}` (selector `app-empresas-detalle`, standalone)
- Modify: `frontend/src/app/features/admin/empresas/empresas.component.{ts,html}` (enlace a detalle)
- Create: `frontend/src/app/core/services/empresa-admin.service.ts`

**Interfaces:**
- Consumes: `GET /api/admin/empresas/{id}`, `GET /api/admin/empresas/{id}/usuarios` (Task 8), `GET /api/admin/suscripciones?empresaId=` (Task 10), `GET /api/admin/pagos?empresaId=` (Task 13).
- Produce: `EmpresaDetalleComponent` con tabs del spec §8: Resumen, Información, Usuarios, Suscripción, Pagos, DTE, SII, Actividad, Alertas, Soporte.

- [x] **Step 1: `empresa-admin.service.ts`** con métodos de listado paginado/búsqueda/detalle/estado.
- [x] **Step 2: Pantalla listado**: actualizar columnas a las del spec §6 y agregar el buscador RUT/razón social/nombre comercial/ID con debounce cuyo estado se conserva al navegar (§36).
- [x] **Step 3: Pantalla detalle**: cabecera con RUT/razón social/estado (badge) + resumen de KPIs; tabs con lugar vacío "sin datos" para las que dependen de otras Tasks (Suscripción/Pagos/etc.) con estado `Empty` cuando corresponda (§33).
- [x] **Step 4: Diálogo de confirmación** para acciones de estado con motivo obligatorio (spec §32) — componente reutilizable `confirm-action-dialog`.
- [x] **Step 5: `ng build`** y prueba manual. Commit — "Pantalla de detalle de empresa con tabs y confirmacion de cambios de estado"

---

### Task 8: Usuarios de una empresa y de la plataforma

**Files:**
- Modify: `admin-backend/src/main/java/cl/slimerp/admin/usuarios/UsuarioAdminController.java`, `UsuarioAdminService.java`, `UsuarioAdminResponse.java`
- Create: `UsuarioAdminBloquearRequest.java` (motivo), `RevocarSesionesRequest.java`
- Modify: frontend `features/admin/empresas-detalle/` tab Usuarios + `features/admin/usuarios/usuarios.component.*` (ex plataforma-usuarios)
- Test: `UsuarioAdminServiceTest.java` (ampliar)

**Interfaces:**
- Produce: `GET /api/admin/empresas/{id}/usuarios` (rename/alías del actual `GET /api/admin/usuarios?tenantId=`), `PATCH /api/admin/usuarios/{id}/activar|desactivar`, `POST /api/admin/usuarios/{id}/bloquear`, `POST /api/admin/usuarios/{id}/revocar-sesiones`. No se permite eliminar (auditoría).

- [ ] **Step 1: Tests** (service): listado por empresa; activar/desactivar/bloquear con motivo auditan (Task 11); revocar sesiones registra `USER_DISABLED`/`SESSION_REVOKED` en auditoría; validaciones de email/RUT duplicados ya existentes se mantienen.
- [ ] **Step 2: Implementar** endpoints y mantener el contrato actual de `plataforma-usuarios` (paridad de campos en el Response).
- [ ] **Step 3: Frontend** — tab Usuarios en el detalle de empresa con acciones Activar/Desactivar/Bloquear/Revocar sesiones (con confirmación); pantalla global de usuarios admin (role selector por admins en Task 19).
- [ ] **Step 4: `ng build`** + suite backend. Commit — "Usuarios de empresa con bloqueo y revocacion de sesiones auditados"

---

### Task 9: Planes (CRUD)

**Files:**
- Create: `admin-backend/src/main/java/cl/slimerp/admin/planes/Plan.java`, `PlanRepository.java`, `PlanController.java`, `PlanService.java`, `PlanRequest.java`, `PlanResponse.java`
- Test: `PlanServiceTest.java`, `PlanControllerTest.java`
- Create: frontend `features/admin/planes/planes.component.{ts,html,scss}`

**Interfaces:**
- Produce: `GET/POST /api/admin/planes`, `GET/PUT /api/admin/planes/{id}`, `PATCH /api/admin/planes/{id}/estado` (`ACTIVE`/`INACTIVE`). Campos del spec §11 (nombre, descripción, precio mensual/anual, límites, módulos JSONB, características, estado).

- [ ] **Step 1: Tests** — crear valida campos obligatorios; desactivar (`INACTIVE`) está permitido solo si no hay suscripciones activas asociadas; eliminar físicamente queda prohibido (`DELETE` no expuesto); `modulos` se serializa/deserializa como lista de strings.
- [ ] **Step 2: Implementar** service/controller con `@PreAuthorize("hasAuthority('PLANES_EDITAR')")` para escritura y `PLANES_VER` para lectura.
- [ ] **Step 3: Frontend** — tabla + formulario de plan (2 columnas: identificación / precios y límites / módulos y estado), usando `PlanResponse`. Responsive.
- [ ] **Step 4: `ng build`** + suite backend. Commit — "CRUD de planes con validaciones y estado"

---

### Task 10: Suscripciones (estados, cambio de plan, extensión, período de gracia)

**Files:**
- Create: `admin-backend/src/main/java/cl/slimerp/admin/suscripciones/Suscripcion.java`, `SuscripcionRepository.java`, `SuscripcionController.java`, `SuscripcionService.java`, `SuscripcionRequest.java`, `ExtenderSuscripcionRequest.java`, `CambiarPlanRequest.java`
- Test: `SuscripcionServiceTest.java`, `SuscripcionControllerTest.java`
- Create: frontend `features/admin/suscripciones/suscripciones.component.{ts,html,scss}`

**Interfaces:**
- Produce: `GET /api/admin/suscripciones?page&limit&estado&planId&empresaId&proximasAVencerDias`, `GET /{id}`, `POST /api/admin/suscripciones`, `POST /{id}/extend` (body `nuevoVencimiento` o `dias`), `POST /{id}/cambiar-plan`, `POST /{id}/suspend`, `POST /{id}/reactivate`. Estados del spec §10 (TRIAL…EXPIRED). `gracePeriodDays` se lee de `admin.configuracion` (Task 18), nunca hardcodeado — la transición PAST_DUE→SUSPENDED la ejecuta el job del Task 21.

- [ ] **Step 1: Tests** — crear suscripción valida overlap de empresa activa (una suscripción activa por empresa); `extend` modifica `fecha_vencimiento` y audita `SUBSCRIPTION_EXTENDED` con old/new; `cambiar-plan` valida precio del plan y audita `PLAN_CHANGE`; `suspend`/`reactivate` auditan y actualizan `tenant.status` (Task 6); `gracePeriodDays` viene de configuración.
- [ ] **Step 2: Implementar** service/controller + repositorio con paginación y filtros combinables.
- [ ] **Step 3: Frontend** — listado con columnas del spec §10, filtros, y acciones con confirmación (§32).
- [ ] **Step 4: `ng build`** + suite backend. Commit — "Gestion de suscripciones con extension, cambio de plan y periodo de gracia"

---

### Task 11: Auditoría — servcio central + endpoints + pantalla

**Files:**
- Create: `admin-backend/src/main/java/cl/slimerp/admin/auditoria/AuditLog.java`, `AuditLogRepository.java`, `AuditService.java`, `AuditController.java`
- Test: `AuditServiceTest.java`, `AuditControllerTest.java`
- Create: frontend `features/admin/auditoria/auditoria.component.{ts,html,scss}`

**Interfaces:**
- Produce: `GET /api/admin/audit?page&limit&adminUserId&companyId&modulo&action&desde&hasta` → `Paginated<AuditLogResponse>` (spec §19: campos del `audit_loges`). `AuditService.registrar(...)` consumido por Tasks 6/8/9/10/13/14/16/17/18.

- [ ] **Step 1: Tests** — `AuditService.registrar` guarda la acción con `oldValue`/`newValue` como JSON (diff de campos relevantes); el controller filtra por combinaciones y pagina; registrar no falla (log error) aunque el repositorio devuelva error.
- [ ] **Step 2: Implementar** `AuditLog` (JSONB via `@JdbcTypeCode(SqlTypes.JSON)` o `String` con conversión manual según el patrón del repo para JSONB).
- [ ] **Step 3: Frontend** — tabla con filtros + detalle expandido mostrando "Antes/Después" legible (spec §19 ejemplo).
- [ ] **Step 4: `ng build`** + suite backend. Commit — "Auditoria central con endpoints de consulta y filtros"

---

### Task 12: Centro de alertas

**Files:**
- Create: `admin-backend/src/main/java/cl/slimerp/admin/alertas/Alerta.java`, `AlertaRepository.java`, `AlertaController.java`, `AlertaService.java`
- Test: `AlertaServiceTest.java`, `AlertaControllerTest.java`
- Create: frontend `features/admin/alertas/alertas.component.{ts,html,scss}`

**Interfaces:**
- Produce: `GET /api/admin/alerts?severity&status&companyId&tipo` (paginado), `PATCH /api/admin/alerts/{id}/read`, `POST /api/admin/alerts/{id}/resolve` (body opcional `motivo`). `AlertaService.crear(...)` consumido por Tasks 6/8/10/15/16/21.

- [ ] **Step 1: Tests** — listar filtra por severity/status; leer marca `status=READ` y `leidaEn`; resolver exige que exista, marca `RESOLVED` + `resueltaEn` y audita; crear valida severity/tipo y evita duplicados idénticos `OPEN`.
- [ ] **Step 2: Implementar** entidad/controller (spec §17: campos `id,type,severity,title,description,companyId,createdAt,readAt,resolvedAt,status`).
- [ ] **Step 3: Frontend** — listado con badges por severidad, filtros, acciones leer/resolver y contador de críticas en el layout.
- [ ] **Step 4: `ng build`** + suite backend. Commit — "Centro de alertas con lectura y resolucion"

---

## FASE 2 — P1 (MVP completo)

### Task 13: Pagos — consolidación con cobranza

**Files:**
- Modify: `admin-backend/src/main/java/cl/slimerp/admin/cobranza/CobranzaController.java`, `CobranzaService.java`, `PagoCobranzaRequest.java`
- Create: `admin-backend/src/main/java/cl/slimerp/admin/pagos/PagoController.java` (o ampliar cobranza con ruta `/api/admin/pagos`), `RegistrarPagoManualRequest.java`
- Test: `CobranzaServiceTest.java` (ampliar), `PagoControllerTest.java` (nuevo)
- Create: frontend `features/admin/pagos/pagos.component.{ts,html,scss}`

**Interfaces:**
- Produce: sobre las tablas `cobranza_empresa`/`cobranza_pago` (sin duplicar): `GET /api/admin/pagos?page&limit&empresaId&estado` (consolidado de cobranza), `POST /api/admin/pagos` (registro manual), `GET /api/admin/pagos/{id}`. `RegistrarPagoManualRequest{companyId, suscripcionId?, monto, metodo, referencia, fecha}`. Crea/actualiza cobranza y audita `PAYMENT_REGISTERED` (§12, §43).

- [ ] **Step 1: Tests** — registrar pago manual valida monto>0 y empresa existente; si no hay `cobranza_empresa` del período la crea; marca el estado de la cobranza; audita `PAYMENT_REGISTERED` con `oldValue`/`newValue`.
- [ ] **Step 2: Implementar** servicio de pagos sobre el repositorio de cobranza existente; estados del spec §12 (PENDING/PAID/FAILED/REFUNDED/CANCELLED).
- [ ] **Step 3: Frontend** — tabla de pagos con filtros, registro manual como dialog, historial por empresa en el detalle (tab Pagos).
- [ ] **Step 4: `ng build`** + suite backend. Commit — "Modulo de pagos sobre la cobranza existente"

---

### Task 14: Vencimientos (`/admin/subscriptions/expiring`)

**Files:**
- Modify: `admin-backend/src/main/java/cl/slimerp/admin/suscripciones/SuscripcionService.java`, `SuscripcionController.java`
- Create: `VencimientosResponse.java`
- Test: `SuscripcionServiceTest.java` (ampliar)
- Create: frontend `features/admin/vencimientos/vencimientos.component.{ts,html,scss}`

**Interfaces:**
- Produce: `GET /api/admin/subscriptions/expiring` → ventanas `Vencen hoy`, `Vencen en 3 días`, `7 días`, `30 días`, `Vencidas` (spec §13) con acciones `ver empresa`, `contactar`, `extender`, `registrar pago`, `suspender`, `reactivar` (reutiliza tasks 10/13).

- [ ] **Step 1: Tests** — la ventana "hoy" considera `fecha_vencimiento = CURDATE` y estado no CANCELLED; las ventanas suman rangos (`(0,3]`, `(3,7]`, `(7,30]`); "vencidas" = `fecha_vencimiento < CURDATE` y estado en (PAST_DUE|EXPIRED|ACTIVE vencida); los totales se calculan por query agregada.
- [ ] **Step 2: Implementar** endpoint con consultas `between`/`before` en `SuscripcionRepository`.
- [ ] **Step 3: Frontend** — sección con las 5 ventanas en cards + acciones rápidas.
- [ ] **Step 4: `ng build`** + suite backend. Commit — "Seccion de vencimientos por ventanas de tiempo"

---

### Task 15: Monitoreo DTE (read-only)

**Files:**
- Create: `admin-backend/src/main/java/cl/slimerp/admin/dte/DteController.java`, `DteService.java`, `DteResponse.java`, `DteDashboardResponse.java`
- Test: `DteServiceTest.java`
- Create: frontend `features/admin/dte/dte.component.{ts,html,scss}`

**Interfaces:**
- Consumes: documentos del ERP (tablas del backend de negocio: `venta`, `folio_venta`; conocer su shape leyendo `backend` antes de implementar). SII (Task 16) provee `estadoSii` cuando exista.
- Produce: `GET /api/admin/dte?empresaId&tipoDte&estado&fechaDesde&fechaHasta&folio&rutReceptor` (paginado, §16) y `GET /api/admin/dte/dashboard` (emitidos/aceptados/rechazados/pendientes/error). Es **monitoreo y diagnóstico**: sin endpoints de emisión (§16).

- [ ] **Step 1: Inspeccionar** el modelo de documentos en `backend` (ventas/folios) y mapear el contrato `DteResponse` (campos mínimos del spec §16: estado, folio, tipo, RUT receptor).
- [ ] **Step 2: Tests** — el service agrega los contadores del dashboard solo con documentos del ERP (mock controlado); los filtros se delegan en consultas con paginación; nunca expone payloads sensibles.
- [ ] **Step 3: Implementar** service/controller con `@PreAuthorize("hasAuthority('DTE_VER')")`.
- [ ] **Step 4: Frontend** — dashboard DTE (cards) + tabla de monitoreo con filtros y drill-down al detalle de la empresa.
- [ ] **Step 5: `ng build`** + suite backend. Commit — "Monitoreo de DTE global read-only"

---

### Task 16: Estado SII por empresa

**Files:**
- Create: `admin-backend/src/main/java/cl/slimerp/admin/sii/SiiController.java`, `SiiService.java`, `SiiEstadoResponse.java`
- Test: `SiiServiceTest.java`
- Create: frontend `features/admin/sii/sii.component.{ts,html,scss}` (o tab en detalle de empresa)

**Interfaces:**
- Consumes: configuración SII del ERP (si existe en `backend`) y alertas de certificado (Task 12).
- Produce: `GET /api/admin/sii` (global por empresa: estado `CONFIGURED/NOT_CONFIGURED/CERTIFICATE_EXPIRING/CERTIFICATE_EXPIRED/CONNECTION_ERROR`, `certificadoVence`, `ambiente`, `ultimaComunicacion`, `ultimoDte`) y `GET /api/admin/sii/empresas/{id}`. **Nunca** devuelve secretos ni credenciales (§15).

- [ ] **Step 1: Inspeccionar** el subsistema SII/certificados del `backend` (si existe) para definir el source de datos; si no existe aun, implementar el endpoint con estado calculado (empresa sin config → `NOT_CONFIGURED`) y alertas de certificado.
- [ ] **Step 2: Tests** — el service enmascara certificados (nunca hay contenido de certificado en la respuesta); estados derivados correctos; `CERTIFICATE_EXPIRING` cuando vence en <30 días.
- [ ] **Step 3: Implementar** + alertas asociadas (`SiiService` consume `AlertaService` para generar `CERTIFICATE_EXPIRING`/`SII_ERROR`).
- [ ] **Step 4: Frontend** — vista global con filtro por estado + detalle de empresa.
- [ ] **Step 5: `ng build`** + suite backend. Commit — "Estado SII por empresa sin exponer credenciales"

---

### Task 17: Sistema de soporte (tickets)

**Files:**
- Create: `admin-backend/src/main/java/cl/slimerp/admin/soporte/TicketSoporte.java`, `TicketSoporteRepository.java`, `SoporteController.java`, `SoporteService.java`, `TicketRequest.java`, `TicketResponse.java`
- Test: `SoporteServiceTest.java`, `SoporteControllerTest.java`
- Create: frontend `features/admin/soporte/soporte.component.{ts,html,scss}`

**Interfaces:**
- Produce: `GET /api/admin/support?page&limit&status&prioridad&empresaId`, `POST /api/admin/support`, `GET /api/admin/support/{id}`, `PATCH /api/admin/support/{id}` (asignar/prioridad/cambiar estado con motivo), `POST /api/admin/support/{id}/resolver`. Campos del spec §18 (prioridades LOW…CRITICAL, estados OPEN…CLOSED).

- [ ] **Step 1: Tests** — crear ticket requiere `subject` y empresa válida (si es interna); transición de estado valida flujo (no REOPEN desde CLOSED sin ruta explícita); `resolver` audita y setea `resueltoEn`.
- [ ] **Step 2: Implementar** service/controller (`@PreAuthorize` con `SOPORTE_VER`/`SOPORTE_EDITAR`).
- [ ] **Step 3: Frontend** — cola de tickets con filtros, detalle con historial de cambios y acciones por rol (SUPPORT edita, AUDITOR solo ve).
- [ ] **Step 4: `ng build`** + suite backend. Commit — "Sistema de soporte basado en tickets"

---

### Task 18: Configuración global

**Files:**
- Create: `admin-backend/src/main/java/cl/slimerp/admin/configuracion/Configuracion.java`, `ConfiguracionRepository.java`, `ConfiguracionController.java`, `ConfiguracionService.java`
- Test: `ConfiguracionServiceTest.java`
- Create: frontend `features/admin/configuracion/configuracion.component.{ts,html,scss}`

**Interfaces:**
- Produce: `GET /api/admin/configuracion` (todas las claves), `PATCH /api/admin/configuracion` (body map clave→valor) y helpers `getInt(clave,default)`, `getString(...)`. Claves del spec §22: `plataforma.nombre`, `suscripcion.gracePeriodDays`, `suscripcion.trialDays`, logo, email soporte, configuración de alertas y suscripciones.

- [ ] **Step 1: Tests** — get devuelve defaults si la clave no existe; getInt falla limpio si el JSON no es numérico; patch valida claves conocidas (lista blanca) y audita `CONFIG_CHANGED`; cada cambio actualiza `actualizado_por`.
- [ ] **Step 2: Implementar** service + controller (solo `CONFIG_GLOBAL_EDITAR` escribe; `CONFIG_GLOBAL_VER` lee).
- [ ] **Step 3: Frontend** — formulario por secciones (plataforma / suscripción / alertas), layout responsive.
- [ ] **Step 4: `ng build`** + suite backend. Commit — "Configuracion global con lista blanca de claves"

---

## FASE 3 — P2 (futuro / madurez)

### Task 19: Sesión administrativa — perfil, seguridad y admins

**Files:**
- Create: `admin-backend/src/main/java/cl/slimerp/admin/usuario/AdminSesionController.java`, `AdminUsuarioController.java` (gestión de admins), `PerfilResponse.java`
- Test: `AdminSesionControllerTest.java`
- Create: frontend `features/admin/perfil/perfil.component.*` y `features/admin/seguridad/seguridad.component.*`

**Interfaces:**
- Produce: `GET /api/admin/profile` (usuario, email, rol, último acceso), `GET /api/admin/sessions` (activas), `POST /api/admin/sessions/{id}/revoke`, `POST /api/admin/auth/logout` (ya en Task 3); CRUD de `admin.usuario` para crear ADMIN/SUPPORT/FINANCE/AUDITOR (solo `USUARIOS_ADMIN_EDITAR`).

- [ ] **Step 1: Tests** — revocar otra sesión requiere permiso de al menos ADMIN; un admin no puede revocar la sesión de un SUPER_ADMIN salvo SUPER_ADMIN; crear admin valida email/rol/activo y audita.
- [ ] **Step 2: Implementar** controller/service sobre `AdminSesionRepository`/`AdminUsuarioRepository`.
- [ ] **Step 3: Frontend** — pantallas Perfil y Seguridad (sesiones activas, revocar, cerrar otras).
- [ ] **Step 4: `ng build`** + suite backend. Commit — "Sesiones administrativas, perfil y gestion de admins"

---

### Task 20: Health monitoring

**Files:**
- Create: `admin-backend/src/main/java/cl/slimerp/admin/sistema/SistemaService.java`, `HealthController.java`, `HealthResponse.java`
- Test: `SistemaServiceTest.java`
- Create: frontend `features/admin/sistema/sistema.component.*`

**Interfaces:**
- Produce: `GET /api/admin/sistema/health` → estados de Admin API, ERP API (`/actuator/health` o ping), PostgreSQL (query `SELECT 1`), SII, DTE y jobs, con `HEALTHY|DEGRADED|DOWN` (§38). Implementación básica (ping y timeouts cortos).

- [ ] **Step 1: Tests** — cada probe devuelve HEALTHY/DEGRADED/DOWN con un stub de conexión; el estado global es DOWN si algún componente crítico está DOWN y DEGRADED si solo fallan SII/DTE.
- [ ] **Step 2: Implementar** probes con `RestTemplate`/`JdbcTemplate` y timeout (5s) por servicio.
- [ ] **Step 3: Frontend** — pantalla con badges por servicio y última comprobación.
- [ ] **Step 4: `ng build`** + suite backend. Commit — "Health monitoring de la plataforma"

---

### Task 21: Jobs automáticos (scheduler)

**Files:**
- Create: `admin-backend/src/main/java/cl/slimerp/admin/jobs/VencimientoJob.java`, `CertificadoJob.java`, `LimpiezaSesionesJob.java` (bajo `@EnableScheduling`)
- Test: `VencimientoJobTest.java`
- Create: (opcional) `jobs/JobResult.java`

**Interfaces:**
- Produce (espec §39): verificar suscripciones próximas a vencer, detectar vencidas (aplicar transición `PAST_DUE` a los >`gracePeriodDays`, luego `SUSPENDED`), generar alertas, verificar certificados, detectar errores DTE, limpiar sesiones expiradas. Todo en backend, nunca en Angular.

- [ ] **Step 1: Tests** — el job de vencimientos: (a) no toca suscripciones futuras, (b) pasa a `PAST_DUE` las vencidas dentro del período de gracia, (c) pasa a `SUSPENDED` las que superan el período de gracia, (d) genera alertas `WARNING`/`CRITICAL` solo cuando corresponde y (e) audita cada transición.
- [ ] **Step 2: Implementar** schedulers con cron configurable por clave de configuración (Task 18); requisito de idempotencia (aunque se ejecuten dos veces no duplican alertas).
- [ ] **Step 3: Commit** — "Jobs automaticos de vencimientos, certificados y limpieza de sesiones"

---

### Task 22: Notificaciones (infraestructura)

**Files:**
- Create: `admin-backend/src/main/java/cl/slimerp/admin/notificaciones/EventoPlataforma.java`, `NotificationService.java`, `NotificationEventPublisher.java`
- Test: `NotificationServiceTest.java`

**Interfaces:**
- Produce un bus de eventos en memoria/publicador (`SUBSCRIPTION_EXPIRING`, `PAYMENT_RECEIVED`, `SII_ERROR`, `CERTIFICATE_EXPIRING`, `DTE_ERROR`… spec §23). Los handlers iniciales solo generan alertas (Task 12). No implementar Email/Push/WhatsApp en esta fase.

- [ ] **Step 1: Tests** — el publisher entrega el evento al handler de alertas y la deduplicación evita alertas repetidas en un período.
- [ ] **Step 2: Implementar** event publisher + handler a `AlertaService`.
- [ ] **Step 3: Commit** — "Infraestructura de notificaciones basada en eventos (canal inicial: alertas)"

---

### Task 23: Seguridad y UX transversal — consolidación

**Files:**
- Modify: `admin-backend/src/main/java/cl/slimerp/admin/config/SecurityConfig.java` (rate limiting inicial en memoria / bucket), `GlobalExceptionHandler.java`
- Modify: frontend pantallas para cubrir estados `Loading/Empty/Error/Success/Forbidden` (§33) y confirmaciones en todas las acciones críticas (§32)

**Steps:**
- [ ] **Step 1:** Rate limiting por IP en el login (p.ej. 5 intentos/min, cuenta asociada bloqueada 15 min). Tests del interceptor.
- [ ] **Step 2:** `GlobalExceptionHandler` mapea `Forbidden` → 403 con mensaje claro; validaciones → 400 con errores por campo (cerca del campo en el frontend, según CLAUDE.md).
- [ ] **Step 3:** Auditoría transversal: verificar que los `logs de seguridad` registran login/logout/admins revocados (§20).
- [ ] **Step 4:** Revisión UX de una pantalla por persona-rol (SUPPORT, FINANCE, AUDITOR) para validar que el menú y las acciones se filtran por permiso.
- [ ] **Step 5: `ng build`** + suite backend completa. Commit — "Endurecimiento de seguridad (rate limiting, errores 403) y estados de UX"

---

### Task 24: Verificación final contra la especificación (§43)

- [ ] **Step 1:** Ejecutar la suite completa de `admin-backend` y `ng build` del frontend en verde.
- [ ] **Step 2:** Recorrer los criterios de aceptación del §43: Empresas (listar/buscar/filtrar/detalle/cambiar estado/auditado), Usuarios (visualizar/activar/desactivar/permisos), Suscripciones (visualizar/asignar/cambiar plan/extender/vencimientos), Pagos (visualizar/registrar/historial), DTE/SII (estado global/detección de errores/consulta de problemáticos), Alertas (mostrar/leer/resolver), Auditoría (toda acción crítica registrada), Seguridad (usuario sin permisos no ejecuta acciones restringidas).
- [ ] **Step 3:** Prueba manual del flujo del §45 (un admin responde: cuántas empresas, activas, en prueba, que deben pagar, vencidas, SII, DTE, usuarios con problemas, alertas, acciones realizadas).
- [ ] **Step 4:** Prueba de IDOR/cross-tenant: manipular `companyId` en requests y verificar 403/404 con empresa ajena (§20, §40).
- [ ] **Step 5: Commit final** — "Saavia Admin ERP: verificacion final contra la especificacion"

---

## Criterios de aceptación (extraído del spec §43)

- **Empresas:** listar, buscar, filtrar, ver detalle, cambiar estado; todas las acciones auditadas.
- **Usuarios:** visualizar, activar/desactivar, se respetan permisos.
- **Suscripciones:** visualizar, asignar/cambiar plan, extender, manejar vencimientos.
- **Pagos:** visualizar, registrar, mantener historial.
- **DTE/SII:** estado global, detección de errores, consulta de DTE problemáticos.
- **Alertas:** mostrar, marcar como leída, resolver.
- **Auditoría:** toda acción administrativa crítica genera registro.
- **Seguridad:** un usuario administrativo sin permisos no ejecuta acciones restringidas.
