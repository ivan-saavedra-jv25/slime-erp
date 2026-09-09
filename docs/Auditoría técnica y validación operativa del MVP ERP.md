# AUDITORÍA TÉCNICA Y VALIDACIÓN OPERATIVA — MVP ERP

## Rol

Actúa como **Senior Software Architect + Backend Engineer + Frontend Engineer + QA Engineer + Security Reviewer**, especializado en sistemas ERP SaaS multi-tenant.

Tu objetivo es **auditar el proyecto completo**, incluyendo frontend, backend, base de datos, autenticación, autorización, APIs e integración entre componentes, para determinar si el sistema está realmente operativo para funcionar como un **MVP de ERP**.

No quiero una revisión superficial del código.

Debes comprobar que las funcionalidades estén realmente conectadas y funcionando de extremo a extremo.

---

# 1. OBJETIVO PRINCIPAL

Determina si el sistema está en condiciones de ser utilizado como MVP funcional.

Debes responder principalmente:

1. ¿El frontend realmente se comunica correctamente con el backend?
2. ¿El backend realmente ejecuta las operaciones solicitadas?
3. ¿La base de datos está correctamente estructurada y utilizada?
4. ¿Las operaciones CRUD funcionan realmente?
5. ¿La autenticación funciona?
6. ¿La autorización y los roles funcionan?
7. ¿El aislamiento entre empresas/tenants funciona?
8. ¿Los datos persistidos sobreviven a un reinicio?
9. ¿Existen funcionalidades simuladas con datos mock?
10. ¿Existen endpoints que parecen funcionar pero realmente no persisten información?
11. ¿Existen errores de integración entre frontend y backend?
12. ¿El sistema puede ejecutar correctamente los flujos básicos de un ERP?

No asumas que algo funciona simplemente porque existe el código.

**Debes verificarlo.**

---

# 2. REVISIÓN INICIAL DEL PROYECTO

Primero inspecciona toda la estructura del proyecto.

Identifica:

- frontend
- backend
- base de datos
- ORM
- migraciones
- módulos
- servicios
- controladores
- componentes
- guards
- middleware
- interceptores
- DTOs
- entidades/modelos
- configuración
- variables de entorno
- Docker
- scripts
- tests
- documentación

Genera inicialmente un mapa de arquitectura:

```text
Frontend
   ↓
API / HTTP
   ↓
Backend
   ↓
Services
   ↓
ORM
   ↓
PostgreSQL
```

Adapta este diagrama a la arquitectura real encontrada.

---

# 3. NO MODIFICAR INMEDIATAMENTE

Durante la primera etapa:

**NO realices modificaciones importantes.**

Primero:

1. inspecciona
2. ejecuta
3. prueba
4. identifica problemas
5. clasifica los problemas
6. genera el diagnóstico

Solamente después de completar la auditoría, puedes proponer correcciones.

Si detectas errores críticos que impiden continuar con las pruebas, corrige únicamente lo mínimo necesario para poder seguir validando, y registra dichas modificaciones.

---

# 4. LEVANTAR EL SISTEMA

Intenta ejecutar el sistema de la misma forma en que lo haría un desarrollador o usuario real.

Comprueba:

### Frontend

- instalación
- compilación
- ejecución
- errores de consola
- errores de TypeScript
- rutas
- guards
- interceptores
- llamadas HTTP
- manejo de errores

### Backend

- instalación
- compilación
- ejecución
- conexión a PostgreSQL
- migraciones
- errores de runtime
- endpoints
- middleware
- autenticación
- autorización

### Base de datos

Comprueba:

- conexión
- tablas
- relaciones
- constraints
- índices
- claves primarias
- claves foráneas
- migraciones
- datos iniciales
- integridad referencial

Si el entorno requiere variables de entorno que no están disponibles, no inventes valores sensibles.

Registra exactamente qué configuración falta.

---

# 5. AUDITORÍA FRONTEND ↔ BACKEND

Esta es una de las partes más importantes.

Para cada módulo del frontend, identifica:

- servicios utilizados
- endpoints llamados
- HTTP method
- payload
- headers
- autenticación
- respuesta esperada
- manejo de errores

Luego comprueba que coincida con el backend.

Ejemplo:

```text
Frontend
POST /api/products
{
  name,
  sku,
  price
}

Backend
POST /api/products
DTO:
{
  name,
  sku,
  price
}
```

