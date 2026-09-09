# Auditoría Técnica y Validación Operativa del MVP ERP — Plan de Trabajo

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Nota:** Este plan no es un plan de implementación de features. Es un plan de **auditoría/investigación**: cada tarea produce un documento de hallazgos verificados (no código de producción), salvo el Task 0 donde puede requerirse una corrección mínima para poder levantar el entorno (permitido explícitamente por la Regla 3 del documento fuente). No modificar funcionalidad existente durante este plan salvo lo estrictamente necesario para destrabar el entorno.

**Goal:** Determinar, con evidencia verificada (no supuestos), si slime-erp está realmente operativo como MVP de ERP, module por módulo, y entregar un informe final con matriz de estado, hallazgos clasificados por severidad y un plan de corrección priorizado en 5 fases.

**Architecture (real, verificada por inspección estática del repo):**
```
Angular 18 (frontend/src/app)
   ↓ HttpClient (environment.apiUrl = http://localhost:8080/api en dev)
Spring Boot 3.3.4 (backend/src/main/java/cl/slimerp)
   ↓ Spring Security + JWT (jjwt)
Services (por paquete: auth, catalogo, compras, inventario, tesoreria, ventas, reporteria, usuarios, admin, permisos, dashboard)
   ↓ Spring Data JPA
PostgreSQL 16 (docker-compose: servicio "db")
   ↑ Flyway (21 migraciones, V1__init.sql … V21__folio_venta.sql)
```
Multi-tenancy: existe paquete `cl.slimerp.tenant` con `Tenant`, `Usuario`, `Rol`, `TenantRepository`, `UsuarioRepository` — su uso real (filtro obligatorio por tenant en cada query) debe verificarse en Task 9, no asumirse.

**Tech Stack:** Angular 18 + Angular Material + RxJS (frontend), Spring Boot 3.3.4 + Spring Data JPA + Spring Security + JWT (jjwt) + Flyway + PostgreSQL 16 (backend), Docker Compose para orquestación local.

**Spec:** `docs/Auditoría técnica y validación operativa del MVP ERP.md` (documento fuente completo — 27 secciones + reglas importantes; este plan lo reorganiza en tareas ejecutables pero no reemplaza su contenido; ante cualquier duda de alcance, el documento fuente manda).

## Global Constraints

Reglas del documento fuente que aplican a **todas** las tareas de este plan (sección "REGLAS IMPORTANTES"):

- No asumir que algo funciona porque existe el código — todo debe verificarse en vivo cuando el entorno lo permita.
- Un mock (localStorage, arrays hardcodeados, `setTimeout` simulando latencia, datos estáticos) nunca se clasifica como funcionalidad OPERATIVA.
- Una pantalla terminada no implica un módulo terminado (ver definición OPERATIVO / PARCIAL / NO OPERATIVO / NO IMPLEMENTADO en Task 14).
- Toda funcionalidad debe verificarse de extremo a extremo: UI → API → Backend → DB, incluyendo persistencia real (el dato sobrevive a un reinicio/reconsulta).
- La autorización y el aislamiento multi-tenant deben verificarse en el **backend**, nunca asumir que ocultar un botón en el frontend es suficiente.
- No ocultar problemas para mejorar el score final; no refactorizar por gusto durante la auditoría; no eliminar funcionalidades existentes; no cambiar arquitectura sin justificarlo.
- Si algo no puede probarse por falta de configuración, se marca explícitamente **"NO VERIFICADO"** (nunca se reporta como "funciona" ni como "no funciona" sin evidencia).
- Si se encuentra un problema, buscar activamente otras ubicaciones donde pueda repetirse el mismo problema.
- Pensar como atacante al revisar multi-tenancy/autorización; como usuario real al probar flujos ERP; como DBA al revisar persistencia e integridad.

**Hallazgo previo (de la inspección de estructura hecha para armar este plan, aún NO verificado en vivo):** el backend no tiene ningún controlador `Caja*` ni `FlujoCaja*` (los paquetes backend son: `admin, auth, catalogo, compras, config, dashboard, inventario, permisos, reporteria, tenant, tesoreria, usuarios, ventas` — no hay `caja`). Sin embargo el frontend tiene dos módulos completos (`frontend/src/app/features/caja/` y `frontend/src/app/features/flujo-caja/`), cada uno con su propio `core/storage` (`cash-storage.service.ts`, `storage.service.ts`) y `caja` además tiene `core/seed`. Esto es una señal fuerte de módulos **SIMULADOS** (Frontend → datos locales, sin backend). Se prioriza su verificación en Task 2.

---

### Task 0: Preparar y verificar el entorno (bloqueador)

**Files:**
- Create: `docs/auditoria/00-entorno.md`
- Referencia: `docker-compose.yml`, `.env.example`, `backend/src/main/resources/application.yml`, `backend/src/main/resources/application-docker.yml`

**Interfaces:**
- Produces: confirmación de que existe un backend accesible en `http://localhost:8080/api` con JWT válido, una base PostgreSQL con las 21 migraciones aplicadas, y un frontend accesible en `http://localhost:4200`. Todas las tareas siguientes dependen de esto.

- [ ] **Paso 1: Verificar/crear `.env`**

Si no existe `.env` en la raíz, copiar `.env.example` a `.env` y completar `DB_PASSWORD` y `JWT_SECRET` con valores locales (no inventar secretos "de producción", solo valores de desarrollo). Registrar en el informe si `.env` ya existía o se creó, y qué variables tuvo que completar el auditor.

- [ ] **Paso 2: Levantar PostgreSQL**

```bash
docker compose up -d db
docker compose logs db --tail 30
```
Confirmar `pg_isready` OK. Si falla, registrar el error exacto en `docs/auditoria/00-entorno.md` — no inventar que funciona.

- [ ] **Paso 3: Levantar el backend y verificar migraciones Flyway**

```bash
docker compose up -d --build backend
docker compose logs backend --tail 100
```
Buscar en el log líneas `Flyway` confirmando que las migraciones `V1` a `V21` se aplicaron sin error. Confirmar que el proceso queda escuchando en `:8080` (buscar "Started" en el log de Spring Boot).

- [ ] **Paso 4: Probar el backend en vivo**

