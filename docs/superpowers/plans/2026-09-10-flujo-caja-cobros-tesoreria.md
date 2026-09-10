# Cobros de Tesorería en Flujo de Caja Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hacer que el Flujo de Caja sume automáticamente, como ingresos de solo lectura, los cobros confirmados que ya se registran en Tesorería > Historial de pagos.

**Architecture:** Un servicio Angular nuevo (`CobrosSyncService`) trae los pagos `CONFIRMADA` desde el endpoint que ya existe (`GET /api/tesoreria/pagos`) y los mapea a un tipo liviano `SyncedIncome`. El motor de proyección (`projection.ts`, función pura) los recibe como un cuarto insumo junto a los ingresos/gastos manuales (`recurring`/`oneOff`), sin que se persistan en el `localStorage` del Flujo de Caja. El store (`cashflow.store.ts`) orquesta la sincronización — automática al abrir el módulo, manual con un botón — y la UI del detalle mensual los muestra como ítems de solo lectura con un link de vuelta a Tesorería.

**Tech Stack:** Angular 18 standalone components + signals, RxJS, Jasmine/Karma (`ng test`).

**Spec:** `docs/superpowers/specs/2026-09-10-flujo-caja-cobros-tesoreria-design.md`

## Global Constraints

- Ningún paso de este plan genera commits — el commit se hace solo cuando el usuario lo pide explícitamente (spec, decisión #6). No incluyas pasos de `git commit` al ejecutar este plan.
- Sin cambios de backend: se reutiliza `GET /api/tesoreria/pagos` (`TransaccionPagoController`/`TransaccionPagoService`) tal como quedó implementado.
- No se persiste nada nuevo en el `localStorage` del Flujo de Caja — `STATE_VERSION` (`core/models.ts`) no cambia.
- Reutilizar `TransaccionPagoService` y `ClienteService` ya existentes en `frontend/src/app/core/services/` — no duplicar llamadas HTTP.
- Seguir el estilo visual ya usado en `month-detail.css` (tokens `var(--space-*)`, `var(--error-base)`, `var(--text-muted)`, etc.) sin introducir colores o componentes nuevos.
- Correr los tests con Chrome del sistema (no hay Chromium en el host fuera de Docker):
  ```bash
  cd frontend && CHROME_BIN="C:\Program Files\Google\Chrome\Application\chrome.exe" npx ng test --watch=false --browsers=ChromeHeadless --include='<ruta-del-spec>'
  ```
  Ajusta `--include` a la ruta del archivo `.spec.ts` de cada tarea.

---

### Task 1: `SyncedIncome` y la función de mapeo

**Files:**
- Create: `frontend/src/app/features/flujo-caja/core/synced-income.ts`
- Test: `frontend/src/app/features/flujo-caja/core/synced-income.spec.ts`

**Interfaces:**
- Consumes: `TransaccionPago` (`frontend/src/app/core/models/models.ts`, campos `id: number`, `cuentaPorCobrarId: number`, `ventaId: number`, `clienteId: number`, `fecha: string`, `monto: number`, más el resto de campos del pago); `MonthKey` (`./models`, alias de `string`).
- Produces: `interface SyncedIncome { id: string; month: MonthKey; description: string; amount: number; cuentaPorCobrarId: number }` y `function mapPagoToSyncedIncome(pago: TransaccionPago, nombreCliente: string): SyncedIncome` — los usan las Tasks 2 y 3.

- [ ] **Step 1: Escribe el test que falla**

Crea `frontend/src/app/features/flujo-caja/core/synced-income.spec.ts`:

```ts
import { TransaccionPago } from '../../../core/models/models';
import { mapPagoToSyncedIncome } from './synced-income';

function pagoDeEjemplo(overrides: Partial<TransaccionPago> = {}): TransaccionPago {
  return {
    id: 42,
    cuentaPorCobrarId: 7,
    ventaId: 26,
    clienteId: 5,
    fecha: '2026-09-10T19:37:00',
    monto: 70000,
    medioPago: 'TRANSFERENCIA',
    estado: 'CONFIRMADA',
    usuarioId: 1,
    observaciones: null,
    transferenciaBancoOrigen: null,
    transferenciaBancoDestino: null,
    transferenciaNumeroOperacion: null,
    transferenciaFecha: null,
    tarjetaEntidad: null,
    tarjetaTipo: null,
    tarjetaNumeroOperacion: null,
    tarjetaFecha: null,
    chequeBanco: null,
    chequeNumero: null,
    chequeFechaEmision: null,
    chequeFechaPago: null,
    ...overrides,
  };
}

describe('mapPagoToSyncedIncome', () => {
  it('arma el id con el prefijo pago-', () => {
    const resultado = mapPagoToSyncedIncome(pagoDeEjemplo({ id: 42 }), 'Abogado Pablo Fernández');
    expect(resultado.id).toBe('pago-42');
  });

  it('toma el mes desde los primeros 7 caracteres de la fecha', () => {
    const resultado = mapPagoToSyncedIncome(pagoDeEjemplo({ fecha: '2026-03-15T10:00:00' }), 'Cliente X');
    expect(resultado.month).toBe('2026-03');
  });

  it('arma la descripción con el nombre del cliente y el número de venta', () => {
    const resultado = mapPagoToSyncedIncome(pagoDeEjemplo({ ventaId: 26 }), 'Abogado Pablo Fernández');
    expect(resultado.description).toBe('Cobro Abogado Pablo Fernández — Venta V-26');
  });

  it('copia el monto y la cuenta por cobrar tal cual', () => {
    const resultado = mapPagoToSyncedIncome(pagoDeEjemplo({ monto: 70000, cuentaPorCobrarId: 7 }), 'Cliente X');
    expect(resultado.amount).toBe(70000);
    expect(resultado.cuentaPorCobrarId).toBe(7);
  });
});
```

- [ ] **Step 2: Corre el test y confirma que falla**

Run: `cd frontend && CHROME_BIN="C:\Program Files\Google\Chrome\Application\chrome.exe" npx ng test --watch=false --browsers=ChromeHeadless --include='src/app/features/flujo-caja/core/synced-income.spec.ts'`

Expected: FAIL — error de compilación `Cannot find module './synced-income'` (el archivo todavía no existe).

- [ ] **Step 3: Implementa `synced-income.ts`**

Crea `frontend/src/app/features/flujo-caja/core/synced-income.ts`:

```ts
import { MonthKey } from './models';
import { TransaccionPago } from '../../../core/models/models';

/** Un cobro confirmado en Tesorería, representado como ingreso del mes en el Flujo de Caja. */
export interface SyncedIncome {
  id: string;
  month: MonthKey;
  description: string;
  amount: number;
  cuentaPorCobrarId: number;
}

export function mapPagoToSyncedIncome(pago: TransaccionPago, nombreCliente: string): SyncedIncome {
  return {
    id: `pago-${pago.id}`,
    month: pago.fecha.slice(0, 7),
    description: `Cobro ${nombreCliente} — Venta V-${pago.ventaId}`,
    amount: pago.monto,
    cuentaPorCobrarId: pago.cuentaPorCobrarId,
  };
}
```

- [ ] **Step 4: Corre el test y confirma que pasa**

Run: mismo comando del Step 2.
Expected: PASS — 4 specs, 0 failures.

---

### Task 2: `CobrosSyncService`

**Files:**
- Create: `frontend/src/app/features/flujo-caja/core/cobros-sync.service.ts`

**Interfaces:**
- Consumes: `TransaccionPagoService.buscar(filtros: FiltrosHistorialPago): Observable<PaginaResponse<TransaccionPago>>` (`frontend/src/app/core/services/transaccion-pago.service.ts`); `ClienteService.listar(): Observable<Cliente[]>` (`frontend/src/app/core/services/cliente.service.ts`); `SyncedIncome`/`mapPagoToSyncedIncome` de la Task 1.
- Produces: `CobrosSyncService` (`providedIn: 'root'`) con `sincronizar(): Observable<SyncedIncome[]>` — lo usa la Task 4.

No hay test automatizado para esta tarea: la lógica de mapeo puro ya quedó cubierta en la Task 1, y el llamado HTTP orquestado se verifica manualmente en el navegador en la Task 5 (así lo define la spec, sección Testing). Esta tarea se verifica con `tsc` (type-check).

- [ ] **Step 1: Implementa el servicio**

Crea `frontend/src/app/features/flujo-caja/core/cobros-sync.service.ts`:

```ts
import { Injectable, inject } from '@angular/core';
import { Observable, firstValueFrom, from } from 'rxjs';
import { ClienteService } from '../../../core/services/cliente.service';
import { TransaccionPago } from '../../../core/models/models';
import { TransaccionPagoService } from '../../../core/services/transaccion-pago.service';
import { mapPagoToSyncedIncome, SyncedIncome } from './synced-income';

const TAMANO_PAGINA = 200;

@Injectable({ providedIn: 'root' })
export class CobrosSyncService {
  private readonly transaccionPagoService = inject(TransaccionPagoService);
  private readonly clienteService = inject(ClienteService);

  sincronizar(): Observable<SyncedIncome[]> {
    return from(this.cargarTodo());
  }

  private async cargarTodo(): Promise<SyncedIncome[]> {
    const [pagos, clientes] = await Promise.all([
      this.traerTodosLosConfirmados(),
      firstValueFrom(this.clienteService.listar()),
    ]);
    const nombrePorId = new Map(clientes.map((c) => [c.id, c.nombre]));
    return pagos.map((pago) =>
      mapPagoToSyncedIncome(pago, nombrePorId.get(pago.clienteId) ?? `Cliente #${pago.clienteId}`),
    );
  }

  /** Pagina hasta traer todos los pagos confirmados: `projection.ts` necesita el conjunto completo, no una página. */
  private async traerTodosLosConfirmados(): Promise<TransaccionPago[]> {
    const acumulado: TransaccionPago[] = [];
    let pagina = 0;
    for (;;) {
      const resp = await firstValueFrom(
        this.transaccionPagoService.buscar({ estado: 'CONFIRMADA', pagina, tamano: TAMANO_PAGINA }),
      );
      acumulado.push(...resp.contenido);
      if (acumulado.length >= resp.total || resp.contenido.length === 0) break;
      pagina++;
    }
    return acumulado;
  }
}
```

- [ ] **Step 2: Verifica que compila**

Run: `cd frontend && npx tsc -p tsconfig.app.json --noEmit`
Expected: sin errores.

---

### Task 3: `projection.ts` recibe los cobros sincronizados

**Files:**
- Modify: `frontend/src/app/features/flujo-caja/core/seed.ts`
- Modify: `frontend/src/app/features/flujo-caja/core/projection.ts`
- Test: `frontend/src/app/features/flujo-caja/core/projection.spec.ts` (nuevo — hoy no existe ningún test para este motor)

**Interfaces:**
- Consumes: `SyncedIncome` (Task 1).
- Produces: `Line.source` gana `'synced'`; `Line.cuentaPorCobrarId?: number`; `CATEGORIA_VENTAS_ID` exportado desde `seed.ts`; `projectMonth`/`projectMonths`/`projectThrough`/`projectYear` ganan un quinto/cuarto/tercer/tercer parámetro opcional `syncedIncomes: readonly SyncedIncome[] = []` respectivamente — la Task 4 llama a `projectYear(state, year, syncedIncomes)`.

- [ ] **Step 1: Escribe el test que falla**

Crea `frontend/src/app/features/flujo-caja/core/projection.spec.ts`:

```ts
import { CashflowState, OneOffItem } from './models';
import { CATEGORIA_VENTAS_ID, DEFAULT_CATEGORIES } from './seed';
import { projectMonth, projectYear } from './projection';
import { SyncedIncome } from './synced-income';