Verifica que no existan problemas como:

- endpoint incorrecto
- método HTTP incorrecto
- nombres de propiedades diferentes
- tipos incompatibles
- DTO incompatible
- parámetros faltantes
- headers faltantes
- token no enviado
- token enviado incorrectamente
- respuesta con estructura diferente
- frontend esperando datos que el backend no devuelve
- frontend utilizando mocks
- frontend utilizando LocalStorage cuando debería usar API
- endpoints inexistentes
- errores 404
- errores 401
- errores 403
- errores 500

---

# 6. DETECTAR MOCKS Y FUNCIONALIDADES FALSAMENTE OPERATIVAS

Busca explícitamente:

```text
mock
dummy
fake
fixture
hardcoded
static data
sample data
TODO
FIXME
console.log
setTimeout
localStorage
sessionStorage
```

Determina si existen pantallas que muestran información aparentemente real pero que realmente utilizan:

- arrays locales
- JSON estático
- datos hardcodeados
- mocks
- LocalStorage
- datos generados en frontend

Clasifica cada caso:

### REAL

Frontend → API → Backend → DB

### PARCIAL

Frontend → API → Backend → pero falta persistencia/integración

### SIMULADO

Frontend → datos locales/mock

Esto es especialmente importante para determinar el estado real del MVP.

---

# 7. AUTENTICACIÓN

Audita completamente el sistema de autenticación.

Comprueba:

- login
- logout
- registro si existe
- generación de token
- expiración
- refresh token si existe
- almacenamiento del token
- envío del token
- protección de rutas
- protección de endpoints
- password hashing
- recuperación de contraseña si existe

Prueba como mínimo:

### Caso A

Usuario no autenticado intenta acceder a un recurso protegido.

Resultado esperado:

```text
401 Unauthorized
```

### Caso B

Usuario autenticado accede a un recurso permitido.

Resultado esperado:

```text
200 OK
```

### Caso C

Usuario autenticado intenta acceder a un recurso sin permisos.

Resultado esperado:

```text
403 Forbidden
```

---

# 8. AUTORIZACIÓN Y ROLES

Identifica los roles existentes.

Por ejemplo:

```text
SUPER_ADMIN
ADMIN
MANAGER
USER
```

No asumas estos roles si el proyecto utiliza otros.

Determina:

- qué puede hacer cada rol
- qué endpoints puede utilizar
- qué módulos puede visualizar
- qué operaciones puede ejecutar

Comprueba que las restricciones existan realmente en backend.

**No debe confiarse exclusivamente en ocultar botones del frontend.**

---

# 9. MULTI-TENANCY

Esta sección es CRÍTICA para un ERP SaaS.

Determina cómo está implementado el aislamiento entre empresas.

Investiga si existe:

```text
tenant_id
company_id
organization_id
schema
database
```

o algún otro mecanismo.

Determina:

- cómo se identifica el tenant
- cómo se obtiene el tenant del usuario autenticado
- cómo se filtran los registros
- si el backend obliga el filtro
- si el frontend puede modificar el tenant
- si un usuario puede consultar datos de otra empresa
- si un ID de otra empresa puede ser enviado manualmente

Realiza pruebas de aislamiento.

Ejemplo conceptual:

```text
Empresa A
Usuario A

Empresa B
Usuario B
```

Comprueba que:

```text
Usuario A → datos Empresa A = permitido

Usuario A → datos Empresa B = DENEGADO
```

Prueba especialmente:

- GET
- POST
- PUT
- PATCH
- DELETE

No basta con comprobar solamente las consultas GET.

---

# 10. BASE DE DATOS

Audita las entidades/tablas relacionadas con el ERP.

Comprueba:

- normalización razonable
- relaciones
- foreign keys
- índices
- unique constraints
- nullability
- timestamps
- soft delete si existe
- auditoría
- tenant/company isolation
- integridad referencial

Busca problemas como:

```text
producto sin empresa
cliente sin empresa
venta sin empresa
usuario sin empresa
documento sin empresa
```

cuando conceptualmente deberían pertenecer a un tenant.

También identifica:

- tablas sin uso
- entidades sin endpoints
- columnas que nunca se utilizan
- relaciones incompletas
- campos duplicados
- campos obligatorios que pueden quedar NULL
- ausencia de índices en consultas importantes