```bash
curl -i http://localhost:8080/api/auth/login -X POST -H "Content-Type: application/json" -d "{}"
```
Se espera un 400 (validación) o 401, no un error de conexión. Esto confirma que el backend responde.

- [ ] **Paso 5: Levantar el frontend**

```bash
docker compose up -d --build frontend
```
o en modo dev: `cd frontend && npm install && npm start` (más útil para ver errores de consola/TypeScript en vivo). Confirmar que carga en `http://localhost:4200` sin errores fatales de compilación.

- [ ] **Paso 6: Documentar resultado**

Completar `docs/auditoria/00-entorno.md` con: qué se pudo levantar, qué falló, qué quedó "NO VERIFICADO" y por qué, y cualquier corrección mínima aplicada para destrabar el entorno (con el diff exacto, según Regla 3 del documento fuente).

---

### Task 1: Mapear arquitectura real e inventariar módulos

**Files:**
- Create: `docs/auditoria/01-arquitectura-inventario.md`

**Interfaces:**
- Consumes: entorno levantado en Task 0 (opcional para este task, es mayormente estático).
- Produces: tabla de correspondencia módulo frontend ↔ paquete backend ↔ tablas DB, que las Tasks 2-6 usan como checklist de cobertura.

- [ ] **Paso 1: Confirmar inventario de módulos frontend**

```bash
ls frontend/src/app/features
```
Módulos ya identificados: `bodegas, caja, categorias, clientes, compras, dashboard, empresas, flujo-caja, formas-pago, login, movimientos, productos, proveedores, reportes, tesoreria, usuarios, ventas`.

- [ ] **Paso 2: Confirmar inventario de controladores backend y rutas**

Rutas ya relevadas (confirmar que siguen vigentes con `grep -rn "@RequestMapping\|@GetMapping\|@PostMapping\|@PutMapping\|@PatchMapping\|@DeleteMapping" backend/src/main/java/cl/slimerp`):

| Paquete backend | Controlador | Base path |
|---|---|---|
| admin | EmpresaAdminController | `/api/admin/empresas` |
| auth | AuthController | `/api/auth` |
| catalogo | CategoriaController | `/api/categorias` |
| catalogo | SubcategoriaController | `/api/subcategorias` |
| catalogo | ClienteController | `/api/clientes` |
| catalogo | ProveedorController | `/api/proveedores` |
| catalogo | ProductoController | `/api/productos` |
| catalogo | FormaPagoController | `/api/formas-pago` |
| compras | CompraController | `/api/compras` |
| dashboard | DashboardController | `/api/dashboard` |
| inventario | BodegaController | `/api/bodegas` |
| inventario | MovimientoInventarioController | `/api/movimientos` |
| inventario | StockController | `/api/stock` |
| reporteria | LibroVentasController | `/api/reportes` |
| tesoreria | CuentaPorCobrarController | `/api/tesoreria/cuentas` |
| tesoreria | TransaccionPagoController | `/api/tesoreria/pagos` |
| usuarios | UsuarioController | `/api/usuarios` |
| usuarios | UsuarioPermisoController | `/api/usuarios/{id}/permisos*` |
| ventas | VentaController | `/api/ventas` |

**No existe** ningún controlador para `caja` ni `flujo-caja`.

- [ ] **Paso 3: Cruzar módulos frontend sin contraparte backend**

Completar la tabla anterior agregando la columna "módulo frontend correspondiente" y marcar explícitamente cuáles módulos frontend **no tienen backend**: al día de hoy, `caja` y `flujo-caja`. Verificar si hay algún otro caso revisando cada carpeta de `frontend/src/app/features/*/core/services` en busca de servicios que llamen `HttpClient` vs. servicios que solo usan `localStorage`/arrays en memoria:

```bash
grep -rLn "HttpClient" frontend/src/app/features --include="*.service.ts"
```
(archivos que NO importan `HttpClient` en un `*.service.ts` son candidatos a mock/localStorage — cruzar con `grep -rl "localStorage" frontend/src/app/features`).

- [ ] **Paso 4: Inventariar tablas de base de datos**

```bash
ls backend/src/main/resources/db/migration | sort
```
Listar en el informe las 21 migraciones con una línea de resumen cada una (leer el nombre de archivo y, si el nombre no es suficiente, abrir el archivo). Marcar si existe o no una tabla relacionada a `caja`/`flujo_caja` — la hipótesis actual es que no existe.

- [ ] **Paso 5: Documentar el mapa de arquitectura**

Guardar en `docs/auditoria/01-arquitectura-inventario.md` el diagrama de arquitectura (adaptado del Task header de este plan) + la tabla completa de correspondencia frontend↔backend↔DB + la lista de módulos sin backend confirmada.

---

### Task 2: Auditar Caja y Flujo de Caja (prioridad alta — sospecha de simulación total)

**Files:**
- Create: `docs/auditoria/02-caja-flujo-caja.md`
- Inspeccionar: `frontend/src/app/features/caja/**`, `frontend/src/app/features/flujo-caja/**`

**Interfaces:**
- Consumes: tabla de Task 1 (confirmación de que no hay controlador backend `caja`/`flujo-caja`).
- Produces: clasificación REAL/PARCIAL/SIMULADO de ambos módulos para la matriz final (Task 14).

- [ ] **Paso 1: Confirmar el mecanismo de persistencia de `caja`**

Leer `frontend/src/app/features/caja/core/storage/cash-storage.service.ts` completo y `frontend/src/app/features/caja/core/seed/` (si existe contenido). Determinar si usa `localStorage`, `sessionStorage`, un array en memoria, o efectivamente llama a un endpoint HTTP. Documentar con cita exacta del código (archivo + línea).

- [ ] **Paso 2: Confirmar el mecanismo de persistencia de `flujo-caja`**

Mismo análisis sobre `frontend/src/app/features/flujo-caja/core/storage.service.ts`.

- [ ] **Paso 3: Probar en el navegador (con el entorno del Task 0 levantado)**

Abrir `http://localhost:4200`, navegar a Caja y a Flujo de Caja, crear un movimiento/apertura de caja, y luego:
1. Recargar la página (F5) y confirmar si el dato persiste.
2. Abrir la app en una ventana de incógnito / otro navegador con la misma sesión de usuario y confirmar si el dato aparece (si no aparece, es señal fuerte de `localStorage` local al navegador, no backend real).
3. Revisar la pestaña Network del navegador: confirmar si existe o no una petición HTTP hacia `/api/caja*` o `/api/flujo-caja*` al guardar.

