# Scaffold de slime-erp a partir de slime-erp_old — Diseño

## Objetivo

`slime-erp` es un ERP nuevo, más simple, que arranca reutilizando dos partes
puntuales del proyecto anterior (`C:\Users\ivana\Documents\slime-erp_old`):

1. El **sistema de diseño y shell visual** del frontend (Angular).
2. La **configuración multi-tenant** del backend (Spring Boot): autenticación
   JWT, entidades tenant/usuario, y autogestión de tenants.

Todo lo demás (ventas, compras, inventario, categorías, bodegas, formas de
pago, tesorería, reportería, **DTE/facturación electrónica**, super-admin)
queda fuera. Se diseñará y construirá como módulos nuevos más adelante, sobre
esta base.

## Alcance

### Incluido — Backend (`backend/`)

Proyecto Spring Boot 21 / Maven, paquete `cl.slimerp` (se mantiene el nombre
de paquete del proyecto original).

Copiar y adaptar:

- Scaffolding de build: `pom.xml`, `Dockerfile`, `application.yml`,
  `application-docker.yml`, `SlimErpApplication.java`.
- `config/`: `JwtAuthFilter`, `JwtService`, `SecurityConfig`, `TenantContext`,
  `GlobalExceptionHandler`.
  - **Excluido:** `UploadsConfig` (es para imágenes de producto; no aplica sin
    ese módulo todavía).
- `tenant/`: `Tenant`, `Usuario`, `Rol`, `TenantRepository`,
  `UsuarioRepository` — tal cual.
- `auth/`: `AuthController`, `LoginRequest`, `LoginResponse` — login JWT
  funcional completo.
- `admin/`: `EmpresaAdminController`, `EmpresaAdminService`,
  `CrearEmpresaRequest`, `EmpresaResponse`, `EmpresaConflictException` —
  autogestión de altas/activación de tenants.
- Catálogo básico (paquete nuevo, p. ej. `catalogo/`, en vez de mezclarlo en
  `common/` con todo lo demás que no traemos):
  - `Cliente` (entity + controller + repository + `ClienteRequest`).
  - `Producto` (entity + controller + repository).
  - Alcance de campos: los definidos en `V1__init.sql` original (nombre, rut,
    contacto para cliente; sku, nombre, descripción, precios, stock,
    controla_stock para producto). Sin categoría, subcategoría, bodega,
    imagen ni stock mínimo — esos son de módulos posteriores no incluidos.
- Migraciones Flyway: una única `V1__init.sql` nueva, recortada a las tablas
  `tenant`, `usuario`, `cliente`, `producto`, con el mismo seed de desarrollo
  (tenant demo + usuario `admin@demo.cl` / `admin123`).

Explícitamente excluido del backend: `common/` (Banco, Categoria,
Subcategoria, Proveedor, FormaPago, ImagenStorageService, TipoProducto),
`compras/`, `ventas/`, `inventario/`, `reporteria/`, `tesoreria/`, **`dte/`
completo (nada de XML, CAF, certificados ni facturación electrónica)**.

### Incluido — Frontend (`frontend/`)

Proyecto Angular 18 + Tailwind (solo como utilidad de layout, no como sistema
visual — así está configurado en `tailwind.config.js` original).

Copiar y adaptar:

- Scaffolding de build: `angular.json`, `tsconfig*.json`, `package.json`
  (sin `jspdf` / `jspdf-autotable`, que solo se usan para PDFs de venta/DTE),
  `tailwind.config.js`, `Dockerfile`, `nginx.conf`, `.editorconfig`,
  `src/index.html`, `src/main.ts`, `src/app/app.config.ts`,
  `src/app/app.component.*`, `src/environments/*`.
- `src/styles.scss`, `src/styles/_components.scss`,
  `src/styles/tokens/*.css` — sistema de diseño completo, sin cambios.
- `src/app/layout/` — shell (topbar + sidebar colapsable), con el menú
  lateral recortado a solo **Dashboard** (se quitan los grupos de
  ventas/compras/tesorería/facturación/etc. del original). Branding
  actualizado de "Slim ERP" a "Slime ERP".
- `src/app/shared/modal/` — tal cual.
- `src/app/features/login/` — HTML/SCSS tal cual. El `.ts` se simplifica a un
  formulario reactivo visual, **sin** llamada real a `AuthService` (no se
  trae `core/services`, `core/guards` ni `core/interceptors` en esta etapa).
- `src/app/features/dashboard/` — HTML/SCSS del layout de tarjetas y gráfico
  de barras, con datos estáticos de ejemplo en el `.ts` en vez de
  `DashboardService` (que depende de ventas, fuera de alcance).
- `src/app/app.routes.ts` — recortado a `login` y `dashboard` únicamente, sin
  guards (`authGuard`, `tenantGuard`, `superAdminGuard` no se copian todavía).

Explícitamente excluido del frontend: `core/guards`, `core/interceptors`,
`core/services`, `core/models`, y todos los `features/*` de negocio
(clientes, proveedores, productos, categorías, bodegas, formas-pago, ventas,
compras, movimientos, reporteria, tesoreria, admin, **facturacion/DTE
completo**).

### Explícitamente fuera de alcance (todo el proyecto)

Proveedores, ventas, compras, inventario, categorías, bodegas, formas de
pago, tesorería, reportería, super-admin (rutas/guard), y **cualquier cosa
relacionada a DTE / facturación electrónica / XML / CAF / certificados**,
tanto en frontend como en backend. No se copia el paquete `dte/` del backend
ni `features/facturacion` ni las rutas `admin/empresas/:id/{emisor,
certificado, caf}` del frontend.

## Desconexión frontend/backend en esta etapa

El backend queda con autenticación JWT completamente funcional. El frontend,
por decisión explícita, **no** queda conectado a esa autenticación todavía
(login es solo visual). Esto es intencional: primero se establece la base
visual y la base de tenant por separado; el cableado (guards, interceptor,
auth.service real) se hace en una iteración posterior cuando se retome el
desarrollo del frontend funcional.

## Estructura resultante

```
slime-erp/
├── .env / .env.example        (ya existen, sin cambios)
├── docker-compose.yml          (nuevo, adaptado del original)
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
│       │   └── catalogo/      (Cliente, Producto)
│       └── resources/
│           ├── application.yml
│           ├── application-docker.yml
│           └── db/migration/V1__init.sql
└── frontend/
    ├── angular.json, package.json, tailwind.config.js, tsconfig*.json
    ├── Dockerfile, nginx.conf
    └── src/
        ├── index.html, main.ts
        ├── styles.scss, styles/_components.scss, styles/tokens/*.css
        └── app/
            ├── app.component.*, app.config.ts, app.routes.ts
            ├── layout/
            ├── shared/modal/
            └── features/{login, dashboard}/
```

## Testing / verificación

- Backend: `mvn -q -pl backend compile` (o `mvn spring-boot:run`) para
  confirmar que compila y Flyway aplica `V1__init.sql` sin errores.
- Frontend: `npm install && ng build` dentro de `frontend/` para confirmar
  que compila sin las dependencias/rutas removidas.
- No se agregan tests automatizados nuevos en este scaffold — es una copia
  adaptada de código ya existente, sin lógica nueva propia todavía.

## Siguientes pasos (fuera de este spec)

- Cablear el frontend al backend (guards, interceptor JWT, auth.service
  real) cuando se retome el frontend funcional.
- Diseñar e implementar, uno a uno, los módulos de negocio excluidos
  (proveedores, ventas, compras, inventario, reportería, tesorería) y,
  eventualmente, un módulo de facturación electrónica propio — cuando se
  decida abordarlo, será su propio spec.