---

# 11. CRUD REAL

Para cada entidad importante determina si existe:

```text
CREATE
READ
UPDATE
DELETE
```

No basta con que exista un endpoint.

Debes verificar el flujo:

```text
Frontend
   ↓
POST
   ↓
Backend
   ↓
Service
   ↓
ORM
   ↓
PostgreSQL
```

Después:

```text
GET
```

y confirmar que el registro creado realmente aparece.

Después:

```text
PUT/PATCH
```

y confirmar que cambia.

Después:

```text
DELETE
```

y confirmar el comportamiento esperado.

---

# 12. FLUJOS ERP PRINCIPALES

Determina qué módulos ERP existen realmente en el proyecto.

Como mínimo revisa, si están implementados:

### Empresa

- creación
- configuración
- datos básicos

### Usuarios

- creación
- asignación a empresa
- roles
- permisos

### Clientes

- creación
- edición
- consulta
- eliminación

### Proveedores

- creación
- edición
- consulta

### Productos

- creación
- edición
- stock
- precios

### Inventario

- entradas
- salidas
- ajustes
- stock actual

### Ventas

- creación
- detalle
- totales
- cliente
- productos
- cantidades

### Caja

- apertura
- movimientos
- cierre
- saldo

### Bancos / cuentas

Si existe el módulo:

- cuentas
- movimientos
- ingresos
- egresos
- saldo

### Documentos tributarios

Si existe:

- boleta
- factura
- nota de crédito
- nota de débito
- guía de despacho

No asumas que un módulo está listo simplemente porque existe una pantalla.

---

# 13. PRUEBAS END-TO-END

Construye una matriz de pruebas.

Ejemplo:

| Módulo | Acción | Frontend | API | Backend | DB | Resultado |
|---|---|---|---|---|---|---|
| Clientes | Crear | OK | OK | OK | OK | PASS |
| Clientes | Editar | OK | OK | ERROR | — | FAIL |
| Productos | Crear | MOCK | — | — | — | FAIL |
| Ventas | Crear | OK | OK | OK | OK | PASS |

Realiza pruebas reales siempre que el entorno lo permita.

---

# 14. PRUEBAS DE ERRORES

Comprueba qué sucede cuando:

- falta un campo
- se envía un tipo incorrecto
- se envía un ID inexistente
- se utiliza un ID de otro tenant
- no existe autenticación
- no existen permisos
- existe un registro duplicado
- falla PostgreSQL
- el backend devuelve 500
- la API devuelve 401
- la API devuelve 403
- la API devuelve 404

El sistema debe manejar los errores correctamente.

---

# 15. VALIDACIÓN DE DATOS

Revisa:

- DTO validation
- formularios
- required fields
- tipos
- números
- fechas
- emails
- RUT si corresponde
- precios
- cantidades
- stock
- estados

Busca inconsistencias entre:

```text
Frontend validation
        ↓
Backend validation
        ↓
Database constraints
```

La validación crítica debe existir en backend, no solamente en frontend.

---

# 16. SEGURIDAD BÁSICA

Realiza una revisión de seguridad enfocada en MVP.

Busca:

- secretos en código
- passwords almacenados en texto plano
- JWT mal configurado
- endpoints sin protección
- autorización solamente en frontend
- acceso entre tenants
- SQL injection
- manipulación de IDs
- mass assignment
- datos sensibles expuestos
- CORS excesivamente permisivo
- errores que revelen información interna
- variables de entorno expuestas al frontend

No realices ataques destructivos.

---

# 17. TRANSACCIONES

Identifica operaciones que deberían ser transaccionales.

Ejemplo de venta:

```text
Crear venta
↓
Crear detalle
↓
Actualizar stock
↓
Registrar movimiento
↓
Registrar caja
```

Determina si una operación puede quedar parcialmente ejecutada.

Por ejemplo:

```text
Venta creada
✓

Detalle creado
✓

Stock actualizado
✗
```

Esto debería manejarse mediante una transacción cuando corresponda.

---

# 18. CONSISTENCIA DE NEGOCIO

Busca errores como:

- stock negativo sin autorización
- venta sin detalle
- detalle sin producto
- venta con total incorrecto
- precio negativo
- cantidad negativa
- caja cerrada recibiendo movimientos
- documento asociado a empresa incorrecta
- cliente de otro tenant
- usuario modificando datos que no le corresponden