- [ ] **Paso 4: Clasificar y documentar**

En `docs/auditoria/02-caja-flujo-caja.md`, usar la clasificación del documento fuente (REAL / PARCIAL / SIMULADO) para cada submódulo (apertura, movimientos, cierre, arqueo, entradas, salidas, historial, resumen en `caja`; dashboard, month, settings, audit en `flujo-caja`). Si se confirma SIMULADO, este es candidato a **problema CRÍTICO o ALTO** (afecta directamente la fiabilidad de un módulo financiero) — dejarlo anotado para Task 14.

---

### Task 3: Auditar la cadena transaccional Ventas → Inventario/Stock → Compras

**Files:**
- Create: `docs/auditoria/03-ventas-inventario-compras.md`

**Interfaces:**
- Consumes: JWT válido del Task 0 (login real) para llamar a los endpoints protegidos.
- Produces: veredicto sobre si crear una venta realmente descuenta stock, y si una compra realmente lo incrementa; input directo para Task 11 (transacciones) y Task 14 (matriz CRUD).

- [ ] **Paso 1: Revisar contrato frontend↔backend de Ventas**

Leer `frontend/src/app/features/ventas/**` (servicio HTTP) y compararlo campo a campo contra `VentaController` (`backend/src/main/java/cl/slimerp/ventas/VentaController.java`, endpoints `GET /api/ventas`, `GET /{id}`, `POST`, `GET /{id}/pdf`) y su DTO de entrada. Documentar cualquier discrepancia de nombres de propiedades, tipos o campos faltantes.

- [ ] **Paso 2: Probar el flujo real de venta con curl**

```bash
TOKEN=$(curl -s http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"usuario":"<usuario_valido>","password":"<password_valido>"}' | jq -r .token)

curl -i http://localhost:8080/api/stock -H "Authorization: Bearer $TOKEN"

curl -i http://localhost:8080/api/ventas -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"clienteId":1,"items":[{"productoId":1,"cantidad":1,"precio":1000}]}'

curl -i http://localhost:8080/api/stock -H "Authorization: Bearer $TOKEN"
```
(Ajustar el payload real al DTO encontrado en el Paso 1; usar credenciales de un usuario de prueba existente en el seed de datos, ver migraciones `V11`–`V13`). Confirmar si el segundo `GET /api/stock` refleja el descuento.

- [ ] **Paso 3: Probar el flujo real de compra**

Repetir el mismo patrón contra `CompraController` (`/api/compras`) y confirmar si el stock sube tras una compra creada vía `POST /api/compras`.

- [ ] **Paso 4: Revisar movimientos de inventario**

Confirmar en `MovimientoInventarioController` (`/api/movimientos`) que la venta y la compra generan efectivamente un registro de movimiento (`GET /api/movimientos` antes/después). Revisar también el endpoint de importación masiva (`POST /api/movimientos/importar`) — probar con un archivo válido e inválido y confirmar manejo de errores.

- [ ] **Paso 5: Documentar**

Completar `docs/auditoria/03-ventas-inventario-compras.md` con el resultado de cada prueba (PASS/FAIL/NO VERIFICADO), evidencia (request/response), y clasificación REAL/PARCIAL/SIMULADO para: Ventas, Compras, Movimientos de Inventario, Stock.

---

### Task 4: Auditar Catálogo (Clientes, Proveedores, Productos, Categorías/Subcategorías, Formas de Pago, Bodegas)

**Files:**
- Create: `docs/auditoria/04-catalogo.md`

**Interfaces:**
- Consumes: JWT del Task 0.
- Produces: veredicto CRUD real (Create → Read → Update → Delete, en ese orden, confirmando cada paso) para cada entidad de catálogo.

- [ ] **Paso 1: Ejecutar el ciclo CRUD completo por entidad vía curl**

Para cada uno de: `clientes` (`ClienteController`), `proveedores` (`ProveedorController`), `productos` (`ProductoController`), `categorias` (`CategoriaController`), `subcategorias` (`SubcategoriaController`), `formas-pago` (`FormaPagoController`), `bodegas` (`BodegaController`):

```bash
curl -i http://localhost:8080/api/<recurso> -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{...payload según DTO real...}'
curl -i http://localhost:8080/api/<recurso>       -H "Authorization: Bearer $TOKEN"   # confirmar que aparece el creado
curl -i http://localhost:8080/api/<recurso>/<id> -X PUT -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{...cambio...}'
curl -i http://localhost:8080/api/<recurso>/<id>       -H "Authorization: Bearer $TOKEN"   # confirmar el cambio
curl -i http://localhost:8080/api/<recurso>/<id> -X DELETE -H "Authorization: Bearer $TOKEN"
```
Nota: `ProductoController` no expone `DELETE` en la tabla relevada del Task 1 — confirmar leyendo el archivo si realmente falta o se omitió en el relevamiento; si falta, es un hallazgo de CRUD incompleto (Sección 23 del doc fuente).

- [ ] **Paso 2: Cruzar contra el frontend**

Para cada módulo, abrir el formulario correspondiente en `http://localhost:4200`, hacer la misma secuencia crear/editar/eliminar desde la UI, y confirmar en la pestaña Network que dispara exactamente los mismos endpoints (no localStorage, no arrays estáticos). Revisar además `frontend/src/app/features/<modulo>/**` en busca de `mock`, `dummy`, `TODO`, `FIXME`, datos hardcodeados:

```bash
grep -rniE "mock|dummy|fixture|hardcode|TODO|FIXME" frontend/src/app/features/clientes frontend/src/app/features/proveedores frontend/src/app/features/productos frontend/src/app/features/categorias frontend/src/app/features/bodegas frontend/src/app/features/formas-pago
```

- [ ] **Paso 3: Documentar**

Matriz PASS/FAIL/NO VERIFICADO por entidad y operación (Create/Read/Update/Delete) en `docs/auditoria/04-catalogo.md`, con clasificación REAL/PARCIAL/SIMULADO.

---

### Task 5: Auditar Empresas, Usuarios y Permisos

**Files:**
- Create: `docs/auditoria/05-empresas-usuarios-permisos.md`

