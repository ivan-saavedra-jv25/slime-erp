# Cobros de Tesorería sincronizados en Flujo de Caja — Design

## Contexto

`frontend/src/app/features/flujo-caja` es una herramienta de proyección financiera (presupuesto mensual: ingresos/gastos recurrentes y puntuales, por categoría) que vive **enteramente en el navegador** (`localStorage`, sin backend, sin tenant). El usuario carga sus ingresos y gastos a mano, mes a mes.

Ya existe un documento previo, `docs/superpowers/specs/2026-09-08-flujo-caja-backend-design.md`, que propone migrar **todo** el módulo a un backend por tenant y sumar automáticamente los **totales de Ventas y Compras** (devengado — lo facturado) a la proyección. Ese trabajo nunca se implementó (no existe módulo `cl.slimerp.flujocaja` en el backend ni un plan asociado) y queda fuera de este documento — es una migración de infraestructura mucho más grande y con datos distintos (devengado, no caja).

Este trabajo es más acotado: traer al Flujo de Caja los **cobros efectivamente recibidos** de clientes, que ya se registran en **Tesorería > Historial de pagos** (`TransaccionPago`, estado `CONFIRMADA`). Es información de caja real (lo que efectivamente entró), complementaria — no reemplaza ni se cruza con la spec de 2026-09-08.

## Decisiones validadas con el usuario

1. "Sincronizar" significa **autocompletar automáticamente**: cada cobro confirmado aparece como ingreso real en su mes, sin acción manual. No es una vista lado a lado de proyectado-vs-real.
2. Alcance: **solo** cobros a clientes (cuentas por cobrar) vía Tesorería. Caja Chica y Compras quedan fuera.
3. Enfoque técnico: **fusión en vivo, sin persistir**. Los cobros se piden al backend cada vez que se abre el módulo (o se pide actualizar) y se mezclan en el cálculo de proyección; nunca se escriben en el `localStorage` del Flujo de Caja. Esto evita migración de esquema y hace que un pago anulado en Tesorería desaparezca solo, en la siguiente sincronización.
4. Visualización: los cobros sincronizados aparecen como ítems de **solo lectura** en la lista de ingresos del mes (junto a los manuales), con una marca visual y sin botones de editar/eliminar — se administran desde Tesorería.
5. La spec de 2026-09-08 (backend completo + Ventas/Compras) queda como trabajo futuro pendiente, sin modificarla.
6. Ningún paso de este trabajo genera commits por su cuenta — ni al escribir esta spec, ni durante la implementación. El commit se hace solo cuando el usuario lo pide explícitamente, igual que el resto de la sesión.

## Arquitectura

```
Angular (flujo-caja)
   ↓ CobrosSyncService (nuevo)
   ↓ TransaccionPagoService.buscar()  (ya existe, sin cambios)
Backend: GET /api/tesoreria/pagos?estado=CONFIRMADA&pagina&tamano  (ya existe, sin cambios)
```

Sin cambios de backend. El motor de proyección (`projection.ts`) sigue siendo una función pura del frontend; solo gana un cuarto insumo (los cobros sincronizados) además de `state.recurring` y `state.oneOff`.

## 1. Nuevo servicio: `CobrosSyncService`

`frontend/src/app/features/flujo-caja/core/cobros-sync.service.ts` (nuevo).

```ts
export interface SyncedIncome {
  id: string;                 // `pago-${transaccionPago.id}`
  month: MonthKey;            // derivado de transaccionPago.fecha
  description: string;        // `Cobro ${clienteNombre} — Venta V-${ventaId}`
  amount: number;
  cuentaPorCobrarId: number;  // para el link "Ver cuenta"
}
```

- `sincronizar(): Observable<SyncedIncome[]>`:
  - Pide todas las páginas de `transaccionPagoService.buscar({ estado: 'CONFIRMADA', pagina, tamano: 200 })` (usa el endpoint que ya implementamos con paginación) hasta acumular `total` resultados — mismo criterio de "traer todo lo que haga falta" que ya usa `LibroVentasService`/`TransaccionPagoService` puertas adentro, pero acá es el cliente el que pagina porque el consumidor (`projection.ts`) necesita el conjunto completo, no una página.
  - Resuelve nombres de cliente con `clienteService.listar()` (una sola vez, mismo patrón ya usado en `tesoreria-historial.component.ts`).
  - Mapea cada `TransaccionPago` a un `SyncedIncome` con una función pura exportada aparte, `mapPagoToSyncedIncome(pago: TransaccionPago, nombreCliente: string): SyncedIncome`, para poder testearla sin `HttpClient`.
- Si el usuario no tiene permiso `TESORERIA_VER`, la llamada devuelve 403; el servicio no lo oculta, lo deja propagar como error del observable (lo maneja el store, ver más abajo).

## 2. `SyncedIncome` vive fuera de `core/models.ts`

No se toca `models.ts` ni `STATE_VERSION`: `SyncedIncome` no es parte del estado persistido, así que se declara en el nuevo `cobros-sync.service.ts` (o un `core/synced-income.ts` si `projection.ts` también necesita importarlo — se decide al implementar, según qué quede más limpio).

## 3. Cambios en `core/projection.ts`

