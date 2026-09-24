# ND-010 — Frontend base: servicio, modelos, rutas y menú

## Objetivo

Dejar el módulo conectado en frontend: servicio HTTP, tipos en `models.ts`, 4 rutas lazy y
el ítem del sidebar, espejo exacto del módulo notas-credito.

## Servicio — `frontend/src/app/core/services/nota-debito.service.ts`

`base = ${environment.apiUrl}/notas-debito`, `@Injectable({ providedIn: 'root' })`.

| Método | HTTP | Ruta | Params / Body → Response |
|---|---|---|---|
| `listar(filtros: FiltrosNotaDebito)` | GET | `/notas-debito` | `pagina`, `tamano`, `estado`, `clienteId`, `tipoReversion`, `notaCreditoId`, `desde`, `hasta`, `q`, `sort`, `dir` → `PaginaResponse<NotaDebitoResumen>` |
| `dashboard(desde, hasta)` | GET | `/notas-debito/dashboard` | → `DashboardNotasDebito` |
| `obtener(id)` | GET | `/notas-debito/{id}` | → `NotaDebito` |
| `notasCreditoAsociables(clienteId, q?)` | GET | `/notas-debito/notas-credito-asociables` | → `DocumentoNotaCreditoAsociable[]` |
| `lineasNotaCredito(ncId, excluyendoNotaDebitoId?)` | GET | `/notas-debito/notas-credito/{ncId}/lineas` | → `LineaNotaCreditoOriginal[]` |
| `crear(request)` | POST | `/notas-debito` | `NotaDebitoRequest` → `NotaDebito` |
| `actualizar(id, request)` | PUT | `/notas-debito/{id}` | → `NotaDebito` |
| `eliminar(id)` | DELETE | `/notas-debito/{id}` | → `void` |
| `emitir(id)` | POST | `/notas-debito/{id}/emitir` | `{}` → `NotaDebito` |
| `anular(id, motivo?)` | POST | `/notas-debito/{id}/anular` | `{ motivo }` → `NotaDebito` |
| `obtenerPdf(id)` | GET | `/notas-debito/{id}/pdf` | `blob` |

Exportar `NotaDebitoRequest` y `FiltrosNotaDebito` con las mismas convenciones que NC.
Acompañar con `nota-debito.service.spec.ts` espejo.

## Modelos — `frontend/src/app/core/models/models.ts`

Agregar un bloque `// --- Nota de Débito ---` (junto al de NC):

- Tipos: `EstadoNotaDebito = 'BORRADOR'|'EMITIDA'|'ANULADA'`, `TipoReversion =
  'REVIERTE_DOCUMENTO'|'REVIERTE_MONTO'|'REVIERTE_TEXTO'`,
  `AccionNotaDebito = 'CREADA'|'EDITADA'|'EMITIDA'|'ANULADA'`,
  `ImpactoInventario = 'NO'|'PARCIAL'|'TOTAL'`,
  `TipoMovimientoNotaDebito = 'REVERSION'|'REVERSA_ANULACION'`.
- Interfaces: `DocumentoNotaCreditoAsociable`, `LineaNotaCreditoOriginal`,
  `LineaNotaDebito`, `NotaDebitoItem` (request), `MovimientoRelacionado` (reutilizar),
  `EventoNotaDebito`, `NotaDebitoResumen`, `NotaDebito`, `ConteoPorEstadoNotaDebito`,
  `ConteoPorTipoReversion`, `DashboardNotasDebito`.
- `NotaDebito` incluye: `numero, estado, tipoReversion, clienteNombre/Rut/RazonSocial,
  documentoAsociado (NC), observaciones, textoCorreccion, bodegaNombre, exenta, moneda,
  descuento, montoSubtotal, montoDescuento, montoNeto, montoIva, montoTotal,
  fechaEmision, fechaAnulacion, impactoInventario, lineas[], movimientosInventario[],
  historial[]`.

## Rutas — `frontend/src/app/app.routes.ts`

Clonar el bloque de NC respetando el orden (todas lazy):

```ts
{ path: 'notas-debito',               loadComponent: () => import('./features/notas-debito/notas-debito.component').then((m) => m.NotasDebitoComponent) },
{ path: 'notas-debito/nueva',         loadComponent: () => import('./features/notas-debito/nota-debito-form.component').then((m) => m.NotaDebitoFormComponent) },
{ path: 'notas-debito/:id/editar',    loadComponent: () => import('./features/notas-debito/nota-debito-form.component').then((m) => m.NotaDebitoFormComponent) },
{ path: 'notas-debito/:id',           loadComponent: () => import('./features/notas-debito/nota-debito-detalle.component').then((m) => m.NotaDebitoDetalleComponent) },
```

## Sidebar

En `layout.component.ts` `GRUPOS`, grupo `operacion`, junto a "Notas de Crédito":

```ts
{ ruta: '/notas-debito', label: 'Notas de Débito', icono: 'assignment_returned', permiso: 'NOTAS_DEBITO_VER' },
```

## Validación

- `ng build` compila.
- `ng test --include=**/nota-debito.service.spec.ts` pasa.