**Interfaces:**
- Consumes: JWT del Task 0 (idealmente de un usuario `SUPER_ADMIN` o rol equivalente, para probar `EmpresaAdminController`).
- Produces: input directo para Task 8 (autorización/roles) y Task 9 (multi-tenancy) — este task solo confirma que el CRUD funciona, la verificación de restricción por rol/tenant se profundiza después.

- [ ] **Paso 1: Confirmar roles reales del sistema**

Leer `backend/src/main/java/cl/slimerp/tenant/Rol.java` y documentar exactamente los roles que define el enum/entidad (no asumir `SUPER_ADMIN/ADMIN/MANAGER/USER` del documento fuente — son solo un ejemplo).

- [ ] **Paso 2: Probar creación y activación/desactivación de empresa**

```bash
curl -i http://localhost:8080/api/admin/empresas -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{...}'
curl -i http://localhost:8080/api/admin/empresas/<id>/activar -X PATCH -H "Authorization: Bearer $TOKEN"
curl -i http://localhost:8080/api/admin/empresas/<id>/desactivar -X PATCH -H "Authorization: Bearer $TOKEN"
```
Confirmar con `GET` (si existe) o consultando la DB directamente que el estado cambió.

- [ ] **Paso 3: Probar CRUD de usuarios y asignación de permisos extra**

```bash
curl -i http://localhost:8080/api/usuarios -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{...}'
curl -i http://localhost:8080/api/usuarios/<id>/permisos -H "Authorization: Bearer $TOKEN"
curl -i http://localhost:8080/api/usuarios/<id>/permisos-extra -X PUT -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{...}'
```
Confirmar que el permiso extra asignado efectivamente cambia el comportamiento de autorización de ese usuario en un endpoint protegido (probar un endpoint que antes le daba 403 y confirmar que pasa a 200, o viceversa).

- [ ] **Paso 4: Documentar**

`docs/auditoria/05-empresas-usuarios-permisos.md` con PASS/FAIL/NO VERIFICADO por operación y clasificación REAL/PARCIAL/SIMULADO.

---

### Task 6: Auditar Tesorería, Reportes (Libro de Ventas) y Dashboard

**Files:**
- Create: `docs/auditoria/06-tesoreria-reportes-dashboard.md`

**Interfaces:**
- Consumes: JWT del Task 0, datos de venta creados en Task 3 (para que el Libro de Ventas y el dashboard tengan algo que mostrar).

- [ ] **Paso 1: Probar Cuentas por Cobrar y Pagos**

```bash
curl -i http://localhost:8080/api/tesoreria/cuentas -H "Authorization: Bearer $TOKEN"
curl -i http://localhost:8080/api/tesoreria/cuentas/resumen -H "Authorization: Bearer $TOKEN"
curl -i http://localhost:8080/api/tesoreria/cuentas/venta/<ventaId> -H "Authorization: Bearer $TOKEN"
curl -i http://localhost:8080/api/tesoreria/cuentas/<cuentaId>/pagos -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{...}'
curl -i http://localhost:8080/api/tesoreria/pagos/<id>/anular -X POST -H "Authorization: Bearer $TOKEN"
```
Confirmar que registrar un pago realmente actualiza el saldo de la cuenta por cobrar (`GET /api/tesoreria/cuentas/<id>` antes/después), y que anular un pago revierte el efecto (ver Sección 18 del doc fuente — consistencia de negocio: "caja cerrada recibiendo movimientos", aplicar el mismo criterio a cuentas anuladas).

- [ ] **Paso 2: Probar Libro de Ventas (recién agregado según el historial de commits del repo)**

```bash
curl -i "http://localhost:8080/api/reportes/libro-ventas?desde=2026-01-01&hasta=2026-12-31" -H "Authorization: Bearer $TOKEN"
curl -i "http://localhost:8080/api/reportes/libro-ventas/excel?desde=2026-01-01&hasta=2026-12-31" -H "Authorization: Bearer $TOKEN" -o /tmp/libro-ventas.xlsx
```
Confirmar que los totales (neto afecto, neto exento) coinciden con las ventas creadas en Task 3, y que el Excel exportado abre correctamente y contiene los mismos datos que la vista.

- [ ] **Paso 3: Probar Dashboard**

```bash
curl -i http://localhost:8080/api/dashboard -H "Authorization: Bearer $TOKEN"
curl -i http://localhost:8080/api/dashboard/ventas-evolucion -H "Authorization: Bearer $TOKEN"
```
Confirmar que las cifras reflejan datos reales de la DB (cruzar contra una consulta SQL directa) y no valores de ejemplo hardcodeados.

- [ ] **Paso 4: Documentar**

`docs/auditoria/06-tesoreria-reportes-dashboard.md` con resultados y clasificación.

---

### Task 7: Auditar Autenticación

**Files:**
- Create: `docs/auditoria/07-autenticacion.md`
- Inspeccionar: `backend/src/main/java/cl/slimerp/auth/**`, `backend/src/main/java/cl/slimerp/config/**`, `frontend/src/app/core/services/auth.service.ts`, `frontend/src/app/core/guards/**`, `frontend/src/app/core/interceptors/**`

**Interfaces:**
- Consumes: entorno del Task 0.
- Produces: veredicto sobre login, expiración de token, protección de rutas — sección "10. SEGURIDAD" y "7. AUTENTICACIÓN" del informe final.

- [ ] **Paso 1: Revisar cómo se genera y valida el JWT**

Leer el código de emisión del token en `auth` package y la configuración de `JWT_SECRET`/expiración en `application.yml`. Documentar: algoritmo usado, tiempo de expiración configurado, si existe refresh token (según la estructura de paquetes relevada, no aparece un endpoint de refresh — confirmar leyendo `AuthController` completo, que solo expone `POST /api/auth/login`).

- [ ] **Paso 2: Probar Caso A — sin autenticación**

```bash
curl -i http://localhost:8080/api/clientes
```
Esperado: `401`. Documentar el código real obtenido.

- [ ] **Paso 3: Probar Caso B — autenticado, recurso permitido**

```bash
curl -i http://localhost:8080/api/clientes -H "Authorization: Bearer $TOKEN"
```
Esperado: `200`.

- [ ] **Paso 4: Probar token expirado/inválido**

