# Saavia Admin — Especificación técnica del panel administrativo SaaS

## 1. Objetivo

Construir un panel administrativo central para gestionar y monitorear la plataforma SaaS del ERP **Saavia**.

Este sistema NO corresponde al ERP utilizado por los clientes.

El sistema corresponde al **Backoffice Administrativo de Saavia**, utilizado por los administradores de la plataforma para:

- Gestionar empresas/tenants.
- Gestionar usuarios de las empresas.
- Gestionar planes y suscripciones.
- Gestionar pagos.
- Supervisar el estado de las empresas.
- Supervisar integraciones con SII.
- Supervisar documentos tributarios electrónicos.
- Gestionar alertas.
- Gestionar soporte.
- Consultar auditoría.
- Supervisar la salud general de la plataforma.

La aplicación debe estar diseñada como un sistema **multi-tenant**, pero el administrador central debe operar desde un contexto global de plataforma.

---

# 2. Arquitectura conceptual

La plataforma estará compuesta por dos aplicaciones/backend principales:

```text
                         SAAVIA
                            |
             +--------------+--------------+
             |                             |
       ADMIN BACKEND                  ERP BACKEND
             |                             |
       ADMIN DATABASE                TENANT DATABASE
             |                             |
       +-----+------+             +--------+--------+
       |            |             |                 |
    Empresas     Pagos        Tenant A          Tenant B
    Planes       Auditoría        |                 |
    Soporte      Alertas       Datos ERP         Datos ERP
```

## Regla fundamental

El Admin Backend debe administrar principalmente información de plataforma.

No debe convertirse en una segunda interfaz para operar directamente el ERP de los clientes.

Cuando sea necesario consultar información de un tenant, debe realizarse mediante servicios controlados y auditados.

---

# 3. Stack tecnológico

Utilizar el stack existente del proyecto cuando sea posible.

Frontend:

- Angular.
- TypeScript.
- Angular Router.
- Angular Reactive Forms.
- Componentes reutilizables.
- Guards.
- Interceptors.
- Servicios HTTP.
- Manejo centralizado de errores.
- Diseño responsive.

Backend:

- Utilizar el backend existente del proyecto si corresponde.
- API REST.
- Autenticación mediante JWT o mecanismo existente.
- RBAC para autorización.
- Validación de DTOs.
- Logging.
- Auditoría.

Base de datos:

- PostgreSQL.

No crear tecnologías paralelas innecesarias.

Antes de implementar, inspeccionar el proyecto existente y reutilizar:

- Componentes.
- Layout.
- Sistema de autenticación.
- Design system.
- Servicios.
- Tipografías.
- Colores.
- Iconografía.
- Patrones de arquitectura.

---

# 4. Roles administrativos

Implementar RBAC.

Roles mínimos:

## SUPER_ADMIN

Acceso total.

Puede:

- Gestionar empresas.
- Gestionar usuarios.
- Gestionar planes.
- Gestionar suscripciones.
- Gestionar pagos.
- Gestionar alertas.
- Gestionar soporte.
- Consultar auditoría.
- Gestionar configuración global.
- Suspender/reactivar empresas.

## ADMIN

Puede:

- Consultar empresas.
- Gestionar usuarios.
- Gestionar soporte.
- Consultar alertas.
- Consultar auditoría.

No puede modificar configuraciones críticas de plataforma.

## SUPPORT

Puede:

- Consultar empresas.
- Consultar usuarios.
- Consultar actividad.
- Consultar errores.
- Gestionar tickets.

No puede modificar pagos ni configuración financiera.

## FINANCE

Puede:

- Consultar empresas.
- Gestionar planes.
- Gestionar suscripciones.
- Registrar pagos.
- Consultar pagos pendientes.
- Consultar vencimientos.

## AUDITOR

Solo lectura:

- Empresas.
- Usuarios.
- Suscripciones.
- Pagos.
- Auditoría.
- Alertas.
- Actividad.

---

# 5. Dashboard administrativo

Ruta:

```text
/admin/dashboard
```

Debe mostrar información global de la plataforma.

## KPIs

Mostrar:

- Empresas totales.
- Empresas activas.
- Empresas en prueba.
- Empresas suspendidas.
- Empresas vencidas.
- Nuevas empresas del período.
- Usuarios totales.
- Suscripciones activas.
- Suscripciones por vencer.
- Pagos pendientes.
- Pagos vencidos.
- DTE emitidos.
- DTE rechazados.
- DTE pendientes.
- Alertas críticas.