function estadoVacio(): CashflowState {
  return {
    version: 3,
    settings: { baseYear: 2026, openingBalance: 0, provisions: { taxRatePercent: 0, contingencyMonths: 0 } },
    categories: DEFAULT_CATEGORIES.map((c) => ({ ...c })),
    recurring: [],
    oneOff: [],
    overrides: [],
  };
}

function cobroDeEjemplo(overrides: Partial<SyncedIncome> = {}): SyncedIncome {
  return {
    id: 'pago-1',
    month: '2026-03',
    description: 'Cobro Cliente X — Venta V-10',
    amount: 50000,
    cuentaPorCobrarId: 3,
    ...overrides,
  };
}

describe('projectMonth con cobros sincronizados', () => {
  it('agrega el cobro como línea de ingreso del mes correspondiente', () => {
    const proyeccion = projectMonth(estadoVacio(), '2026-03', 0, 0, [cobroDeEjemplo()]);

    expect(proyeccion.incomes.length).toBe(1);
    expect(proyeccion.incomes[0].source).toBe('synced');
    expect(proyeccion.incomes[0].amount).toBe(50000);
    expect(proyeccion.incomes[0].cuentaPorCobrarId).toBe(3);
    expect(proyeccion.totalIncome).toBe(50000);
  });

  it('no agrega el cobro en un mes distinto al suyo', () => {
    const proyeccion = projectMonth(estadoVacio(), '2026-04', 0, 0, [cobroDeEjemplo({ month: '2026-03' })]);

    expect(proyeccion.incomes.length).toBe(0);
  });

  it('clasifica el cobro bajo la categoría Ventas existente', () => {
    const proyeccion = projectMonth(estadoVacio(), '2026-03', 0, 0, [cobroDeEjemplo()]);

    expect(proyeccion.incomes[0].categoryId).toBe(CATEGORIA_VENTAS_ID);
    expect(proyeccion.incomeByCategory.get(CATEGORIA_VENTAS_ID)).toBe(50000);
  });

  it('cuenta como ingreso operacional para el resultado del mes', () => {
    const proyeccion = projectMonth(estadoVacio(), '2026-03', 0, 0, [cobroDeEjemplo({ amount: 30000 })]);

    expect(proyeccion.operationalIncome).toBe(30000);
    expect(proyeccion.operationalNet).toBe(30000);
  });

  it('si la categoría Ventas fue borrada, cae en la clasificación genérica sin romper', () => {
    const estado = estadoVacio();
    estado.categories = estado.categories.filter((c) => c.id !== CATEGORIA_VENTAS_ID);

    const proyeccion = projectMonth(estado, '2026-03', 0, 0, [cobroDeEjemplo()]);

    expect(proyeccion.incomes[0].nature).toBe('operational');
    expect(proyeccion.incomes[0].variability).toBe('variable');
    expect(proyeccion.totalIncome).toBe(50000);
  });

  it('suma junto con los ingresos manuales del mismo mes sin reemplazarlos', () => {
    const estado = estadoVacio();
    const manual: OneOffItem = {
      id: 'one-1',
      kind: 'income',
      categoryId: CATEGORIA_VENTAS_ID,
      description: 'Venta proyectada',
      amount: 20000,
      month: '2026-03',
      interestAmount: null,
    };
    estado.oneOff = [manual];

    const proyeccion = projectMonth(estado, '2026-03', 0, 0, [cobroDeEjemplo({ amount: 50000 })]);

    expect(proyeccion.incomes.length).toBe(2);
    expect(proyeccion.totalIncome).toBe(70000);
  });
});