```bash
curl -i http://localhost:8080/api/clientes -H "Authorization: Bearer token.invalido.aqui"
```
Esperado: `401`, no `500`. Si el tiempo lo permite, generar un token con expiración pasada (o esperar a que expire uno de corta duración si es configurable) y confirmar el mismo resultado.

- [ ] **Paso 5: Revisar dónde se almacena el token en frontend**

Leer `frontend/src/app/core/services/auth.service.ts` (ya sabemos que usa `localStorage` según el grep hecho al armar este plan — confirmar si guarda el token en texto plano ahí, lo cual es una práctica de riesgo XSS a documentar en la sección de seguridad, no necesariamente bloqueante para un MVP pero sí a registrar).

- [ ] **Paso 6: Probar logout**

Ejecutar logout desde la UI y confirmar que: (a) el token se elimina del storage, (b) una petición posterior con el token viejo copiado manualmente sigue siendo válida hasta su expiración natural (comportamiento esperado si no hay blacklist de tokens — documentarlo, no es necesariamente un bug sino una limitación a anotar) o es rechazada (si existe blacklist/invalidación server-side).

- [ ] **Paso 7: Documentar**

`docs/auditoria/07-autenticacion.md` cubriendo los 3 casos (A/B/C definidos en la sección 7 del doc fuente — el caso C, recurso sin permisos, se prueba en Task 8) más los pasos anteriores.

---

### Task 8: Auditar Autorización y Roles en el backend

**Files:**
- Create: `docs/auditoria/08-autorizacion-roles.md`

**Interfaces:**
- Consumes: roles reales confirmados en Task 5 Paso 1, JWTs de al menos dos usuarios con roles distintos.

- [ ] **Paso 1: Mapear qué endpoints deberían estar restringidos por rol**

Buscar anotaciones de seguridad en los controladores:

```bash
grep -rn "@PreAuthorize\|@Secured\|@RolesAllowed\|hasRole\|hasAuthority" backend/src/main/java/cl/slimerp
```
Documentar, controlador por controlador, si tiene o no una restricción explícita de rol/permiso a nivel de método, y compararlo contra lo que el frontend oculta/muestra según rol (buscar en `frontend/src/app/core/guards` y en los componentes qué botones se ocultan por rol).

- [ ] **Paso 2: Probar Caso C — autenticado sin permisos**

Con un token de un usuario de rol bajo (o sin el permiso extra correspondiente), intentar acceder a un endpoint identificado en el Paso 1 como restringido, por ejemplo `POST /api/admin/empresas` o `DELETE /api/usuarios/<id>`:

```bash
curl -i http://localhost:8080/api/admin/empresas -X POST -H "Authorization: Bearer $TOKEN_USUARIO_BAJO" -H "Content-Type: application/json" -d '{...}'
```
Esperado: `403`. Si devuelve `200`/`201`, es un **hallazgo CRÍTICO** — la restricción existe solo en frontend.

- [ ] **Paso 3: Repetir el Paso 2 para cada operación de escritura (POST/PUT/PATCH/DELETE) del inventario de Task 1**

No basta con probar un solo endpoint — repetir sistemáticamente contra cada controlador que el Paso 1 marcó como "debería estar restringido", en particular `EmpresaAdminController`, `UsuarioController`, `UsuarioPermisoController`, y las operaciones `DELETE` de catálogo.

- [ ] **Paso 4: Documentar**

`docs/auditoria/08-autorizacion-roles.md`: tabla rol × endpoint × resultado esperado × resultado real × severidad si difiere.

---

### Task 9: Auditar Multi-tenancy (aislamiento entre empresas) — CRÍTICO

**Files:**
- Create: `docs/auditoria/09-multitenancy.md`

**Interfaces:**
- Consumes: dos empresas (tenants) distintas con al menos un usuario cada una — si no existen dos tenants de prueba, crearlos en este task usando `EmpresaAdminController` (Task 5) antes de continuar.

- [ ] **Paso 1: Confirmar el mecanismo de identificación del tenant**

Leer cómo el backend obtiene el `tenant_id`/`empresa_id` del usuario autenticado: revisar el JWT (¿el claim incluye el tenant?), revisar si hay un filtro/interceptor Spring (`@Filter` de Hibernate, un `TenantContext`, un `OncePerRequestFilter`) que lo inyecte automáticamente en cada query, o si cada `Service`/`Repository` lo agrega manualmente. Documentar el mecanismo exacto encontrado con archivo + línea.

- [ ] **Paso 2: Confirmar que el filtro es obligatorio, no opcional**

Buscar puntos donde una consulta podría ejecutarse **sin** el filtro de tenant (por ejemplo, un `findById` genérico de Spring Data JPA que no valide el tenant del registro devuelto):

```bash
grep -rn "findById\|getReferenceById" backend/src/main/java/cl/slimerp --include="*.java" | grep -v "Repository.java"
```
Para cada resultado, confirmar si el `Service` que lo llama valida después que el `empresa_id`/`tenant_id` del registro obtenido coincide con el del usuario autenticado.

- [ ] **Paso 3: Prueba de aislamiento cruzado — GET**

Con Usuario A (Empresa A) y un ID de un recurso real de Empresa B (por ejemplo un cliente, un producto, una venta):

```bash
curl -i http://localhost:8080/api/clientes/<id_de_empresa_B> -H "Authorization: Bearer $TOKEN_USUARIO_A"
curl -i http://localhost:8080/api/ventas/<id_de_empresa_B>   -H "Authorization: Bearer $TOKEN_USUARIO_A"
curl -i http://localhost:8080/api/productos/<id_de_empresa_B> -H "Authorization: Bearer $TOKEN_USUARIO_A"
```
Esperado: `403` o `404`, nunca `200` con datos de la Empresa B.

- [ ] **Paso 4: Prueba de aislamiento cruzado — POST/PUT/PATCH/DELETE**

Repetir el mismo intento pero modificando/eliminando un recurso de Empresa B usando el token de Usuario A:

```bash
curl -i http://localhost:8080/api/clientes/<id_de_empresa_B> -X PUT -H "Authorization: Bearer $TOKEN_USUARIO_A" -H "Content-Type: application/json" -d '{...}'
curl -i http://localhost:8080/api/clientes/<id_de_empresa_B> -X DELETE -H "Authorization: Bearer $TOKEN_USUARIO_A"
```
Esta prueba es la más crítica del documento fuente (Sección 9: "No basta con comprobar solamente las consultas GET"). Si cualquiera de estas operaciones tiene éxito sobre datos de otra empresa, es un **hallazgo CRÍTICO de seguridad**.

