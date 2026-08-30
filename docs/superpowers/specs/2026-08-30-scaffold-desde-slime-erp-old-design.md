# Scaffold de slime-erp a partir de slime-erp_old — Diseño

## Objetivo

`slime-erp` es un ERP nuevo, más simple, que arranca reutilizando partes
puntuales del proyecto anterior (`C:\Users\ivana\Documents\slime-erp_old`):

1. El **sistema de diseño** del frontend (tokens de color/tipografía/espaciado
   /radios/sombras), migrado de Tailwind a **Angular Material** con un tema
   custom construido sobre esos mismos tokens.
2. La **configuración multi-tenant** del backend (Spring Boot): autenticación
   JWT, entidades tenant/usuario, y autogestión de tenants.
3. Un modelo de **roles y permisos** (RBAC) fijo, aplicado tanto en backend
   como en frontend, con un módulo básico de gestión de usuarios por tenant.

Todo lo demás (ventas, compras, inventario, categorías, bodegas, formas de
pago, tesorería, reportería, **DTE/facturación electrónica**, roles/permisos
configurables) queda fuera de esta etapa. Se diseñará y construirá como
módulos nuevos más adelante, sobre esta base.

## Alcance

### Base de datos

**PostgreSQL**, una sola instancia compartida por todos los tenants (shared
database, shared schema, columna `tenant_id` en cada tabla de negocio), igual
que el proyecto original. Se levanta vía `docker-compose.yml` con imagen
`postgres:16-alpine`. Migraciones versionadas con **Flyway**
(`db/migration/V1__init.sql`).

### Incluido — Backend (`backend/`)

Proyecto Spring Boot 21 / Maven, paquete `cl.slimerp` (se mantiene el nombre
de paquete del proyecto original).

Copiar y adaptar:

- Scaffolding de build: `pom.xml`, `Dockerfile`, `application.yml`,
  `application-docker.yml`, `SlimErpApplication.java`.
- `config/`: `JwtAuthFilter`, `JwtService`, `SecurityConfig`
  (`@EnableMethodSecurity` ya presente), `TenantContext`,
  `GlobalExceptionHandler`.
  - **Excluido:** `UploadsConfig` (es para imágenes de producto; no aplica sin
    ese módulo todavía).
- `tenant/`: `Tenant`, `Usuario`, `TenantRepository`, `UsuarioRepository` —
  tal cual. `Rol` se mantiene igual: `SUPER_ADMIN, ADMIN, VENDEDOR,
  COMPRADOR, VISUALIZADOR`.
- `auth/`: `AuthController`, `LoginRequest`, `LoginResponse` — login JWT
  funcional completo. `LoginResponse` se amplía para incluir `permisos`
  (lista de `Permiso` del usuario) además de `rol`.
- `admin/`: `EmpresaAdminController`, `EmpresaAdminService`,
  `CrearEmpresaRequest`, `EmpresaResponse`, `EmpresaConflictException` —
  autogestión de altas/activación de tenants, protegido con
  `@PreAuthorize("hasRole('SUPER_ADMIN')")` (igual que el original).
- Catálogo básico (paquete nuevo `catalogo/`, en vez de mezclarlo en
  `common/` con todo lo demás que no traemos):
  - `Cliente` (entity + controller + repository + `ClienteRequest`).
  - `Producto` (entity + controller + repository).
  - Alcance de campos: los definidos en `V1__init.sql` original (nombre, rut,
    contacto para cliente; sku, nombre, descripción, precios, stock,
    controla_stock para producto). Sin categoría, subcategoría, bodega,
    imagen ni stock mínimo — esos son de módulos posteriores no incluidos.
  - Endpoints protegidos por permiso (`@PreAuthorize("hasAuthority('CLIENTES_VER')")`,
    etc.), no solo por autenticación.
- **Nuevo módulo `permisos/`**: enum `Permiso` fijo y una clase
  `RolPermisos` (o método estático) con el mapeo fijo `Rol → Set<Permiso>`.
  No requiere tabla nueva — los permisos son fijos en código, no
  configurables por tenant en esta etapa.

  Catálogo de permisos (acotado a lo que ya está en alcance):
  `CLIENTES_VER, CLIENTES_EDITAR, PRODUCTOS_VER, PRODUCTOS_EDITAR,
  USUARIOS_VER, USUARIOS_EDITAR, EMPRESAS_ADMINISTRAR`.

  Mapeo por rol:
  | Rol | Permisos |
  |---|---|
  | SUPER_ADMIN | `EMPRESAS_ADMINISTRAR` + todos los demás (plataforma) |
  | ADMIN | `CLIENTES_VER`, `CLIENTES_EDITAR`, `PRODUCTOS_VER`, `PRODUCTOS_EDITAR`, `USUARIOS_VER`, `USUARIOS_EDITAR` |
  | VENDEDOR | `CLIENTES_VER`, `CLIENTES_EDITAR`, `PRODUCTOS_VER` |
  | COMPRADOR | `PRODUCTOS_VER`, `PRODUCTOS_EDITAR` |
  | VISUALIZADOR | `CLIENTES_VER`, `PRODUCTOS_VER` |

  `JwtService`/`JwtAuthFilter` se amplían para que las autoridades de Spring
  Security del usuario autenticado incluyan, además del rol (`ROLE_<rol>`,
  como ya hace el original para `hasRole`), un `GrantedAuthority` por cada
  permiso de su rol — así los controladores pueden usar
  `@PreAuthorize("hasAuthority('PERMISO')")`.