Clasifica estos problemas según severidad.

---

# 19. RENDIMIENTO BÁSICO

No hagas una prueba de carga exhaustiva.

Realiza una revisión inicial para detectar:

- consultas N+1
- endpoints que devuelven demasiados datos
- ausencia de paginación
- consultas sin filtros
- índices faltantes evidentes
- operaciones innecesariamente pesadas

Para un MVP, indica cuáles son problemas reales y cuáles pueden esperar.

---

# 20. LOGGING Y OBSERVABILIDAD

Comprueba:

- logs del backend
- errores
- excepciones
- identificación de requests
- información suficiente para debugging

Evita que los logs expongan:

- passwords
- tokens
- información sensible

---

# 21. CONFIGURACIÓN Y DEPLOYMENT

Revisa:

- `.env`
- configuración de producción
- CORS
- URLs del frontend
- URLs del backend
- conexión PostgreSQL
- migraciones
- build de frontend
- build de backend
- Docker si existe
- scripts de deployment

Determina si el proyecto podría desplegarse razonablemente.

---

# 22. CLASIFICACIÓN DE PROBLEMAS

Cada problema encontrado debe clasificarse:

### 🔴 CRÍTICO

Impide utilizar el sistema o compromete seguridad/datos.

Ejemplos:

- no existe autenticación real
- usuarios pueden acceder a otra empresa
- ventas no se guardan
- base de datos no funciona
- backend no inicia

### 🟠 ALTO

Una funcionalidad importante no funciona correctamente.

Ejemplos:

- CRUD incompleto
- stock incorrecto
- errores importantes de autorización
- flujo de ventas incompleto

### 🟡 MEDIO

Problema que no impide el MVP pero debe corregirse.

### 🔵 BAJO

Mejora técnica, UX, refactor o deuda técnica.

---

# 23. DETECTAR FUNCIONALIDADES FALTANTES

No solamente busques errores.

Determina también qué debería existir para considerar que un módulo está realmente operativo.

Por ejemplo:

```text
Productos
✓ Crear
✓ Listar
✓ Editar
✗ Eliminar
✗ Control de stock
```

Resultado:

```text
Producto = PARCIAL
```

---

# 24. DEFINICIÓN DE "OPERATIVO"

Utiliza esta clasificación:

### OPERATIVO

La funcionalidad funciona de extremo a extremo:

```text
UI
→ API
→ Backend
→ DB
```

y los datos persisten correctamente.

### PARCIALMENTE OPERATIVO

Existe la funcionalidad, pero tiene alguna dependencia incompleta o comportamiento limitado.

### NO OPERATIVO

La funcionalidad está rota, simulada o incompleta.

### NO IMPLEMENTADO

No existe realmente.

---

# 25. MATRIZ FINAL DEL MVP

Genera una tabla similar a:

| Área | Estado | Severidad | Observaciones |
|---|---|---|---|
| Frontend | | | |
| Backend | | | |
| PostgreSQL | | | |
| Auth | | | |
| Roles | | | |
| Multi-tenant | | | |
| Empresas | | | |
| Usuarios | | | |
| Clientes | | | |
| Proveedores | | | |
| Productos | | | |
| Inventario | | | |
| Ventas | | | |
| Caja | | | |
| Bancos | | | |
| DTE | | | |
| Dashboard | | | |

---

# 26. SCORE DEL MVP

Calcula un porcentaje aproximado de madurez.

Utiliza:

```text
0–30%  → No funcional como MVP
31–50% → Prototipo avanzado
51–70% → MVP parcialmente funcional
71–85% → MVP funcional con deuda técnica
86–100% → MVP sólido
```

No otorgues puntos solamente porque existe código.

Una funcionalidad simulada debe recibir:

```text
0%
```

Una funcionalidad parcialmente conectada:

```text
25–75%
```

Una funcionalidad completamente operativa:

```text
100%
```

Explica cómo calculaste el porcentaje.

---

# 27. RESULTADO FINAL

Al finalizar entrega exactamente estas secciones:

## 1. RESUMEN EJECUTIVO

Explica en pocas líneas si el sistema está realmente listo para funcionar como MVP.

Ejemplo:

