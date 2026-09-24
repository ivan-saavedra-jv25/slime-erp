# NC-010 — Frontend: servicio, modelos, rutas y menú

## Objetivo

Dejar disponible en el frontend todo lo que los tres componentes van a consumir.

## Convenciones del proyecto (obligatorias)

- Angular 18 **standalone**, sin NgModules.
- Archivos planos en `features/notas-credito/`, sin subcarpeta por componente.
- `constructor(private x: Service)` — **no `inject()`**: los tests instancian las clases a
  mano con spies, sin `TestBed`.
- No existe `frontend/src/app/shared/`; lo transversal vive en `core/`.

## 1. Tipos — `frontend/src/app/core/models/models.ts`

```ts
export type EstadoNotaCredito = 'BORRADOR' | 'EMITIDA' | 'ANULADA';
export type TipoCorreccion = 'CORRIGE_DOCUMENTO' | 'CORRIGE_MONTO' | 'CORRIGE_TEXTO';
export type AccionNotaCredito = 'CREADA' | 'EDITADA' | 'EMITIDA' | 'ANULADA';
export type RecuperacionInventario = 'NO' | 'PARCIAL' | 'TOTAL';
export type TipoMovimientoNotaCredito = 'RECUPERACION' | 'REVERSA_ANULACION';

export interface DocumentoAsociado { ventaId; tipo: TipoDocumentoVenta; folio; numero; fecha; razon; montoTotal; }
export interface DocumentoAsociable { ventaId; tipoDocumento; folio; numero; fecha; montoTotal; exento; bodegaId; tieneNotasCredito; }
export interface LineaDocumentoOriginal { ventaDetalleId; productoId; codigo; descripcion;
  cantidad; cantidadRecuperada; cantidadDisponible; precioUnitario; descuento; subtotal; }
export interface NotaCreditoItem { productoId; ventaDetalleId: number | null; cantidad;
  precioUnitario; descuento; recuperaInventario: boolean; }
export interface LineaNotaCredito extends NotaCreditoItem { id; codigo; descripcion; subtotal; }
export interface MovimientoRelacionado { movimientoInventarioId; tipo; fecha; productoId; producto; cantidad; bodegaId; bodega; }
export interface EventoNotaCredito { fecha; usuario; accion; estadoAnterior; estadoNuevo; detalle; }
export interface NotaCreditoResumen { ... }
export interface NotaCredito { ... }       // espejo de NotaCreditoCompleta
export interface DashboardNotasCredito { ... }
```

Reflejar exactamente los records del backend (`07-acciones-y-listado.md`). Añadir también
los dos `Permiso` nuevos (tarea NC-003).

## 2. Servicio — `frontend/src/app/core/services/nota-credito.service.ts`

`@Injectable({ providedIn: 'root' })`, `constructor(private http: HttpClient)`,
base `${environment.apiUrl}/notas-credito`.

`NotaCreditoRequest` y `FiltrosNotaCredito` se declaran **en este mismo archivo**, no en
`models.ts` (es la convención de `nota-venta.service.ts`).

| Método | Verbo / URL |
|---|---|
| `listar(filtros)` | GET `/` con `HttpParams` |
| `dashboard(desde, hasta)` | GET `/dashboard` |
| `obtener(id)` | GET `/{id}` |
| `documentosAsociables(clienteId, q)` | GET `/documentos-asociables` |
| `lineasDocumento(ventaId, excluyendoNotaCreditoId?)` | GET `/ventas/{ventaId}/lineas` |
| `crear(request)` | POST `/` |
| `actualizar(id, request)` | PUT `/{id}` |
| `eliminar(id)` | DELETE `/{id}` |
| `emitir(id)` | POST `/{id}/emitir` |
| `anular(id, motivo)` | POST `/{id}/anular` |
| `obtenerPdf(id)` | GET `/{id}/pdf`, `responseType: 'blob'` |

Construcción de params: paginación siempre presente, filtros condicionales.

```ts
let params = new HttpParams().set('pagina', filtros.pagina ?? 0).set('tamano', filtros.tamano ?? 10);
if (filtros.estado) params = params.set('estado', filtros.estado);
if (filtros.tipoCorreccion) params = params.set('tipoCorreccion', filtros.tipoCorreccion);
...
```

Las acciones de transición devuelven la entidad completa actualizada; el componente solo
reasigna `this.nota = nota`.

## 3. Etiquetas — `features/notas-credito/estado-nota-credito.ts`

```ts
export const ETIQUETAS_ESTADO: Record<EstadoNotaCredito, string> = {
  BORRADOR: 'Borrador', EMITIDA: 'Emitida', ANULADA: 'Anulada',
};
export const TAGS_ESTADO: Record<EstadoNotaCredito, string> = {
  BORRADOR: 'tag--warning', EMITIDA: 'tag--success', ANULADA: 'tag--error',
};
export const ESTADOS: EstadoNotaCredito[] = ['BORRADOR', 'EMITIDA', 'ANULADA'];

export const ETIQUETAS_TIPO_CORRECCION: Record<TipoCorreccion, string> = {
  CORRIGE_DOCUMENTO: 'Corrige documento',
  CORRIGE_MONTO: 'Corrige monto',
  CORRIGE_TEXTO: 'Corrige texto',
};
export const TIPOS_CORRECCION: TipoCorreccion[] = [...];

export const ETIQUETAS_RECUPERACION: Record<RecuperacionInventario, string> = {
  NO: 'No', PARCIAL: 'Parcial', TOTAL: 'Sí',
};
```

Las clases `tag`, `tag--success|warning|info|error` son **globales**
(`styles/_components.scss`); no redefinirlas.

## 4. Rutas — `frontend/src/app/app.routes.ts`

Dentro del bloque `''` (LayoutComponent + `[authGuard, negocioGuard]`), después de las de
notas de venta. **El orden importa**: `nueva` y `:id/editar` van antes de `:id`.

```ts
{ path: 'notas-credito',            loadComponent: () => import('./features/notas-credito/notas-credito.component').then(m => m.NotasCreditoComponent) },
{ path: 'notas-credito/nueva',      loadComponent: () => import('./features/notas-credito/nota-credito-form.component').then(m => m.NotaCreditoFormComponent) },
{ path: 'notas-credito/:id/editar', loadComponent: () => import('./features/notas-credito/nota-credito-form.component').then(m => m.NotaCreditoFormComponent) },
{ path: 'notas-credito/:id',        loadComponent: () => import('./features/notas-credito/nota-credito-detalle.component').then(m => m.NotaCreditoDetalleComponent) },
```

## 5. Menú — `frontend/src/app/layout/layout.component.ts`

En `GRUPOS` → grupo `operacion`, después de Ventas (la NC corrige una venta, así que va
después en el flujo):

```ts
{ ruta: '/notas-credito', label: 'Notas de Crédito', icono: 'assignment_return', permiso: 'NOTAS_CREDITO_VER' },
```

El getter `grupos` ya filtra por permiso; no hay que tocar nada más.

## Validación de la tarea

- `nota-credito.service.spec.ts` con `HttpTestingController`, al estilo de
  `nota-venta.service.spec.ts`: verificar la URL y el método de cada llamada, y que
  `listar` manda `pagina` y `tamano` por defecto.
- `cd frontend && npm test -- --watch=false` pasa.
- La app compila y el ítem aparece en el sidebar para un usuario con permiso.