- [ ] **Paso 5: Probar manipulación directa del tenant en el payload**

Si algún DTO de creación acepta un campo `empresaId`/`tenantId` desde el body (revisar los DTOs de `POST` relevados en Tasks 3-6), intentar crear un recurso enviando explícitamente el `empresaId` de la Empresa B mientras se está autenticado como Usuario A, y confirmar que el backend lo ignora/rechaza (usa el tenant del JWT, no el del body) en vez de aceptarlo (esto sería mass assignment sobre el tenant, también reportable en Task 12).

- [ ] **Paso 6: Documentar**

`docs/auditoria/09-multitenancy.md`: tabla completa Usuario A/Empresa A → recurso Empresa B, por método HTTP y por entidad, con resultado y severidad.

---

### Task 10: Auditar Base de Datos (esquema, integridad, migraciones)

**Files:**
- Create: `docs/auditoria/10-base-de-datos.md`

**Interfaces:**
- Consumes: PostgreSQL levantado en Task 0.

- [ ] **Paso 1: Listar tablas reales y comparar contra las migraciones**

```bash
docker compose exec db psql -U slim_erp -d slim_erp -c "\dt"
```
Comparar contra las 21 migraciones inventariadas en Task 1.

- [ ] **Paso 2: Revisar FKs, constraints e índices por tabla clave**

```bash
docker compose exec db psql -U slim_erp -d slim_erp -c "\d+ ventas"
docker compose exec db psql -U slim_erp -d slim_erp -c "\d+ productos"
docker compose exec db psql -U slim_erp -d slim_erp -c "\d+ clientes"
docker compose exec db psql -U slim_erp -d slim_erp -c "\d+ usuario"
```
(ajustar nombres reales de tabla según el `\dt` del Paso 1). Confirmar específicamente, para cada tabla que conceptualmente pertenece a un tenant, que existe una columna `empresa_id`/`tenant_id` NOT NULL con FK — esta es la contraparte de datos del hallazgo de Task 9. Buscar explícitamente los casos que advierte la Sección 10 del doc fuente: producto sin empresa, cliente sin empresa, venta sin empresa, usuario sin empresa, documento sin empresa.

- [ ] **Paso 3: Buscar índices faltantes evidentes**

```bash
docker compose exec db psql -U slim_erp -d slim_erp -c "SELECT tablename, indexname, indexdef FROM pg_indexes WHERE schemaname='public' ORDER BY tablename;"
```
Confirmar que las columnas de filtro más comunes (`empresa_id`, foreign keys usadas en joins frecuentes como `venta_id`, `producto_id`) tienen índice.

- [ ] **Paso 4: Revisar integridad referencial con datos reales**

Tras las pruebas de Tasks 3-6 que ya insertaron datos, correr consultas de verificación de huérfanos, por ejemplo:

```bash
docker compose exec db psql -U slim_erp -d slim_erp -c "SELECT COUNT(*) FROM detalle_venta d LEFT JOIN venta v ON d.venta_id = v.id WHERE v.id IS NULL;"
```
(ajustar nombres reales de tabla). El objetivo es confirmar que no puede quedar un detalle huérfano — si la query no puede ejecutarse porque la FK ya lo impide, eso en sí es una buena señal a documentar.

- [ ] **Paso 5: Documentar**

`docs/auditoria/10-base-de-datos.md` con el modelo, relaciones, integridad, multi-tenancy a nivel de esquema, índices, migraciones y problemas encontrados — mismo formato que la sección "11. BASE DE DATOS" del informe final.

---

### Task 11: Pruebas CRUD end-to-end, transacciones y consistencia de negocio

**Files:**
- Create: `docs/auditoria/11-crud-transacciones-consistencia.md`

**Interfaces:**
- Consumes: resultados de Tasks 2-6 (ya contienen las pruebas CRUD por módulo) — este task se enfoca específicamente en construir la **matriz consolidada** y en las pruebas de **transaccionalidad** y **errores** que no se cubrieron módulo por módulo.

- [ ] **Paso 1: Construir la matriz de pruebas end-to-end**

Consolidar en una sola tabla (formato Sección 13 del doc fuente: Módulo | Acción | Frontend | API | Backend | DB | Resultado) todos los resultados de Tasks 2-6.

- [ ] **Paso 2: Probar transaccionalidad de la venta**

Objetivo: confirmar que crear una venta (Task 3) es atómico — si falla la actualización de stock, la venta no debería quedar creada a medias. Forzar un fallo controlado, por ejemplo enviando un `productoId` inexistente en un ítem dentro de una venta con múltiples ítems válidos:

```bash
curl -i http://localhost:8080/api/ventas -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"clienteId":1,"items":[{"productoId":1,"cantidad":1,"precio":1000},{"productoId":999999,"cantidad":1,"precio":1000}]}'
```
Luego verificar con `GET /api/ventas` y una consulta SQL directa que NO quedó una venta parcial (sin su detalle, o con stock parcialmente descontado). Si queda un registro parcial, es un **hallazgo ALTO/CRÍTICO** de falta de transacción (Sección 17 del doc fuente).

- [ ] **Paso 3: Probar consistencia de negocio**

Probar explícitamente los casos de la Sección 18 del doc fuente que apliquen a los módulos existentes:
- Venta con cantidad negativa o precio negativo → debe rechazarse (400), no aceptarse silenciosamente.
- Venta con stock insuficiente → confirmar si el backend lo bloquea o permite stock negativo sin control.
- Detalle de venta sin producto válido → debe rechazarse.

- [ ] **Paso 4: Probar manejo de errores (Sección 14 del doc fuente)**

```bash
curl -i http://localhost:8080/api/clientes -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{}'
curl -i http://localhost:8080/api/clientes -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"nombre":123}'
curl -i http://localhost:8080/api/clientes/999999 -H "Authorization: Bearer $TOKEN"
```
Confirmar que faltan campos → 400 con mensaje claro (no 500), tipo incorrecto → 400, ID inexistente → 404 (no 500, no 200 con `null`).