## Gráficos

Implementar inicialmente:

### Empresas por estado

```text
Activas
Prueba
Suspendidas
Vencidas
Canceladas
```

### Evolución de empresas

Mostrar crecimiento de empresas durante el período seleccionado.

### Suscripciones

Mostrar distribución por plan.

### DTE

Mostrar:

```text
Emitidos
Aceptados
Rechazados
Pendientes
Con error
```

## Actividad reciente

Mostrar las últimas acciones administrativas:

```text
08/09/2026 19:30
Administrador suspendió Empresa ABC

08/09/2026 19:20
Nueva empresa creada

08/09/2026 19:10
Pago registrado

08/09/2026 18:50
Certificado próximo a vencer
```

---

# 6. Gestión de empresas

Ruta:

```text
/admin/companies
```

Esta es una de las funcionalidades principales.

## Listado

Columnas:

- RUT.
- Razón social.
- Nombre comercial.
- Estado.
- Plan.
- Suscripción.
- Fecha de creación.
- Próximo vencimiento.
- Usuarios.
- Estado SII.
- Último acceso.
- Acciones.

## Filtros

Permitir filtrar por:

- RUT.
- Razón social.
- Estado.
- Plan.
- Suscripción.
- Estado SII.
- Fecha de creación.
- Fecha de vencimiento.

## Búsqueda

La búsqueda debe ser rápida y soportar:

```text
RUT
Razón social
Nombre comercial
ID empresa
```

---

# 7. Estados de empresa

Utilizar estados controlados:

```text
TRIAL
ACTIVE
SUSPENDED
EXPIRED
BLOCKED
CANCELLED
```

## TRIAL

Empresa en período de prueba.

## ACTIVE

Empresa activa y con acceso normal.

## SUSPENDED

Empresa temporalmente suspendida.

## EXPIRED

Suscripción vencida.

## BLOCKED

Empresa bloqueada administrativamente.

## CANCELLED

Empresa cancelada.

No eliminar físicamente empresas al cambiar de estado.

Utilizar estados y auditoría.

---

# 8. Detalle de empresa

Ruta:

```text
/admin/companies/:id
```

Crear una vista detallada.

## Información general

Mostrar:

- ID.
- RUT.
- Razón social.
- Nombre comercial.
- Giro.
- Dirección registrada.
- Fecha de creación.
- Estado.
- Plan.
- Suscripción.

## Resumen

Mostrar:

```text
Usuarios
Documentos emitidos
Último acceso
Último DTE
Estado SII
Estado suscripción
Alertas
```

## Tabs

Crear las siguientes pestañas:

```text
Resumen
Información
Usuarios
Suscripción
Pagos
DTE
SII
Actividad
Alertas
Soporte
```

---

# 9. Usuarios de una empresa

Ruta:

```text
/admin/companies/:id/users
```

Mostrar:

- Nombre.
- Email.
- Rol.
- Estado.
- Último acceso.
- Fecha de creación.

Acciones:

- Ver usuario.
- Activar.
- Desactivar.
- Bloquear.
- Revocar sesiones.

No permitir eliminar información histórica necesaria para auditoría.

---

# 10. Suscripciones

Ruta:

```text
/admin/subscriptions
```

Gestionar:

- Plan.
- Empresa.
- Estado.
- Fecha de inicio.
- Fecha de vencimiento.
- Ciclo de facturación.
- Precio.
- Período de gracia.
- Historial.

Estados:

```text
TRIAL
ACTIVE
PAST_DUE
SUSPENDED
CANCELLED
EXPIRED
```

---

# 11. Planes

Ruta:

```text
/admin/plans
```

Permitir definir planes.

Ejemplo:

```text
Plan Básico
Plan Profesional
Plan Empresa
```

Cada plan puede contener:

- Nombre.
- Descripción.
- Precio mensual.
- Precio anual.
- Límite de usuarios.
- Límite de documentos.
- Módulos disponibles.
- Características.
- Estado.

Ejemplo:

```json
{
  "name": "Profesional",
  "monthlyPrice": 29990,
  "maxUsers": 10,
  "maxDocuments": 1000,
  "modules": [
    "sales",
    "purchases",
    "inventory",
    "accounting",
    "dte"
  ]
}
```

No eliminar planes que tengan empresas asociadas.