describe('projectYear con cobros sincronizados', () => {
  it('arrastra el efecto del cobro al saldo final de los meses siguientes', () => {
    const meses = projectYear(estadoVacio(), 2026, [cobroDeEjemplo({ month: '2026-01', amount: 10000 })]);

    expect(meses[0].closingBalance).toBe(10000);
    expect(meses[1].openingBalance).toBe(10000);
  });
});
```

- [ ] **Step 2: Corre el test y confirma que falla**

Run: `cd frontend && CHROME_BIN="C:\Program Files\Google\Chrome\Application\chrome.exe" npx ng test --watch=false --browsers=ChromeHeadless --include='src/app/features/flujo-caja/core/projection.spec.ts'`

Expected: FAIL — error de compilación (`CATEGORIA_VENTAS_ID` no existe en `./seed`, y `projectMonth`/`projectYear` no aceptan un quinto/tercer argumento todavía).

- [ ] **Step 3: Extrae `CATEGORIA_VENTAS_ID` en `seed.ts`**

En `frontend/src/app/features/flujo-caja/core/seed.ts`, reemplaza:

```ts
export const DEFAULT_CATEGORIES: Category[] = [
  {
    id: 'cat-ventas',
    name: 'Ventas',
    kind: 'income',
    nature: 'operational',
    variability: 'variable',
  },
```

por:

```ts
/** Categoría a la que caen los cobros sincronizados desde Tesorería (ver `core/projection.ts`). */
export const CATEGORIA_VENTAS_ID = 'cat-ventas';

export const DEFAULT_CATEGORIES: Category[] = [
  {
    id: CATEGORIA_VENTAS_ID,
    name: 'Ventas',
    kind: 'income',
    nature: 'operational',
    variability: 'variable',
  },
```

(el resto del archivo, desde el segundo elemento de `DEFAULT_CATEGORIES` en adelante, no cambia).

- [ ] **Step 4: Modifica `projection.ts`**

En `frontend/src/app/features/flujo-caja/core/projection.ts`:

Reemplaza el bloque de imports (líneas 1–11):

```ts
import { januaryOf, monthKeysFrom } from './month';
import {
  CashflowState,
  Category,
  MonthKey,
  Nature,
  Override,
  RecurringItem,
  Variability,
} from './models';
import { MONTHS_PER_YEAR } from './seed';
```

por:

```ts
import { januaryOf, monthKeysFrom } from './month';
import {
  CashflowState,
  Category,
  MonthKey,
  Nature,
  Override,
  RecurringItem,
  Variability,
} from './models';
import { CATEGORIA_VENTAS_ID, MONTHS_PER_YEAR } from './seed';
import { SyncedIncome } from './synced-income';
```

Reemplaza la interfaz `Line` (líneas 13–26):

```ts
export interface Line {
  /** Id del `RecurringItem` u `OneOffItem` que origina la línea. */
  id: string;
  source: 'recurring' | 'oneoff';
  description: string;
  categoryId: string;
  amount: number;
  /** `true` si un `Override` cambió el monto de la plantilla en este mes. */
  overridden: boolean;
  nature: Nature;
  variability: Variability;
  /** Parte de `amount` que es interés; el resto amortiza capital. */
  interestAmount: number | null;
}
```

por:

```ts
export interface Line {
  /** Id del `RecurringItem` u `OneOffItem` que origina la línea; para `source: 'synced'`, el id sintético `pago-<id>`. */
  id: string;
  source: 'recurring' | 'oneoff' | 'synced';
  description: string;
  categoryId: string;
  amount: number;
  /** `true` si un `Override` cambió el monto de la plantilla en este mes. */
  overridden: boolean;
  nature: Nature;
  variability: Variability;
  /** Parte de `amount` que es interés; el resto amortiza capital. */
  interestAmount: number | null;
  /** Solo en líneas `source: 'synced'`: la cuenta por cobrar de origen, para enlazar a Tesorería. */
  cuentaPorCobrarId?: number;
}
```

Reemplaza la firma de `projectMonth` (líneas 117–122):

```ts
export function projectMonth(
  state: CashflowState,
  month: MonthKey,
  openingBalance: number,
  accumulatedTaxProvision = 0,
): MonthProjection {
```

por:

```ts
export function projectMonth(
  state: CashflowState,
  month: MonthKey,
  openingBalance: number,
  accumulatedTaxProvision = 0,
  syncedIncomes: readonly SyncedIncome[] = [],
): MonthProjection {
```

Reemplaza el helper `push` (dentro de `projectMonth`):

```ts
  const push = (
    id: string,
    source: Line['source'],
    kind: 'income' | 'expense',
    categoryId: string,
    description: string,
    amount: number,
    overridden: boolean,
    interestAmount: number | null,
  ) => {
    const category = categories.get(categoryId) ?? ORPHAN_CATEGORY;
    const line: Line = {
      id,
      source,
      description,
      categoryId,
      amount,
      overridden,
      nature: category.nature,
      variability: category.variability,
      interestAmount,
    };
    (kind === 'income' ? incomes : expenses).push(line);
  };
```

por:

```ts
  const push = (
    id: string,
    source: Line['source'],
    kind: 'income' | 'expense',
    categoryId: string,
    description: string,
    amount: number,
    overridden: boolean,
    interestAmount: number | null,
    cuentaPorCobrarId?: number,
  ) => {
    const category = categories.get(categoryId) ?? ORPHAN_CATEGORY;
    const line: Line = {
      id,
      source,
      description,
      categoryId,
      amount,
      overridden,
      nature: category.nature,
      variability: category.variability,
      interestAmount,
      cuentaPorCobrarId,
    };
    (kind === 'income' ? incomes : expenses).push(line);
  };
```

Justo después del loop `for (const item of state.oneOff) { ... }` (y antes de `const totalIncome = ...`), agrega:

```ts
  for (const income of syncedIncomes) {
    if (income.month !== month) continue;
    push(
      income.id,
      'synced',
      'income',
      CATEGORIA_VENTAS_ID,
      income.description,
      income.amount,
      false,
      null,
      income.cuentaPorCobrarId,
    );
  }
```

Reemplaza `projectMonths`:

```ts
export function projectMonths(
  state: CashflowState,
  months: readonly MonthKey[],
  openingBalance: number,
): MonthProjection[] {
  const result: MonthProjection[] = [];
  let balance = openingBalance;
  let taxProvision = 0;
  for (const month of months) {
    const projection = projectMonth(state, month, balance, taxProvision);
    result.push(projection);
    balance = projection.closingBalance;
    taxProvision = projection.accumulatedTaxProvision;
  }
  return result;
}
```

por:

```ts
export function projectMonths(
  state: CashflowState,
  months: readonly MonthKey[],
  openingBalance: number,
  syncedIncomes: readonly SyncedIncome[] = [],
): MonthProjection[] {
  const result: MonthProjection[] = [];
  let balance = openingBalance;
  let taxProvision = 0;
  for (const month of months) {
    const projection = projectMonth(state, month, balance, taxProvision, syncedIncomes);
    result.push(projection);
    balance = projection.closingBalance;
    taxProvision = projection.accumulatedTaxProvision;
  }
  return result;
}
```

Reemplaza `projectThrough`:

```ts
export function projectThrough(state: CashflowState, throughYear: number): MonthProjection[] {
  const { baseYear, openingBalance } = state.settings;
  const years = Math.max(1, throughYear - baseYear + 1);
  const months = monthKeysFrom(januaryOf(baseYear), years * MONTHS_PER_YEAR);
  return projectMonths(state, months, openingBalance);
}
```

por:

```ts
export function projectThrough(
  state: CashflowState,
  throughYear: number,
  syncedIncomes: readonly SyncedIncome[] = [],
): MonthProjection[] {
  const { baseYear, openingBalance } = state.settings;
  const years = Math.max(1, throughYear - baseYear + 1);
  const months = monthKeysFrom(januaryOf(baseYear), years * MONTHS_PER_YEAR);
  return projectMonths(state, months, openingBalance, syncedIncomes);
}
```

Reemplaza `projectYear`:

```ts
export function projectYear(state: CashflowState, year: number): MonthProjection[] {
  return projectThrough(state, year).slice(-MONTHS_PER_YEAR);
}
```

por:

```ts
export function projectYear(
  state: CashflowState,
  year: number,
  syncedIncomes: readonly SyncedIncome[] = [],
): MonthProjection[] {
  return projectThrough(state, year, syncedIncomes).slice(-MONTHS_PER_YEAR);
}
```

- [ ] **Step 5: Corre el test y confirma que pasa**

Run: mismo comando del Step 2.
Expected: PASS — 7 specs, 0 failures.

---

### Task 4: `CashflowStore` sincroniza al abrir el módulo

**Files:**
- Modify: `frontend/src/app/features/flujo-caja/core/cashflow.store.ts`

**Interfaces:**
- Consumes: `CobrosSyncService.sincronizar()` (Task 2); `projectYear(state, year, syncedIncomes)` (Task 3).
- Produces: `CashflowStore.syncing: Signal<boolean>`, `CashflowStore.syncError: Signal<string | null>`, `CashflowStore.lastSyncedAt: Signal<Date | null>`, `CashflowStore.syncCobros(): void` — los usa la Task 5 (`month-detail.ts`).

Sin test automatizado nuevo (requeriría `TestBed` + `HttpTestingController`, fuera del alcance de testing que define la spec); se verifica con type-check y, en la Task 5, con el navegador.

- [ ] **Step 1: Modifica los imports**

En `frontend/src/app/features/flujo-caja/core/cashflow.store.ts`, reemplaza:

```ts
import { computed, effect, inject, Injectable, signal } from '@angular/core';
import {
  CashflowState,
  Category,
  ItemKind,
  MonthKey,
  Provisions,
  OneOffItem,
  RecurringItem,
  Settings,
} from './models';
import { currentMonthKey, currentYear, januaryOf, monthKeysFrom } from './month';
import { newId } from './id';
import { projectYear } from './projection';
import { sampleState } from './sample';
import { MONTHS_PER_YEAR, seedState } from './seed';
import { StorageService } from './storage.service';
```

por:

```ts
import { computed, effect, inject, Injectable, signal } from '@angular/core';
import {
  CashflowState,
  Category,
  ItemKind,
  MonthKey,
  Provisions,
  OneOffItem,
  RecurringItem,
  Settings,
} from './models';
import { currentMonthKey, currentYear, januaryOf, monthKeysFrom } from './month';
import { newId } from './id';
import { projectYear } from './projection';
import { sampleState } from './sample';
import { MONTHS_PER_YEAR, seedState } from './seed';
import { StorageService } from './storage.service';
import { CobrosSyncService } from './cobros-sync.service';
import { SyncedIncome } from './synced-income';
```

- [ ] **Step 2: Agrega las señales de sincronización y actualiza `projection`**

Reemplaza:

```ts
  private readonly storage = inject(StorageService);

  private readonly loaded = this.storage.load();

  private readonly _state = signal<CashflowState>(this.loaded.state);
  /** `true` si al iniciar había datos guardados ilegibles. */
  readonly loadCorrupted = signal(this.loaded.corrupted);
```

por:

```ts
  private readonly storage = inject(StorageService);
  private readonly cobrosSyncService = inject(CobrosSyncService);

  private readonly loaded = this.storage.load();

  private readonly _state = signal<CashflowState>(this.loaded.state);
  /** `true` si al iniciar había datos guardados ilegibles. */
  readonly loadCorrupted = signal(this.loaded.corrupted);

  private readonly _syncedIncomes = signal<SyncedIncome[]>([]);
  /** `true` mientras se está pidiendo el detalle de cobros a Tesorería. */
  readonly syncing = signal(false);
  /** Mensaje a mostrar si la última sincronización falló; `null` si no hay error. */
  readonly syncError = signal<string | null>(null);
  /** Momento de la última sincronización exitosa; `null` si nunca se hizo. */
  readonly lastSyncedAt = signal<Date | null>(null);
```

Luego reemplaza:

```ts
  readonly months = computed(() => monthKeysFrom(januaryOf(this._year()), MONTHS_PER_YEAR));
  readonly projection = computed(() => projectYear(this._state(), this._year()));
```

por:

```ts
  readonly months = computed(() => monthKeysFrom(januaryOf(this._year()), MONTHS_PER_YEAR));
  readonly projection = computed(() =>
    projectYear(this._state(), this._year(), this._syncedIncomes()),
  );
```

- [ ] **Step 3: Sincroniza al construir el store y agrega `syncCobros()`**

Reemplaza:

```ts
  constructor() {
    effect(() => this.storage.save(this._state()));
  }
```

por:

```ts
  constructor() {
    effect(() => this.storage.save(this._state()));
    this.syncCobros();
  }

  /** Vuelve a pedir los cobros confirmados a Tesorería y los mezcla en la proyección. */
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

- [ ] **Step 4: Verifica que compila**

Run: `cd frontend && npx tsc -p tsconfig.app.json --noEmit`
Expected: sin errores.

---

### Task 5: Mostrar los cobros sincronizados en el detalle del mes

**Files:**
- Modify: `frontend/src/app/features/flujo-caja/month/month-detail.ts`
- Modify: `frontend/src/app/features/flujo-caja/month/month-detail.html`
- Modify: `frontend/src/app/features/flujo-caja/month/month-detail.css`

**Interfaces:**
- Consumes: `store.syncing`, `store.syncError`, `store.syncCobros()` (Task 4); `Line.source === 'synced'`, `Line.cuentaPorCobrarId` (Task 3).

Sin test automatizado (componente de UI; la spec define verificación manual en navegador para esta parte).

- [ ] **Step 1: Actualiza los imports y el arreglo `imports` del componente**

En `frontend/src/app/features/flujo-caja/month/month-detail.ts`, reemplaza:

```ts
import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
```

por:

```ts
import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
```

Reemplaza el arreglo `imports` del `@Component`:

```ts
  imports: [
    FormsModule,
    NgTemplateOutlet,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatMenuModule,
    MatTooltipModule,
    ClpPipe,
    MonthLabelPipe,
  ],
```

por:

```ts
  imports: [
    FormsModule,
    NgTemplateOutlet,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatMenuModule,
    MatTooltipModule,
    ClpPipe,
    MonthLabelPipe,
  ],
```

- [ ] **Step 2: Expone el estado de sincronización y el método del botón**

Reemplaza:

```ts
  readonly projection = this.store.currentProjection;

  readonly status = computed(() => monthStatus(this.projection()));
```

por:

```ts
  readonly projection = this.store.currentProjection;
  readonly syncing = this.store.syncing;
  readonly syncError = this.store.syncError;

  readonly status = computed(() => monthStatus(this.projection()));
```

Reemplaza:

```ts
  stepYear(delta: number): void {
    this.store.stepYear(delta);
  }
```

por:

```ts
  stepYear(delta: number): void {
    this.store.stepYear(delta);
  }

  actualizarCobros(): void {
    this.store.syncCobros();
  }
```

- [ ] **Step 3: Agrega el botón "Actualizar cobros" y el mensaje de error**

En `frontend/src/app/features/flujo-caja/month/month-detail.html`, reemplaza:

```html
  <div class="month-actions">
    <button type="button" mat-stroked-button (click)="addItem('income')">
      <mat-icon>trending_up</mat-icon>
      Ingreso
    </button>
    <button type="button" mat-flat-button color="primary" (click)="addItem('expense')">
      <mat-icon>trending_down</mat-icon>
      Gasto
    </button>
  </div>
</header>
```

por:

```html
  <div class="month-actions">
    <button
      type="button"
      mat-stroked-button
      [disabled]="syncing()"
      (click)="actualizarCobros()"
      matTooltip="Vuelve a traer los cobros confirmados desde Tesorería"
    >
      <mat-icon>sync</mat-icon>
      {{ syncing() ? 'Sincronizando...' : 'Actualizar cobros' }}
    </button>
    <button type="button" mat-stroked-button (click)="addItem('income')">
      <mat-icon>trending_up</mat-icon>
      Ingreso
    </button>
    <button type="button" mat-flat-button color="primary" (click)="addItem('expense')">
      <mat-icon>trending_down</mat-icon>
      Gasto
    </button>
  </div>
</header>

@if (syncError()) {
  <p class="sync-error">{{ syncError() }}</p>
}
```

- [ ] **Step 4: Distingue las líneas `synced` en la lista de ítems**

En el mismo archivo, reemplaza el bloque completo desde `<ng-template #groupTpl` hasta `</ng-template>`:

```html
<ng-template #groupTpl let-group let-kind="kind">
  <div class="group">
    <div class="group-head">
      <span class="group-name">{{ group.name }}</span>
      <span class="group-total" [class.income-text]="kind === 'income'">
        {{ group.total | clp }}
      </span>
    </div>

    @for (line of group.lines; track line.source + line.id) {
      <div class="line">
        <div class="line-main">
          <span class="line-description">{{ line.description }}</span>
          @if (line.source === 'recurring') {
            <mat-icon class="line-badge" matTooltip="Se repite todos los meses">repeat</mat-icon>
          }
          @if (line.overridden) {
            <span class="line-chip" matTooltip="Monto ajustado sólo para este mes">ajustado</span>
          }
        </div>

        <span class="line-amount">{{ line.amount | clp }}</span>

        <button type="button" mat-icon-button [matMenuTriggerFor]="menu" aria-label="Acciones del movimiento">
          <mat-icon>more_vert</mat-icon>
        </button>

        <mat-menu #menu="matMenu">
          @if (line.source === 'recurring') {
            <button mat-menu-item (click)="editThisMonthOnly(line)">
              <mat-icon>edit_calendar</mat-icon>
              <span>Editar sólo este mes</span>
            </button>
            <button mat-menu-item (click)="editLine(line)">
              <mat-icon>edit</mat-icon>
              <span>Editar la serie</span>
            </button>
            @if (line.overridden) {
              <button mat-menu-item (click)="restoreTemplate(line)">
                <mat-icon>restart_alt</mat-icon>
                <span>Restaurar valor de la serie</span>
              </button>
            }
            <button mat-menu-item (click)="skipThisMonth(line)">
              <mat-icon>event_busy</mat-icon>
              <span>Omitir este mes</span>
            </button>
            <button mat-menu-item (click)="removeLine(line)">
              <mat-icon>delete</mat-icon>
              <span>Eliminar la serie</span>
            </button>
          } @else {
            <button mat-menu-item (click)="editLine(line)">
              <mat-icon>edit</mat-icon>
              <span>Editar</span>
            </button>
            <button mat-menu-item (click)="removeLine(line)">
              <mat-icon>delete</mat-icon>
              <span>Eliminar</span>
            </button>
          }
        </mat-menu>
      </div>
    }
  </div>
</ng-template>
```

por:

```html
<ng-template #groupTpl let-group let-kind="kind">
  <div class="group">
    <div class="group-head">
      <span class="group-name">{{ group.name }}</span>
      <span class="group-total" [class.income-text]="kind === 'income'">
        {{ group.total | clp }}
      </span>
    </div>

    @for (line of group.lines; track line.source + line.id) {
      <div class="line">
        <div class="line-main">
          <span class="line-description">{{ line.description }}</span>
          @if (line.source === 'recurring') {
            <mat-icon class="line-badge" matTooltip="Se repite todos los meses">repeat</mat-icon>
          }
          @if (line.source === 'synced') {
            <mat-icon class="line-badge" matTooltip="Cobro sincronizado desde Tesorería">sync</mat-icon>
          }
          @if (line.overridden) {
            <span class="line-chip" matTooltip="Monto ajustado sólo para este mes">ajustado</span>
          }
        </div>

        <span class="line-amount">{{ line.amount | clp }}</span>

        @if (line.source === 'synced') {
          <a
            mat-icon-button
            [routerLink]="['/tesoreria/cuentas', line.cuentaPorCobrarId]"
            aria-label="Ver cuenta en Tesorería"
            matTooltip="Ver cuenta en Tesorería"
          >
            <mat-icon>open_in_new</mat-icon>
          </a>
        } @else {
          <button type="button" mat-icon-button [matMenuTriggerFor]="menu" aria-label="Acciones del movimiento">
            <mat-icon>more_vert</mat-icon>
          </button>

          <mat-menu #menu="matMenu">
            @if (line.source === 'recurring') {
              <button mat-menu-item (click)="editThisMonthOnly(line)">
                <mat-icon>edit_calendar</mat-icon>
                <span>Editar sólo este mes</span>
              </button>
              <button mat-menu-item (click)="editLine(line)">
                <mat-icon>edit</mat-icon>
                <span>Editar la serie</span>
              </button>
              @if (line.overridden) {
                <button mat-menu-item (click)="restoreTemplate(line)">
                  <mat-icon>restart_alt</mat-icon>
                  <span>Restaurar valor de la serie</span>
                </button>
              }
              <button mat-menu-item (click)="skipThisMonth(line)">
                <mat-icon>event_busy</mat-icon>
                <span>Omitir este mes</span>
              </button>
              <button mat-menu-item (click)="removeLine(line)">
                <mat-icon>delete</mat-icon>
                <span>Eliminar la serie</span>
              </button>
            } @else {
              <button mat-menu-item (click)="editLine(line)">
                <mat-icon>edit</mat-icon>
                <span>Editar</span>
              </button>
              <button mat-menu-item (click)="removeLine(line)">
                <mat-icon>delete</mat-icon>
                <span>Eliminar</span>
              </button>
            }
          </mat-menu>
        }
      </div>
    }
  </div>
</ng-template>
```

- [ ] **Step 5: Agrega el estilo del mensaje de error**

En `frontend/src/app/features/flujo-caja/month/month-detail.css`, justo después del bloque `.month-actions { ... }`, agrega:

```css
.sync-error {
  color: var(--error-base);
  font: var(--font-body-sm);
  margin: calc(-1 * var(--space-3)) 0 var(--space-5);
}
```

- [ ] **Step 6: Verifica que compila**

Run: `cd frontend && npx tsc -p tsconfig.app.json --noEmit`
Expected: sin errores.

Run: `cd frontend && npx ng build --configuration development`
Expected: `Application bundle generation complete`, sin errores.

- [ ] **Step 7: Verificación manual en navegador**

1. Reconstruye el contenedor: desde la raíz del repo, `docker compose build frontend && docker compose up -d frontend`.
2. Abre `http://localhost:4200/tesoreria/historial`, anota la fecha (mes), monto y cliente de cualquier pago con estado "Confirmada".
3. Abre `http://localhost:4200/flujo-caja`, navega al mes anotado.
4. Confirma:
   - Aparece una línea de ingreso con esa descripción y monto, con el ícono de sincronización (tooltip "Cobro sincronizado desde Tesorería").
   - Al pasar el mouse por esa línea no hay menú de editar/eliminar — solo el botón "Ver cuenta" (ícono `open_in_new`), y que al hacer clic navega a `/tesoreria/cuentas/<id>`.
   - El total de ingresos del mes incluye ese monto.
   - El botón "Actualizar cobros" del header muestra "Sincronizando..." brevemente y vuelve a "Actualizar cobros".

---

## Self-Review

- **Cobertura de la spec:** sección 1 (`CobrosSyncService`) → Task 2; sección 3 (`projection.ts`) → Task 3; sección 4 (`cashflow.store.ts`) → Task 4; sección 5 (UI) → Task 5; "Fuera de alcance" no requiere tareas (son exclusiones); "Testing" → cubierto por Tasks 1 y 3 (automatizado) y el Step 7 de la Task 5 (manual).
- **Placeholders:** ninguno — cada paso trae el código completo a escribir.
- **Consistencia de tipos:** `SyncedIncome` (Task 1) se usa igual en `CobrosSyncService.sincronizar()` (Task 2), en las firmas de `projection.ts` (Task 3) y en `CashflowStore._syncedIncomes`/`syncCobros()` (Task 4). `Line.cuentaPorCobrarId` (Task 3) es el mismo campo que consume `month-detail.html` (Task 5). `CATEGORIA_VENTAS_ID` se define una sola vez en `seed.ts` (Task 3) y se usa desde `projection.ts` y desde los tests.