- [ ] **Paso 5: Documentar**

`docs/auditoria/11-crud-transacciones-consistencia.md` con la matriz consolidada y los hallazgos de transaccionalidad/consistencia/errores.

---

### Task 12: Auditar seguridad básica

**Files:**
- Create: `docs/auditoria/12-seguridad.md`

**Interfaces:**
- Consumes: hallazgos de autenticación (Task 7), autorización (Task 8) y multi-tenancy (Task 9) — este task cubre lo que falta de la Sección 16 del doc fuente.

- [ ] **Paso 1: Buscar secretos en el código**

```bash
grep -rniE "password\s*=|secret\s*=|api[_-]?key\s*=" backend/src/main/resources frontend/src/environments --include="*.yml" --include="*.ts" --include="*.properties"
```
Confirmar que `JWT_SECRET` y las credenciales de DB vienen de variables de entorno (`.env`/`docker-compose.yml`) y no están hardcodeadas en `application.yml`.

- [ ] **Paso 2: Revisar configuración CORS**

```bash
grep -rn "CorsConfiguration\|@CrossOrigin\|allowedOrigins" backend/src/main/java/cl/slimerp/config
```
Confirmar que no permite `*` combinado con credenciales, y que la lista de orígenes permitidos es razonable para el entorno.

- [ ] **Paso 3: Revisar hashing de contraseñas**

```bash
grep -rn "PasswordEncoder\|BCrypt" backend/src/main/java/cl/slimerp
```
Confirmar que se usa `BCryptPasswordEncoder` (o equivalente) y no texto plano ni un hash débil (MD5/SHA1 sin salt).

- [ ] **Paso 4: Revisar mass assignment**

Para los DTOs de creación/edición usados en Tasks 3-6, confirmar que no exponen campos internos sensibles (`id`, `empresaId` cuando no corresponde, flags de rol/admin) que un cliente podría setear arbitrariamente. Esto conecta directamente con la regla de identificadores del proyecto (`CLAUDE.md`: el `id` nunca debe ser generado ni enviado por el frontend) — confirmar que efectivamente ningún DTO acepta `id` desde el cliente para creación.

- [ ] **Paso 5: Revisar SQL injection**

```bash
grep -rn "createNativeQuery\|@Query.*nativeQuery\s*=\s*true\|String.format.*SELECT\|\" + .*+ \"" backend/src/main/java/cl/slimerp
```
Para cada resultado, confirmar que usa parámetros bindeados (`:param`, `?`) y no concatenación de strings con input de usuario.

- [ ] **Paso 6: Revisar exposición de errores internos**

```bash
cat backend/src/main/java/cl/slimerp/config/GlobalExceptionHandler.java
```
Confirmar que las respuestas de error no filtran stack traces completos, rutas de archivo del servidor, ni detalles de la excepción de Hibernate/SQL al cliente.

- [ ] **Paso 7: Documentar**

`docs/auditoria/12-seguridad.md` siguiendo el formato de la Sección 16 del doc fuente.

---

### Task 13: Rendimiento básico, logging y configuración de deployment (revisión ligera)

**Files:**
- Create: `docs/auditoria/13-rendimiento-logging-deployment.md`

**Interfaces:**
- Consumes: nada nuevo — es una pasada liviana, no una prueba de carga (explícitamente fuera de alcance según Sección 19 del doc fuente).

- [ ] **Paso 1: Buscar consultas N+1 evidentes**

```bash
grep -rn "@OneToMany\|@ManyToMany" backend/src/main/java/cl/slimerp --include="*.java" -A2
```
Para cada relación encontrada, confirmar si usa `FetchType.LAZY` (default deseable) y si algún endpoint que devuelve listas de esa entidad podría disparar N+1 al serializar la relación completa.

- [ ] **Paso 2: Confirmar paginación en endpoints de listado**

De la tabla del Task 1, listar qué `GET` de listado tienen variante `/pagina` (ya se ve que `categorias`, `bodegas`, `clientes`, `productos`, `subcategorias`, `stock/inventario` sí la tienen) y cuáles **no** (por ejemplo `proveedores`, `compras`, `movimientos`, `formas-pago`, `usuarios` no muestran un endpoint `/pagina` en el relevamiento del Task 1 — confirmar leyendo el controlador si realmente falta). Ausencia de paginación en listados que pueden crecer mucho (movimientos, compras) es un hallazgo a registrar, no necesariamente bloqueante para el MVP.

- [ ] **Paso 3: Revisar logging**

```bash
grep -rn "logger\.\|log\.\|System.out.println" backend/src/main/java/cl/slimerp | grep -iE "password|token|secret" 
```
Confirmar que no se loguean passwords/tokens. Revisar el nivel de logging configurado en `application.yml` y si hay un patrón de log con request-id para debugging.

- [ ] **Paso 4: Revisar configuración de deployment**

Releer `docker-compose.yml`, `backend/Dockerfile`, `frontend/Dockerfile`, `frontend/nginx.conf`, `application-docker.yml`. Confirmar que el build de producción del frontend (`environment.ts` con `apiUrl: '/api'`) efectivamente encaja con la configuración de proxy/nginx (`frontend/nginx.conf`) para que `/api` llegue al backend.

- [ ] **Paso 5: Documentar**

`docs/auditoria/13-rendimiento-logging-deployment.md`, marcando qué problemas son reales para un MVP y cuáles pueden esperar (criterio explícito de la Sección 19 del doc fuente).

---

### Task 14: Consolidar el informe final (14 secciones + plan de corrección + veredicto)

**Files:**
- Create: `docs/auditoria/00-informe-final.md`

**Interfaces:**
- Consumes: **todos** los documentos `docs/auditoria/00-*.md` a `13-*.md` producidos en las tareas anteriores. Esta es la única tarea que depende de que todas las anteriores estén terminadas.

- [ ] **Paso 1: Redactar las 14 secciones exigidas por el documento fuente (Sección 27)**