Utilizar:

```text
ACTIVE
INACTIVE
```

---

# 12. Pagos

Ruta:

```text
/admin/payments
```

Mostrar:

- Empresa.
- Suscripción.
- Monto.
- Fecha.
- Método.
- Estado.
- Referencia.
- Administrador que registró el pago.

Estados:

```text
PENDING
PAID
FAILED
REFUNDED
CANCELLED
```

Permitir:

- Registrar pago manual.
- Consultar pago.
- Marcar pago.
- Ver historial.

Toda modificación debe quedar auditada.

---

# 13. Gestión de vencimientos

Crear una sección:

```text
/admin/subscriptions/expiring
```

Mostrar:

```text
Vencen hoy
Vencen en 3 días
Vencen en 7 días
Vencen en 30 días
Vencidas
```

Permitir acciones:

- Ver empresa.
- Contactar empresa.
- Extender suscripción.
- Registrar pago.
- Suspender.
- Reactivar.

---

# 14. Período de gracia

La lógica de suspensión no debe estar hardcodeada.

Crear configuración:

```text
gracePeriodDays
```

Ejemplo:

```text
Vencimiento
    |
    v
Período de gracia
    |
    v
PAST_DUE
    |
    v
SUSPENDED
```

Toda transición debe quedar registrada.

---

# 15. Integración SII

Ruta:

```text
/admin/sii
```

Mostrar estado global de las empresas.

Por empresa:

```text
Configuración SII: OK
Certificado: OK
Certificado vence: fecha
Ambiente: Producción
Última comunicación: fecha
Último DTE: fecha
```

Estados:

```text
CONFIGURED
NOT_CONFIGURED
CERTIFICATE_EXPIRING
CERTIFICATE_EXPIRED
CONNECTION_ERROR
```

No mostrar secretos ni credenciales sensibles.

Nunca almacenar contraseñas o secretos en texto plano.

---

# 16. Monitoreo de DTE

Ruta:

```text
/admin/dte
```

Mostrar información global.

Filtros:

- Empresa.
- Tipo de DTE.
- Estado.
- Fecha.
- Folio.
- RUT receptor.

Estados:

```text
CREATED
PENDING
SENT
ACCEPTED
REJECTED
ERROR
```

Dashboard:

```text
DTE emitidos
DTE aceptados
DTE rechazados
DTE pendientes
DTE con error
```

## Importante

Este módulo inicialmente debe ser principalmente de:

```text
MONITOREO
DIAGNÓSTICO
AUDITORÍA
```

No convertirlo en una interfaz completa para emitir DTE desde el Admin.

La emisión pertenece al ERP del tenant.

---

# 17. Centro de alertas

Ruta:

```text
/admin/alerts
```

Tipos:

```text
CRITICAL
WARNING
INFO
```

Ejemplos:

### CRITICAL

- Empresa con error de emisión.
- Error SII.
- Certificado vencido.
- Empresa bloqueada.
- Servicio no disponible.

### WARNING

- Certificado próximo a vencer.
- Suscripción próxima a vencer.
- Folios bajos.
- Pago pendiente.

### INFO

- Nueva empresa.
- Nuevo usuario.
- Nueva suscripción.
- Pago recibido.

Cada alerta debe contener:

```text
id
type
severity
title
description
companyId
createdAt
readAt
resolvedAt
status
```

---

# 18. Sistema de soporte

Ruta:

```text
/admin/support
```

Crear sistema básico de tickets.

## Ticket

Campos:

```text
id
companyId
userId
subject
description
category
priority
status
assignedTo
createdAt
updatedAt
resolvedAt
```

Prioridades:

```text
LOW
MEDIUM
HIGH
CRITICAL
```

Estados:

```text
OPEN
IN_PROGRESS
WAITING
RESOLVED
CLOSED
```

---

# 19. Auditoría

Ruta:

```text
/admin/audit
```

Registrar absolutamente todas las acciones administrativas relevantes.

Modelo:

```text
audit_logs
```

Campos:

```text
id
adminUserId
companyId
action
module
entityType
entityId
oldValue
newValue
ipAddress
userAgent
createdAt
```

Ejemplo:

```text
Administrador:
admin@example.com

Empresa:
Empresa ABC

Módulo:
Subscriptions

Acción:
EXTEND_SUBSCRIPTION

Antes:
2026-09-08

Después:
2026-10-08

Fecha:
2026-09-08 19:30
```