- `Line['source']` gana el valor `'synced'`.
- `Line` gana un campo opcional `cuentaPorCobrarId?: number` (solo lo llevan las líneas `synced`; sirve para el link "Ver cuenta" en la UI sin tener que ir a buscar el dato a otro lado).
- `projectMonth(state, month, openingBalance, accumulatedTaxProvision, syncedIncomes: readonly SyncedIncome[] = [])`: nuevo loop, análogo al de `state.oneOff`, que por cada `SyncedIncome` cuyo `.month === month` hace `push(item.id, 'synced', 'income', CATEGORIA_VENTAS_ID, item.description, item.amount, false, null)` y le agrega `cuentaPorCobrarId` a la `Line` resultante.
  - `CATEGORIA_VENTAS_ID = 'cat-ventas'`: la categoría "Ventas" que ya viene por defecto en `DEFAULT_CATEGORIES` (`seed.ts`). Si el usuario la borró, `classify()` ya resuelve a `ORPHAN_CATEGORY` (`operational`/`variable`) sin cambios adicionales — mismo mecanismo que hoy protege a los `oneOff` huérfanos.
- `projectMonths`/`projectThrough`/`projectYear`: ganan el mismo parámetro opcional `syncedIncomes: readonly SyncedIncome[] = []`, filtrando por mes dentro del loop existente y pasando el subconjunto a `projectMonth`.

## 4. Cambios en `core/cashflow.store.ts`

- Inyecta `CobrosSyncService`.
- Nuevas señales:
  ```ts
  private readonly _syncedIncomes = signal<SyncedIncome[]>([]);
  readonly syncing = signal(false);
  readonly syncError = signal<string | null>(null);
  readonly lastSyncedAt = signal<Date | null>(null);
  ```
- `readonly projection = computed(() => projectYear(this._state(), this._year(), this._syncedIncomes()))` (se actualiza la llamada existente para pasar el tercer argumento).
- Nuevo método público:
  ```ts
  syncCobros(): void {
    this.syncing.set(true);
    this.syncError.set(null);
    this.cobrosSyncService.sincronizar().subscribe({
      next: (incomes) => {
        this._syncedIncomes.set(incomes);
        this.lastSyncedAt.set(new Date());
        this.syncing.set(false);
      },
      error: (err) => {
        this.syncError.set(
          err?.status === 403
            ? 'No tienes permiso para ver los cobros de Tesorería.'
            : 'No se pudo sincronizar con Tesorería.',
        );
        this.syncing.set(false);
      },
    });
  }
  ```
- Se llama `syncCobros()` una vez en el constructor (carga automática al abrir el módulo), además de quedar disponible para el botón manual de refresco.
- Si falla, el resto del store sigue funcionando con normalidad (los datos manuales no dependen de esto) — `_syncedIncomes` simplemente queda vacío.

## 5. UI

### `month-detail.html` / `.ts`

- En `<ng-template #groupTpl>`, la rama `@else` (hoy cubre `'oneoff'`) se abre para distinguir `'synced'`:
  - Ícono `sync` junto a la descripción, con `matTooltip="Cobro sincronizado desde Tesorería"` (mismo patrón que el ícono `repeat` que ya usan los recurrentes).
  - En vez del `mat-menu` de editar/eliminar, un botón/link "Ver cuenta" con `routerLink="/tesoreria/cuentas/{{ line.cuentaPorCobrarId }}"`.
- No requiere cambios en `editLine`/`removeLine`/`findEdit`: simplemente no se les da un botón que los invoque para estas líneas.

### Botón de actualización

En el header de `month-detail` (junto a los botones "Ingreso"/"Gasto") o en el dashboard: un botón "Actualizar cobros" (ícono `sync`, `[disabled]="store.syncing()"`) que llama a `store.syncCobros()`, más un texto pequeño de estado ("Sincronizado hace instantes" / el mensaje de `syncError()` si corresponde). Se decide la ubicación exacta al implementar, priorizando no saturar el header ya cargado de `month-detail`.

## Fuera de alcance

- No se persisten los cobros sincronizados en `localStorage` ni en ningún backend nuevo.
- No se sincronizan Compras, Caja Chica, ni ningún otro movimiento — solo `TransaccionPago` con `estado = 'CONFIRMADA'`.
- No se toca ni se avanza la spec de 2026-09-08 (migración completa a backend).
- No hay deduplicación automática si el usuario ya cargó a mano un ingreso proyectado para la misma venta: ambos se suman. Ajustar la proyección manual para no duplicar es responsabilidad del usuario.
- No funciona offline: sin conexión al backend, los cobros reales no aparecen esa sesión, pero el resto del Flujo de Caja (datos manuales) sigue funcionando igual que hoy.

## Testing

- `projection.spec.ts` (nuevo — hoy no existe ningún test para este motor): casos para la rama `synced` en `projectMonth`/`projectYear` — un cobro cae en el mes correcto, se suma a `totalIncome`/`operationalIncome`, y cae en `ORPHAN_CATEGORY` si `cat-ventas` no existe en `state.categories`.
- `cobros-sync.service.spec.ts` (nuevo): test de la función pura `mapPagoToSyncedIncome` (sin `HttpTestingController`).
- Verificación manual en navegador: registrar un pago en Tesorería, abrir Flujo de Caja, confirmar que aparece en el mes correcto con el ícono y el link a la cuenta, que suma bien al total, y que no tiene botones de editar/eliminar.