- **Nuevo módulo `usuarios/`**: `UsuarioController` con CRUD de usuarios
  **dentro del propio tenant** (crear, editar, asignar rol, activar/
  desactivar), protegido por `USUARIOS_VER`/`USUARIOS_EDITAR`, filtrado
  siempre por `tenant_id` vía `TenantContext` igual que el resto de
  entidades de negocio. Reutiliza `Usuario`/`UsuarioRepository`/`Rol` de
  `tenant/`. Sin esto, los roles/permisos no tendrían forma de probarse más
  allá del usuario demo sembrado por la migración.
- Migraciones Flyway: una única `V1__init.sql` nueva, recortada a las tablas
  `tenant`, `usuario`, `cliente`, `producto`, con el mismo seed de desarrollo
  (tenant demo + usuario `admin@demo.cl` / `admin123`, rol `ADMIN`).

Explícitamente excluido del backend: `common/` (Banco, Categoria,
Subcategoria, Proveedor, FormaPago, ImagenStorageService, TipoProducto),
`compras/`, `ventas/`, `inventario/`, `reporteria/`, `tesoreria/`, **`dte/`
completo (nada de XML, CAF, certificados ni facturación electrónica)**, y
cualquier modelo de roles/permisos configurables por tenant (queda fijo en
código por ahora).

### Incluido — Frontend (`frontend/`)

Proyecto Angular 18 + **Angular Material** (reemplaza a Tailwind como
sistema de componentes; Tailwind se retira del proyecto).

Copiar y adaptar:

- Scaffolding de build: `angular.json`, `tsconfig*.json`, `package.json`
  (sin `jspdf`/`jspdf-autotable`, que solo se usan para PDFs de venta/DTE; se
  agregan `@angular/material` y `@angular/cdk`; se quita `tailwindcss`,
  `autoprefixer`, `postcss` y `tailwind.config.js`), `Dockerfile`,
  `nginx.conf`, `.editorconfig`, `src/index.html`, `src/main.ts`,
  `src/app/app.config.ts` (se agrega el provider de animations que requiere
  Material), `src/app/app.component.*`, `src/environments/*`.
- **Tema Material custom**: se define un tema M3 (`mat.define-theme` o
  equivalente en la versión instalada) que mapea sus tokens de color,
  tipografía, densidad y forma a los valores ya existentes en
  `src/styles/tokens/*.css` (colores, tipografía, espaciado, radios,
  sombras), en vez de usar la paleta azul por defecto de Material. Los
  archivos de tokens se mantienen como fuente de verdad; el tema Material
  se construye a partir de ellos.
- `src/app/layout/` — shell migrado a `mat-toolbar` (topbar) +
  `mat-sidenav-container`/`mat-sidenav` (sidebar colapsable), conservando la
  misma disposición y look actual vía el tema custom. Menú lateral con
  **Dashboard**, **Clientes**, **Productos** y **Usuarios**, filtrado según
  los permisos reales del usuario logueado (p. ej. "Usuarios" solo visible
  con `USUARIOS_VER`). Branding actualizado de "Slim ERP" a "Slime ERP".
- `src/app/shared/modal/` — migrado a `MatDialog` o mantenido como
  componente propio si no aporta migrarlo (se decide en implementación,
  manteniendo el mismo look).
- `src/app/features/login/` — **funcional**: formulario reactivo con
  componentes Material (`mat-form-field`, `mat-button`), llama a
  `AuthService.login()` real contra el backend.
- `src/app/features/dashboard/` — HTML/SCSS del layout de tarjetas y
  gráfico de barras migrado a componentes Material donde aplique (cards,
  botones), con datos estáticos de ejemplo en el `.ts` en vez de
  `DashboardService` (que depende de ventas, fuera de alcance).
- **Nueva pantalla `features/usuarios/`**: listado + alta/edición de
  usuarios del tenant, con selector de rol, usando componentes Material
  (`mat-table`, `mat-form-field`, `mat-select`). Protegida por permiso.
- **Nuevas pantallas `features/clientes/` y `features/productos/`**:
  listado + alta/edición básicos (los mismos campos que expone el backend
  de `catalogo/`), con componentes Material (`mat-table`, `mat-form-field`).
  Sin esto, los permisos `CLIENTES_*`/`PRODUCTOS_*` no tendrían una pantalla
  real donde aplicarse. Cada una oculta sus acciones de edición si el
  usuario solo tiene el permiso `*_VER`.