## Acciones auditables

Como mínimo:

```text
CREATE
UPDATE
DELETE
ACTIVATE
DEACTIVATE
SUSPEND
REACTIVATE
BLOCK
UNBLOCK
LOGIN
LOGOUT
PLAN_CHANGE
PAYMENT_REGISTERED
SUBSCRIPTION_EXTENDED
USER_DISABLED
```

---

# 20. Seguridad

La seguridad es prioritaria.

Implementar:

- Autenticación.
- Autorización RBAC.
- Guards.
- Expiración de sesión.
- Revocación de sesiones.
- Rate limiting.
- Validación de entrada.
- Protección contra IDOR.
- Protección contra acceso cross-tenant.
- Logs de seguridad.
- Auditoría.
- HTTPS en producción.

## Regla crítica

Un administrador no debe poder acceder accidentalmente a datos de otra empresa utilizando un `companyId` manipulado.

Todo acceso debe validar permisos en backend.

Nunca confiar únicamente en el frontend.

---

# 21. Gestión de sesión administrativa

Crear:

```text
/admin/profile
/admin/security
```

Mostrar:

- Usuario.
- Email.
- Rol.
- Último acceso.
- Sesiones activas.

Permitir:

- Cerrar sesión.
- Revocar otras sesiones.

Registrar accesos administrativos.

---

# 22. Configuración global

Ruta:

```text
/admin/settings
```

Configuraciones iniciales:

```text
Nombre plataforma
Logo
Email soporte
Período de prueba
Período de gracia
Configuración de alertas
Configuración de suscripciones
```

Separar configuración global de configuración del tenant.

---

# 23. Notificaciones

Diseñar infraestructura para futuras notificaciones.

Eventos:

```text
SUBSCRIPTION_EXPIRING
SUBSCRIPTION_EXPIRED
PAYMENT_PENDING
PAYMENT_RECEIVED
SII_ERROR
CERTIFICATE_EXPIRING
CERTIFICATE_EXPIRED
DTE_ERROR
```

Inicialmente puede utilizarse solamente el centro de alertas.

Posteriormente agregar:

```text
Email
Push
WhatsApp
```

No implementar estos canales si no son necesarios para el MVP.

---

# 24. Base de datos administrativa

Crear un esquema independiente para administración.

Ejemplo conceptual:

```text
admin
├── admin_users
├── roles
├── permissions
├── companies
├── plans
├── subscriptions
├── payments
├── support_tickets
├── alerts
├── audit_logs
├── system_settings
└── admin_sessions
```

Los datos propios del ERP deben permanecer separados.

---

# 25. Modelo Company

Ejemplo conceptual:

```text
companies

id
rut
legal_name
business_name
status
plan_id
created_at
updated_at
last_access_at
```

No almacenar aquí todos los datos operacionales del ERP.

---

# 26. Modelo Subscription

```text
subscriptions

id
company_id
plan_id
status
start_date
end_date
billing_cycle
price
grace_period_days
created_at
updated_at
```

---

# 27. Modelo Payment

```text
payments

id
company_id
subscription_id
amount
currency
payment_method
status
external_reference
paid_at
created_by
created_at
updated_at
```

---

# 28. Modelo Alert

```text
alerts

id
company_id
severity
type
title
description
status
created_at
read_at
resolved_at
```

---

# 29. Modelo AuditLog

```text
audit_logs

id
admin_user_id
company_id
action
module
entity_type
entity_id
old_value
new_value
ip_address
user_agent
created_at
```

Los valores `old_value` y `new_value` pueden utilizar JSONB.

---

# 30. Navegación

Crear menú lateral:

```text
Dashboard

Empresas
  ├── Todas
  ├── Activas
  ├── En prueba
  ├── Suspendidas
  └── Vencidas

Suscripciones
  ├── Todas
  ├── Activas
  ├── Por vencer
  └── Vencidas

Planes

Pagos
  ├── Todos
  ├── Pendientes
  └── Vencidos

DTE
  ├── Monitoreo
  ├── Pendientes
  ├── Rechazados
  └── Errores

SII

Alertas

Soporte

Auditoría

Configuración
```

---

# 31. UX/UI

El diseño debe diferenciarse visualmente del ERP de los clientes.

Debe sentirse como un:

```text
SaaS Admin Dashboard
```

y no como un ERP operativo.

Priorizar:

- Información clara.
- Tablas.
- Filtros.
- Estados.
- Badges.
- KPIs.
- Alertas.
- Acciones rápidas.
- Confirmaciones antes de acciones críticas.

Utilizar el sistema visual existente de Saavia.

Mantener consistencia con:

- Colores.
- Tipografía.
- Espaciado.
- Botones.
- Inputs.
- Modales.
- Tablas.
- Iconos.

---

# 32. Acciones críticas

Acciones como:

```text
Suspender empresa
Bloquear empresa
Cancelar suscripción
Cambiar plan
Desactivar usuario
```

deben requerir confirmación.

Mostrar:

```text
¿Está seguro?

Empresa:
Empresa ABC SpA

Acción:
Suspender empresa

Motivo:
[________________________]

[Cancelar] [Confirmar]
```

El motivo debe almacenarse en auditoría cuando corresponda.

---

# 33. Estados de carga y errores

Todas las vistas deben contemplar:

```text
Loading
Empty
Error
Success
Unauthorized
Forbidden
```

Ejemplo:

```text
No existen empresas que coincidan con los filtros.
```

No dejar pantallas en blanco.

---

# 34. API REST

Diseñar endpoints siguiendo una estructura consistente.

Ejemplo:

```text
GET    /api/admin/companies
GET    /api/admin/companies/:id
POST   /api/admin/companies
PATCH  /api/admin/companies/:id
POST   /api/admin/companies/:id/suspend
POST   /api/admin/companies/:id/reactivate
```

Usuarios:

```text
GET    /api/admin/companies/:id/users
PATCH  /api/admin/users/:id
POST   /api/admin/users/:id/disable
POST   /api/admin/users/:id/enable
```

Suscripciones:

```text
GET    /api/admin/subscriptions
GET    /api/admin/subscriptions/:id
POST   /api/admin/subscriptions/:id/extend
POST   /api/admin/subscriptions/:id/suspend
POST   /api/admin/subscriptions/:id/reactivate
```

Pagos:

```text
GET    /api/admin/payments
POST   /api/admin/payments
GET    /api/admin/payments/:id
```

DTE:

```text
GET    /api/admin/dte
GET    /api/admin/dte/:id
```

Alertas:

```text
GET    /api/admin/alerts
PATCH  /api/admin/alerts/:id/read
POST   /api/admin/alerts/:id/resolve
```

Auditoría:

```text
GET    /api/admin/audit
```

---

# 35. Paginación

Todas las listas grandes deben utilizar paginación.

Ejemplo:

```text
GET /api/admin/companies?page=1&limit=20
```

Respuesta:

```json
{
  "data": [],
  "pagination": {
    "page": 1,
    "limit": 20,
    "total": 128,
    "totalPages": 7
  }
}
```

No cargar miles de registros innecesariamente.

---

# 36. Búsqueda y filtros

La búsqueda debe ejecutarse preferentemente en backend.

No descargar toda la tabla para filtrar mediante Angular.

Los filtros deben poder combinarse:

```text
Estado + Plan + Fecha
```

y mantener el estado de los filtros al navegar cuando corresponda.

---

# 37. Dashboard API

Crear un endpoint específico:

```text
GET /api/admin/dashboard
```

Debe devolver los KPIs necesarios para evitar múltiples requests innecesarios.

Ejemplo conceptual:

```json
{
  "companies": {
    "total": 128,
    "active": 117,
    "trial": 5,
    "suspended": 4,
    "expired": 2
  },
  "subscriptions": {
    "active": 117,
    "expiring": 8,
    "expired": 2
  },
  "payments": {
    "pending": 12,
    "overdue": 4
  },
  "dte": {
    "issued": 12482,
    "accepted": 12100,
    "rejected": 43,
    "pending": 339
  },
  "alerts": {
    "critical": 2,
    "warning": 8
  }
}
```

---

# 38. Health monitoring

Preparar una sección:

```text
/admin/system
```

para monitorear:

```text
Admin API
ERP API
PostgreSQL
SII integration
DTE service
Background jobs
```

Estados:

```text
HEALTHY
DEGRADED
DOWN
```

Inicialmente puede ser una implementación básica.

---

# 39. Jobs automáticos

Preparar backend para tareas programadas.

Ejemplos:

```text
Verificar suscripciones próximas a vencer
Detectar suscripciones vencidas
Generar alertas
Verificar certificados próximos a vencer
Detectar errores DTE
Limpiar sesiones expiradas
```