En `docs/auditoria/00-informe-final.md`, en este orden exacto:
1. Resumen Ejecutivo (Estado NO LISTO/PARCIAL/LISTO, madurez %, conteo de problemas por severidad).
2. Arquitectura Actual (tomar del Task 1, ya verificada, no solo el diagrama teórico).
3. Matriz de Funcionalidades (tabla de la Sección 25 del doc fuente: Frontend, Backend, PostgreSQL, Auth, Roles, Multi-tenant, Empresas, Usuarios, Clientes, Proveedores, Productos, Inventario, Ventas, Caja, Bancos/Tesorería, DTE, Dashboard — completar Estado/Severidad/Observaciones con los datos reales de Tasks 2-6, 10).
4. Problemas Críticos (formato Problema/Ubicación/Impacto/Cómo reproducir/Causa probable/Solución recomendada — extraídos de Tasks 2, 8, 9, 11, 12 principalmente).
5. Problemas Altos.
6. Problemas Medios.
7. Problemas Bajos / Deuda Técnica.
8. Funcionalidades Mock (lista exhaustiva — mínimo Caja y Flujo de Caja si Task 2 lo confirma, más cualquier otro caso que haya salido del grep de `localStorage`/mocks en Task 4).
9. Flujos End-to-End (checklist ✓/✗ por flujo completo probado en Tasks 3-6).
10. Seguridad (resumen ejecutivo de Tasks 7, 8, 9, 12: Autenticación/Autorización/Multi-tenancy/Protección de endpoints/Protección de datos/Riesgos encontrados).
11. Base de Datos (resumen de Task 10).
12. Checklist MVP (formato `[✓]/[~]/[✗]/[ ]` por funcionalidad, consistente con la Matriz de Funcionalidades).
13. Plan de Corrección, en las 5 fases exactas del doc fuente:
    - FASE 1 — BLOQUEADORES (lo que impide cualquier prueba real; normalmente ya resuelto en Task 0, pero documentar si algo quedó bloqueando otras áreas).
    - FASE 2 — CORE ERP (Ventas/Inventario/Compras/Caja si aplica).
    - FASE 3 — SEGURIDAD (hallazgos de Tasks 8, 9, 12).
    - FASE 4 — CALIDAD (validaciones, errores, consistencia de Task 11).
    - FASE 5 — HARDENING (Task 13).

- [ ] **Paso 2: Calcular el score de madurez del MVP**

Aplicar el criterio de la Sección 26 del doc fuente (0% simulado, 25-75% parcial, 100% operativo, por funcionalidad) sobre la Matriz de Funcionalidades del Paso 1, y explicar el cálculo (no dar un número sin mostrar cómo se llegó a él).

- [ ] **Paso 3: Redactar el veredicto final (Sección 14 duplicada / "VEREDICTO FINAL" al cierre del doc fuente)**

```text
¿EL SISTEMA ESTÁ LISTO PARA MVP?
SÍ / NO / PARCIALMENTE

Madurez estimada: __%
Cantidad de módulos operativos: __/__
Bloqueadores: __
Riesgo principal: __
Siguiente acción recomendada: __
```

- [ ] **Paso 4: Revisión de consistencia**

Releer las 14 secciones y confirmar que ningún hallazgo CRÍTICO de Tasks 2, 8, 9 o 12 quedó fuera de "4. Problemas Críticos" y de la "Fase 3 — Seguridad" del plan de corrección. Confirmar que todo lo marcado "NO VERIFICADO" en cualquier task anterior se refleja como tal (no como PASS ni FAIL) en la Matriz de Funcionalidades.

---

## Cobertura del documento fuente

Verificación de que cada sección de `docs/Auditoría técnica y validación operativa del MVP ERP.md` tiene una tarea que la cubre:

| Sección del doc fuente | Task de este plan |
|---|---|
| 1-2 Objetivo, Revisión inicial | Task 1 |
| 3 No modificar inmediatamente | Constraint global + nota del Task 0 |
| 4 Levantar el sistema | Task 0 |
| 5 Frontend↔Backend | Tasks 2-6 |
| 6 Detectar mocks | Tasks 2, 4 (grep sistemático) |
| 7 Autenticación | Task 7 |
| 8 Autorización y roles | Task 8 |
| 9 Multi-tenancy | Task 9 |
| 10 Base de datos | Task 10 |
| 11 CRUD real | Tasks 3-6, consolidado en Task 11 |
| 12 Flujos ERP principales | Tasks 2-6 |
| 13 Pruebas end-to-end | Task 11 Paso 1 |
| 14 Pruebas de errores | Task 11 Paso 4 |
| 15 Validación de datos | Tasks 3-6 (dentro de cada ciclo CRUD) + Task 11 Paso 3-4 |
| 16 Seguridad básica | Task 12 |
| 17 Transacciones | Task 11 Paso 2 |
| 18 Consistencia de negocio | Task 11 Paso 3 |
| 19 Rendimiento básico | Task 13 |
| 20 Logging y observabilidad | Task 13 |
| 21 Configuración y deployment | Task 13 |
| 22-24 Clasificación, faltantes, definición operativo | Aplicado transversalmente en cada Task, consolidado en Task 14 |
| 25 Matriz final | Task 14 |
| 26 Score MVP | Task 14 |
| 27 Resultado final (14 secciones) | Task 14 |
| Veredicto final | Task 14 |

## Orden de ejecución y dependencias

```
Task 0 (bloqueador)
   ↓
Task 1
   ↓
Tasks 2, 3, 4, 5, 6  →  pueden paralelizarse entre sí (no comparten estado, cada una prueba módulos distintos)
   ↓
Task 7, 8  →  pueden paralelizarse entre sí, dependen de Task 5 (roles) y Task 0
   ↓
Task 9  →  depende de tener al menos 2 tenants (Task 5) y del entendimiento de Task 8
Task 10 →  puede correr en paralelo con 7/8/9, depende solo de Task 0 y de que Tasks 2-6 ya hayan insertado datos de prueba
   ↓
Task 11 →  depende de que 2-6 estén terminadas (consolida su matriz) y usa hallazgos de transacciones
Task 12 →  depende de 7, 8, 9 (usa sus hallazgos) y de Task 0
Task 13 →  independiente, puede correr en cualquier momento después de Task 1
   ↓
Task 14 (consolidación final) →  depende de TODAS las anteriores
```

Dado que varias tareas son independientes entre sí (Tasks 2-6, y luego 7/8 junto con 10 y 13), esto es un buen candidato para `superpowers:dispatching-parallel-agents` en los tramos paralelos, ejecutando Task 14 solo al final cuando todo lo demás esté completo y revisado.