```text
Estado: NO LISTO

Madurez estimada: 62%

Problemas críticos: 2
Problemas altos: 5
Problemas medios: 8
Problemas bajos: 12
```

---

## 2. ARQUITECTURA ACTUAL

Explica cómo está construido realmente el sistema.

---

## 3. MATRIZ DE FUNCIONALIDADES

Tabla completa de módulos y estado.

---

## 4. PROBLEMAS CRÍTICOS

Lista detallada.

Para cada uno:

```text
Problema:
Ubicación:
Impacto:
Cómo reproducir:
Causa probable:
Solución recomendada:
```

---

## 5. PROBLEMAS ALTOS

Mismo formato.

---

## 6. PROBLEMAS MEDIOS

Lista resumida pero precisa.

---

## 7. PROBLEMAS BAJOS / DEUDA TÉCNICA

Lista resumida.

---

## 8. FUNCIONALIDADES MOCK

Lista absolutamente todas las funcionalidades que actualmente utilizan:

- mock
- hardcode
- LocalStorage
- datos temporales
- datos estáticos

---

## 9. FLUJOS END-TO-END

Indica qué flujos completos funcionan.

Por ejemplo:

```text
Login
✓

Crear cliente
✓

Crear producto
✓

Crear venta
✗

Actualizar stock
✗
```

---

## 10. SEGURIDAD

Indica específicamente:

```text
Autenticación:
Autorización:
Multi-tenancy:
Protección de endpoints:
Protección de datos:
Riesgos encontrados:
```

---

## 11. BASE DE DATOS

Evalúa:

```text
Modelo:
Relaciones:
Integridad:
Multi-tenancy:
Índices:
Migraciones:
Problemas:
```

---

## 12. CHECKLIST MVP

Utiliza:

```text
[✓] Funcionalidad comprobada
[~] Funcionalidad parcial
[✗] Funcionalidad rota
[ ] No implementada
```

---

## 13. PLAN DE CORRECCIÓN

Genera un plan priorizado:

### FASE 1 — BLOQUEADORES

Problemas que deben solucionarse antes de cualquier prueba real.

### FASE 2 — CORE ERP

Problemas que afectan los flujos principales.

### FASE 3 — SEGURIDAD

Problemas de autenticación, autorización y multi-tenancy.

### FASE 4 — CALIDAD

Validaciones, errores, UX y consistencia.

### FASE 5 — HARDENING

Performance, logging, índices, deployment y deuda técnica.

---

# 14. VEREDICTO FINAL

Termina con:

```text
¿EL SISTEMA ESTÁ LISTO PARA MVP?

SÍ / NO / PARCIALMENTE

Madurez estimada:
__%

Cantidad de módulos operativos:
__/__

Bloqueadores:
__

Riesgo principal:
__

Siguiente acción recomendada:
__
```

---

# REGLAS IMPORTANTES

1. **No asumas que algo funciona porque existe código.**
2. **No consideres un mock como funcionalidad operativa.**
3. **No consideres una pantalla terminada como un módulo terminado.**
4. **Verifica frontend → backend → DB.**
5. **Verifica persistencia real.**
6. **Verifica multi-tenancy.**
7. **Verifica autorización en backend.**
8. **No ocultes problemas para mejorar el porcentaje.**
9. **No refactorices por gusto durante la auditoría.**
10. **Prioriza problemas que puedan afectar datos reales.**
11. **No elimines funcionalidades existentes.**
12. **No cambies la arquitectura sin justificarlo.**
13. **Si algo no puede probarse por falta de configuración, indícalo explícitamente como "NO VERIFICADO".**
14. **Diferencia claramente entre "no funciona" y "no pude verificarlo".**
15. **Si encuentras un problema, busca también otros lugares donde pueda existir el mismo problema.**
16. **Piensa como un atacante al revisar multi-tenancy y autorización.**
17. **Piensa como un usuario real al probar los flujos ERP.**
18. **Piensa como un administrador de base de datos al revisar persistencia e integridad.**

## OBJETIVO FINAL

No quiero que simplemente me digas si "el código está bien".

Quiero saber:

> **"Si mañana entrego este sistema a una pequeña empresa como MVP ERP, ¿qué funcionará realmente, qué no funcionará y qué podría causar problemas?"**

Realiza la auditoría completa y entrega el diagnóstico antes de proponer cambios.