No realizar estas tareas únicamente desde Angular.

Deben ejecutarse en backend.

---

# 40. Reglas de multi-tenancy

El sistema debe respetar estrictamente la separación de tenants.

```text
ADMIN
  |
  +---- Company A
  |
  +---- Company B
  |
  +---- Company C
```

El administrador puede consultar múltiples empresas porque tiene permisos globales.

Los usuarios del ERP, en cambio, solamente pueden acceder a su tenant.

Nunca permitir que:

```text
userTenantA -> datosTenantB
```

---

# 41. No implementar en el MVP

No construir inicialmente:

- CRM avanzado.
- Marketing.
- Facturación SaaS automatizada compleja.
- WhatsApp.
- App móvil administrativa.
- Analítica avanzada.
- BI.
- Automatizaciones complejas.
- Gestión contable del propio Saavia.
- Emisión de DTE desde Admin.
- Edición directa de datos operacionales de los tenants.

Estas funcionalidades pueden quedar preparadas arquitectónicamente para futuras versiones.

---

# 42. Prioridad de implementación

## P0 — Obligatorio

Implementar primero:

1. Autenticación administrativa.
2. RBAC.
3. Dashboard.
4. Empresas.
5. Detalle de empresa.
6. Usuarios.
7. Estados de empresa.
8. Planes.
9. Suscripciones.
10. Auditoría.
11. Alertas básicas.

## P1 — MVP completo

Después:

12. Pagos.
13. Vencimientos.
14. Monitoreo DTE.
15. Estado SII.
16. Soporte.
17. Configuración global.

## P2 — Futuro

Finalmente:

18. Health monitoring.
19. Jobs automáticos avanzados.
20. Notificaciones.
21. Analítica SaaS.
22. Automatización de cobros.

---

# 43. Criterios de aceptación

El sistema se considera funcional cuando:

### Empresas

- Es posible listar empresas.
- Es posible buscar empresas.
- Es posible filtrar empresas.
- Es posible ver detalle.
- Es posible cambiar estado.
- Todas las acciones quedan auditadas.

### Usuarios

- Es posible visualizar usuarios.
- Es posible activar/desactivar usuarios.
- Los permisos se respetan.

### Suscripciones

- Es posible visualizar suscripciones.
- Es posible asignar planes.
- Es posible cambiar planes.
- Es posible extender suscripciones.
- Se manejan vencimientos.

### Pagos

- Es posible visualizar pagos.
- Es posible registrar pagos.
- Se mantiene historial.

### DTE/SII

- Es posible visualizar estado global.
- Es posible detectar errores.
- Es posible consultar DTE problemáticos.

### Alertas

- Se muestran alertas.
- Se pueden marcar como leídas.
- Se pueden resolver.

### Auditoría

Toda acción administrativa crítica genera un registro.

### Seguridad

Un usuario administrativo sin permisos no puede ejecutar acciones restringidas.

---

# 44. Regla de implementación

Antes de comenzar a desarrollar:

1. Analizar el proyecto existente.
2. Identificar arquitectura actual.
3. Identificar autenticación existente.
4. Identificar componentes reutilizables.
5. Identificar modelos existentes.
6. Identificar conexión PostgreSQL.
7. Identificar estructura multi-tenant.
8. No duplicar funcionalidades existentes.
9. Proponer cambios de arquitectura antes de modificar componentes críticos.
10. Mantener compatibilidad con el ERP existente.

No reemplazar código existente sin justificarlo.

---

# 45. Resultado esperado

El resultado final debe ser un **Admin SaaS funcional**, separado conceptualmente del ERP de los clientes.

El administrador debe poder entrar al sistema y responder rápidamente:

```text
¿Cuántas empresas tengo?
        ↓
¿Cuáles están activas?
        ↓
¿Cuáles están en prueba?
        ↓
¿Cuáles deben pagar?
        ↓
¿Cuáles están vencidas?
        ↓
¿Cuáles tienen problemas con SII?
        ↓
¿Cuáles tienen errores DTE?
        ↓
¿Qué usuarios están teniendo problemas?
        ↓
¿Qué alertas requieren atención?
        ↓
¿Qué acciones administrativas se realizaron?
```

La prioridad es construir una plataforma **operativa, segura, auditable y escalable**, evitando sobrecargar el MVP con funcionalidades que pertenecen al ERP de cada tenant.