- `src/app/core/services/auth.service.ts` — **nuevo** (antes excluido):
  login real, maneja sesión (token + datos de usuario incluyendo rol y
  permisos) en memoria/localStorage, expone helpers de tipo
  `tienePermiso(permiso)`.
- `src/app/core/interceptors/auth.interceptor.ts` — **nuevo**: adjunta el
  JWT a las peticiones.
- `src/app/core/guards/auth.guard.ts` — **nuevo**: protege rutas que
  requieren sesión iniciada. (`tenantGuard` y `superAdminGuard` del proyecto
  viejo no se copian todavía — se evalúan cuando haya rutas que los
  necesiten).
- `src/app/core/models/models.ts` — nuevo, mínimo: tipos para `Usuario`,
  `Rol`, `Permiso`, `LoginResponse`, `Cliente`, `Producto` (los necesarios
  para las pantallas incluidas).
- `src/app/app.routes.ts` — `login`, `dashboard`, `usuarios`, `clientes`,
  `productos`, todas con `authGuard`.

Explícitamente excluido del frontend: `core/guards/tenant.guard.ts` y
`core/guards/super-admin.guard.ts`, y todos los `features/*` de negocio que
no sean login/dashboard/usuarios (proveedores, categorías, bodegas,
formas-pago, ventas, compras, movimientos, reporteria, tesoreria, admin de
empresas, **facturacion/DTE completo**).

### Explícitamente fuera de alcance (todo el proyecto)

Proveedores, ventas, compras, inventario, categorías, bodegas, formas de
pago, tesorería, reportería, pantalla de administración de empresas
(SUPER_ADMIN) del frontend, roles/permisos configurables por tenant, y
**cualquier cosa relacionada a DTE / facturación electrónica / XML / CAF /
certificados**, tanto en frontend como en backend.

## Estructura resultante

```
slime-erp/
├── .env / .env.example        (ya existen, sin cambios)
├── docker-compose.yml          (nuevo, adaptado del original; servicio Postgres 16)
├── backend/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/
│       ├── java/cl/slimerp/
│       │   ├── SlimErpApplication.java
│       │   ├── config/        (Jwt*, SecurityConfig, TenantContext, GlobalExceptionHandler)
│       │   ├── tenant/        (Tenant, Usuario, Rol, repos)
│       │   ├── auth/          (AuthController, LoginRequest/Response)
│       │   ├── admin/         (EmpresaAdmin*)
│       │   ├── permisos/      (Permiso, RolPermisos)
│       │   ├── usuarios/      (UsuarioController y DTOs, nuevo)
│       │   └── catalogo/      (Cliente, Producto)
│       └── resources/
│           ├── application.yml
│           ├── application-docker.yml
│           └── db/migration/V1__init.sql
└── frontend/
    ├── angular.json, package.json, tsconfig*.json  (sin tailwind.config.js)
    ├── Dockerfile, nginx.conf
    └── src/
        ├── index.html, main.ts
        ├── styles.scss, styles/_components.scss (recortado a lo que Material no cubra),
        │   styles/tokens/*.css, styles/material-theme.scss (nuevo)
        └── app/
            ├── app.component.*, app.config.ts, app.routes.ts
            ├── core/{services,interceptors,guards,models}/  (auth mínimo)
            ├── layout/
            ├── shared/modal/
            └── features/{login, dashboard, usuarios}/
```

## Testing / verificación

- Backend: `mvn -q -pl backend compile` (o `mvn spring-boot:run`) para
  confirmar que compila y Flyway aplica `V1__init.sql` sin errores.
  Verificar manualmente que un usuario `VENDEDOR` recibe 403 en un endpoint
  que requiere `USUARIOS_EDITAR`, y que `SUPER_ADMIN` puede llamar
  `/api/admin/empresas`.
- Frontend: `npm install && ng build` dentro de `frontend/` para confirmar
  que compila sin Tailwind y con Angular Material. Verificar manualmente
  que el login autentica contra el backend y que el menú lateral cambia
  según el rol del usuario logueado.
- No se agregan tests automatizados nuevos en este scaffold — es una copia
  adaptada de código ya existente más el módulo nuevo de permisos/usuarios,
  sin cobertura de tests propia todavía.

## Siguientes pasos (fuera de este spec)

- Evaluar si se necesitan `tenantGuard`/`superAdminGuard` y la pantalla de
  administración de empresas en el frontend.
- Diseñar e implementar, uno a uno, los módulos de negocio excluidos
  (proveedores, ventas, compras, inventario, reportería, tesorería) y,
  eventualmente, un módulo de facturación electrónica propio — cuando se
  decida abordarlo, será su propio spec.
- Evaluar si el modelo de roles/permisos fijo necesita evolucionar a
  configurable por tenant.
