# Cuentas por Pagar — Frontend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the frontend for Cuentas por Pagar — four screens (list, detail with payment registration, per-supplier statement, payment history), their models/services, routes, and nav entries — consuming the backend REST endpoints already built and merged in `docs/superpowers/plans/2026-09-21-cuentas-por-pagar-backend.md`.

**Architecture:** New `frontend/src/app/features/cuentas-por-pagar/` feature, a near-exact mirror of the existing `frontend/src/app/features/tesoreria/` (Cuentas por Cobrar): `CuentasPorPagarComponent` mirrors `TesoreriaCuentasComponent`, `CuentaPorPagarDetalleComponent` mirrors `TesoreriaCuentaDetalleComponent`, `ProveedorDetalleComponent` mirrors `TesoreriaClienteDetalleComponent`, `PagosCompraHistorialComponent` mirrors `TesoreriaHistorialComponent`. New services `cuenta-por-pagar.service.ts`/`transaccion-pago-compra.service.ts` mirror `cuenta-por-cobrar.service.ts`/`transaccion-pago.service.ts`. The one structural difference from the mirror: `CuentaPorPagar` has two possible origins (compra/gasto), so the list screen adds an `origen` filter and a `categoriaGastoId` filter (both purely client-side — the backend's `listar()` has no pagination and no `origen` parameter, by design, since this feature has no consumer yet; see the backend plan's parked findings), and the detail screen resolves either a `Proveedor` or a `CategoriaGasto` depending on origin instead of always a client.

**Tech Stack:** Angular standalone components, Angular Material (`MatCard`, `MatButton`, `MatIcon`, `MatPaginator`), template-driven forms (`NgForm`/`ngModel`).

**Spec:** `docs/superpowers/specs/2026-09-21-cuentas-por-pagar-design.md`, section "5. Frontend: frontend/src/app/features/cuentas-por-pagar/". The backend plan (`2026-09-21-cuentas-por-pagar-backend.md`) is also load-bearing here — its parked findings (`listar`/`resumen` load the full table, no pagination; `TransaccionPagoCompraService.buscar()` has no free-text search) directly shape this plan's design: **do not build a frontend that assumes a `buscar(filtros)` on `CuentaPorPagarService` or a text-search box on the payments historial — the backend does not have either.**

## Global Constraints

- `id` is always database-generated — this plan never sends one.
- Reuse `TESORERIA_VER`/`TESORERIA_EDITAR`/`TESORERIA_ANULAR` via `AuthService.tienePermiso(...)` — no new permissions.
- No unit-test convention exists for Angular components in this codebase — verification is TypeScript compiling cleanly (`npx tsc --noEmit` from `frontend/`) plus a manual browser walkthrough, not a test suite.
- Reuse the global CSS classes already defined in `frontend/src/styles/_components.scss` (`.form-panel`, `.form-group`, `.form-grid`, `.page-header`, `.page-error`, `.empty-state`, `.field-error`, `.required-mark`, `.hint`, `.form-section-hint`, `.tag`/`.tag--success`/`.tag--warning`/`.tag--error`) — never redefine them locally. The Tesorería-specific classes (`.kpi-grid`, `.kpi-card`, `.historial-table`, `.filtros-grid`, `.clickable-row`, `.toggle-row`, `.two-col`, `.totals-group`/`.totals-row`, `.panel-header-row`/`.panel-actions`, `.inline-form`, `.tipo-grid`/`.tipo-card`, `.right`) are **component-scoped, not global** — every `tesoreria-*.component.scss` file already duplicates them, and this plan follows that exact existing convention rather than introducing a shared partial.
- `CuentaPorPagarService.listar()` accepts `proveedorId`/`categoriaGastoId`/`estado` but only honors ONE at a time (an if/else chain on the backend, mirroring `CuentaPorCobrarService.listar()`'s identical limitation) — this plan's list screen therefore fetches the full unfiltered list once and does all filtering/grouping client-side, exactly like `TesoreriaCuentasComponent` already does.
- No git commands that mutate the repository may run except the explicit "Commit" steps below — unless the user explicitly asks for it in the moment.
- Backend must already be running the merged `2026-09-21-cuentas-por-pagar-backend.md` plan's endpoints for the manual verification step in Task 6 to work.

---

## Task 1: Frontend models and services

**Files:**
- Modify: `frontend/src/app/core/models/models.ts`
- Create: `frontend/src/app/core/services/cuenta-por-pagar.service.ts`
- Create: `frontend/src/app/core/services/transaccion-pago-compra.service.ts`

**Interfaces:**
- Produces: `CuentaPorPagar`, `TransaccionPagoCompra`, `EstadoCuentaPorPagar`, `ResumenCuentasPorPagar` model types; `CuentaPorPagarService`, `TransaccionPagoCompraService` — consumed by Tasks 2–5.

No test suite for this task — verification is `npx tsc --noEmit` from `frontend/`.

- [ ] **Step 1: Add the model types**

Add to `frontend/src/app/core/models/models.ts`, near the existing `EstadoCuentaPorCobrar`/`CuentaPorCobrar`/`TransaccionPago`/`ResumenTesoreria` types:
```typescript
export type EstadoCuentaPorPagar = 'DEUDA' | 'PARCIAL' | 'PAGADO' | 'ANULADO';

export interface CuentaPorPagar {
  id: number;
  compraId: number | null;
  gastoId: number | null;
  proveedorId: number | null;
  categoriaGastoId: number | null;
  descripcion: string;
  fechaGeneracion: string;
  montoTotal: number;
  montoPagado: number;
  saldoPendiente: number;
  estado: EstadoCuentaPorPagar;
  fechaUltimoPago: string | null;
  observaciones: string | null;
  usuarioAnuloId: number | null;
  fechaAnulacion: string | null;
  motivoAnulacion: string | null;
}

export interface TransaccionPagoCompra {
  id: number;
  cuentaPorPagarId: number;
  compraId: number | null;
  gastoId: number | null;
  fecha: string;
  monto: number;
  medioPago: MedioPago;
  estado: EstadoTransaccion;
  usuarioId: number | null;
  observaciones: string | null;
  transferenciaBancoOrigen: string | null;
  transferenciaBancoDestino: string | null;
  transferenciaNumeroOperacion: string | null;
  transferenciaFecha: string | null;
  tarjetaEntidad: string | null;
  tarjetaTipo: string | null;
  tarjetaNumeroOperacion: string | null;
  tarjetaFecha: string | null;
  chequeBanco: string | null;
  chequeNumero: string | null;
  chequeFechaEmision: string | null;
  chequeFechaPago: string | null;
  usuarioAnuloId: number | null;
  fechaAnulacion: string | null;
  motivoAnulacion: string | null;
}

export interface ResumenCuentasPorPagar {
  totalPorPagar: number;
  totalPagado: number;
  saldoPendiente: number;
  cuentasEnDeuda: number;
  cuentasParciales: number;
  cuentasPagadas: number;
}
```
`MedioPago` and `EstadoTransaccion` already exist in this file (used by the existing `TransaccionPago` interface) — reuse them, do not redeclare.

- [ ] **Step 2: Write `cuenta-por-pagar.service.ts`**

```typescript
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CuentaPorPagar, EstadoCuentaPorPagar, ResumenCuentasPorPagar } from '../models/models';

@Injectable({ providedIn: 'root' })
export class CuentaPorPagarService {
  private readonly base = `${environment.apiUrl}/tesoreria/cuentas-por-pagar`;

  constructor(private http: HttpClient) {}

  listar(proveedorId?: number, categoriaGastoId?: number, estado?: EstadoCuentaPorPagar): Observable<CuentaPorPagar[]> {
    const params: Record<string, string> = {};
    if (proveedorId) params['proveedorId'] = String(proveedorId);
    if (categoriaGastoId) params['categoriaGastoId'] = String(categoriaGastoId);
    if (estado) params['estado'] = estado;
    return this.http.get<CuentaPorPagar[]>(this.base, { params });
  }

  resumen(): Observable<ResumenCuentasPorPagar> {
    return this.http.get<ResumenCuentasPorPagar>(`${this.base}/resumen`);
  }

  obtener(id: number): Observable<CuentaPorPagar> {
    return this.http.get<CuentaPorPagar>(`${this.base}/${id}`);
  }

  anular(id: number, motivo: string): Observable<CuentaPorPagar> {
    return this.http.post<CuentaPorPagar>(`${this.base}/${id}/anular`, { motivo });
  }
}
```

- [ ] **Step 3: Write `transaccion-pago-compra.service.ts`**

```typescript
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { EstadoTransaccion, MedioPago, PaginaResponse, TransaccionPagoCompra } from '../models/models';

export interface TransaccionPagoCompraRequest {
  monto: number;
  medioPago: MedioPago;
  observaciones?: string;
  transferenciaBancoOrigen?: string;
  transferenciaBancoDestino?: string;
  transferenciaNumeroOperacion?: string;
  transferenciaFecha?: string;
  tarjetaEntidad?: string;
  tarjetaTipo?: string;
  tarjetaNumeroOperacion?: string;
  tarjetaFecha?: string;
  chequeBanco?: string;
  chequeNumero?: string;
  chequeFechaEmision?: string;
  chequeFechaPago?: string;
}

export interface FiltrosHistorialPagoCompra {
  estado?: EstadoTransaccion;
  medioPago?: MedioPago;
  fechaDesde?: string;
  fechaHasta?: string;
  pagina?: number;
  tamano?: number;
}

@Injectable({ providedIn: 'root' })
export class TransaccionPagoCompraService {
  private readonly base = `${environment.apiUrl}/tesoreria`;

  constructor(private http: HttpClient) {}

  registrarPago(cuentaId: number, request: TransaccionPagoCompraRequest): Observable<TransaccionPagoCompra> {
    return this.http.post<TransaccionPagoCompra>(`${this.base}/cuentas-por-pagar/${cuentaId}/pagos`, request);
  }

  listarPorCuenta(cuentaId: number): Observable<TransaccionPagoCompra[]> {
    return this.http.get<TransaccionPagoCompra[]>(`${this.base}/cuentas-por-pagar/${cuentaId}/pagos`);
  }

  buscar(filtros: FiltrosHistorialPagoCompra): Observable<PaginaResponse<TransaccionPagoCompra>> {
    const params: Record<string, string> = {
      pagina: String(filtros.pagina ?? 0),
      tamano: String(filtros.tamano ?? 10),
    };
    if (filtros.estado) params['estado'] = filtros.estado;
    if (filtros.medioPago) params['medioPago'] = filtros.medioPago;
    if (filtros.fechaDesde) params['fechaDesde'] = filtros.fechaDesde;
    if (filtros.fechaHasta) params['fechaHasta'] = filtros.fechaHasta;
    return this.http.get<PaginaResponse<TransaccionPagoCompra>>(`${this.base}/pagos-compra`, { params });
  }

  anular(id: number, motivo: string): Observable<TransaccionPagoCompra> {
    return this.http.post<TransaccionPagoCompra>(`${this.base}/pagos-compra/${id}/anular`, { motivo });
  }
}
```

- [ ] **Step 4: Verify TypeScript compiles**

```bash
cd frontend && npx tsc --noEmit
```
Expected: no errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/core/models/models.ts \
  frontend/src/app/core/services/cuenta-por-pagar.service.ts \
  frontend/src/app/core/services/transaccion-pago-compra.service.ts
git commit -m "feat: modelos y servicios frontend de Cuentas por Pagar"
```

---

## Task 2: `CuentasPorPagarComponent` (main list)

**Files:**
- Create: `frontend/src/app/features/cuentas-por-pagar/cuentas-por-pagar.component.ts`
- Create: `frontend/src/app/features/cuentas-por-pagar/cuentas-por-pagar.component.html`
- Create: `frontend/src/app/features/cuentas-por-pagar/cuentas-por-pagar.component.scss`

**Interfaces:**
- Consumes: `CuentaPorPagarService` (Task 1), existing `ProveedorService` (`frontend/src/app/core/services/proveedor.service.ts`), existing `CategoriaGastoService` (`frontend/src/app/core/services/categoria-gasto.service.ts`, from the merged Módulo de Gastos).
- Produces: `CuentasPorPagarComponent`, standalone, selector `app-cuentas-por-pagar` — routed in Task 6.

Mirrors `frontend/src/app/features/tesoreria/tesoreria-cuentas.component.ts`: KPI resumen cards, a "deuda por proveedor" grouped table (compra-origin cuentas only — a `CuentaPorPagar` from a `Gasto` has no `proveedorId`), a toggle to reveal the full filterable table.

- [ ] **Step 1: Write the component class**

```typescript
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { CategoriaGasto, CuentaPorPagar, EstadoCuentaPorPagar, Proveedor, ResumenCuentasPorPagar } from '../../core/models/models';
import { CuentaPorPagarService } from '../../core/services/cuenta-por-pagar.service';
import { ProveedorService } from '../../core/services/proveedor.service';
import { CategoriaGastoService } from '../../core/services/categoria-gasto.service';
import { AuthService } from '../../core/services/auth.service';
import { MonedaPipe } from '../../core/pipes/moneda.pipe';

type Origen = 'COMPRA' | 'GASTO';

const ETIQUETAS: Record<EstadoCuentaPorPagar, string> = {
  DEUDA: 'En deuda',
  PARCIAL: 'Parcial',
  PAGADO: 'Pagado',
  ANULADO: 'Anulado',
};

const TAGS: Record<EstadoCuentaPorPagar, string> = {
  DEUDA: 'tag--error',
  PARCIAL: 'tag--warning',
  PAGADO: 'tag--success',
  ANULADO: '',
};

export interface DeudaProveedor {
  proveedorId: number;
  nombre: string;
  cantidadCuentas: number;
  saldoPendiente: number;
}

@Component({
  selector: 'app-cuentas-por-pagar',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, MatButtonModule, MatIconModule, MatCardModule, MonedaPipe],
  templateUrl: './cuentas-por-pagar.component.html',
  styleUrl: './cuentas-por-pagar.component.scss',
})
export class CuentasPorPagarComponent implements OnInit {
  cuentas: CuentaPorPagar[] = [];
  proveedores: Proveedor[] = [];
  categorias: CategoriaGasto[] = [];
  resumen: ResumenCuentasPorPagar | null = null;
  cargando = true;

  filtroOrigen: Origen | null = null;
  filtroProveedor: number | null = null;
  filtroCategoriaGasto: number | null = null;
  filtroEstado: EstadoCuentaPorPagar | null = null;
  filtroTexto = '';
  mostrarTodas = false;

  constructor(
    private cuentaPorPagarService: CuentaPorPagarService,
    private proveedorService: ProveedorService,
    private categoriaGastoService: CategoriaGastoService,
    public auth: AuthService
  ) {}

  ngOnInit(): void {
    this.proveedorService.listar().subscribe((data) => (this.proveedores = data));
    this.categoriaGastoService.listar().subscribe((data) => (this.categorias = data));
    this.cuentaPorPagarService.resumen().subscribe((data) => (this.resumen = data));
    this.cargar();
  }

  cargar(): void {
    this.cargando = true;
    this.cuentaPorPagarService.listar().subscribe({
      next: (data) => {
        this.cuentas = data;
        this.cargando = false;
      },
      error: () => (this.cargando = false),
    });
  }

  get deudaPorProveedor(): DeudaProveedor[] {
    const porProveedor = new Map<number, DeudaProveedor>();
    for (const c of this.cuentas) {
      if (c.compraId === null || c.proveedorId === null) continue;
      if (c.estado !== 'DEUDA' && c.estado !== 'PARCIAL') continue;
      const entrada = porProveedor.get(c.proveedorId) ?? {
        proveedorId: c.proveedorId,
        nombre: this.nombreProveedor(c.proveedorId),
        cantidadCuentas: 0,
        saldoPendiente: 0,
      };
      entrada.cantidadCuentas += 1;
      entrada.saldoPendiente += c.saldoPendiente;
      porProveedor.set(c.proveedorId, entrada);
    }
    return Array.from(porProveedor.values()).sort((a, b) => b.saldoPendiente - a.saldoPendiente);
  }

  get cuentasFiltradas(): CuentaPorPagar[] {
    const texto = this.filtroTexto.trim().toLowerCase();
    return this.cuentas.filter((c) => {
      if (this.filtroOrigen === 'COMPRA' && c.compraId === null) return false;
      if (this.filtroOrigen === 'GASTO' && c.gastoId === null) return false;
      if (this.filtroProveedor && c.proveedorId !== this.filtroProveedor) return false;
      if (this.filtroCategoriaGasto && c.categoriaGastoId !== this.filtroCategoriaGasto) return false;
      if (this.filtroEstado && c.estado !== this.filtroEstado) return false;
      if (texto && !c.descripcion.toLowerCase().includes(texto)) return false;
      return true;
    });
  }

  origenDe(c: CuentaPorPagar): Origen {
    return c.compraId !== null ? 'COMPRA' : 'GASTO';
  }

  nombreProveedor(id: number): string {
    return this.proveedores.find((p) => p.id === id)?.nombre ?? `Proveedor #${id}`;
  }

  etiquetaEstado(estado: EstadoCuentaPorPagar): string {
    return ETIQUETAS[estado];
  }

  tagEstado(estado: EstadoCuentaPorPagar): string {
    return TAGS[estado];
  }

  puedeRegistrarPago(cuenta: CuentaPorPagar): boolean {
    return cuenta.estado === 'DEUDA' || cuenta.estado === 'PARCIAL';
  }
}
```

- [ ] **Step 2: Write the template**

```html
<div class="page-header">
  <h1>Cuentas por pagar</h1>
  <a mat-stroked-button routerLink="/cuentas-por-pagar/historial">
    <mat-icon>history</mat-icon>
    Historial de pagos
  </a>
</div>

@if (resumen) {
  <section class="kpi-grid">
    <mat-card class="kpi-card">
      <span class="kpi-label">Total por pagar</span>
      <span class="kpi-value">{{ resumen.totalPorPagar | moneda }}</span>
    </mat-card>
    <mat-card class="kpi-card">
      <span class="kpi-label">Total pagado</span>
      <span class="kpi-value">{{ resumen.totalPagado | moneda }}</span>
    </mat-card>
    <mat-card class="kpi-card">
      <span class="kpi-label">Saldo pendiente</span>
      <span class="kpi-value">{{ resumen.saldoPendiente | moneda }}</span>
    </mat-card>
    <mat-card class="kpi-card">
      <span class="kpi-label">En deuda</span>
      <span class="kpi-value">{{ resumen.cuentasEnDeuda }}</span>
    </mat-card>
    <mat-card class="kpi-card">
      <span class="kpi-label">Parciales</span>
      <span class="kpi-value">{{ resumen.cuentasParciales }}</span>
    </mat-card>
    <mat-card class="kpi-card">
      <span class="kpi-label">Pagadas</span>
      <span class="kpi-value">{{ resumen.cuentasPagadas }}</span>
    </mat-card>
  </section>
}

@if (deudaPorProveedor.length) {
  <mat-card class="form-panel">
    <h2>Deuda por proveedor</h2>
    <p class="form-section-hint">Haz clic en un proveedor para ver el detalle de su cuenta corriente.</p>
    <table class="historial-table">
      <thead>
        <tr>
          <th>Proveedor</th>
          <th class="right">Cuentas en deuda</th>
          <th class="right">Saldo total adeudado</th>
          <th class="right"></th>
        </tr>
      </thead>
      <tbody>
        @for (d of deudaPorProveedor; track d.proveedorId) {
          <tr class="clickable-row" [routerLink]="['/cuentas-por-pagar/proveedores', d.proveedorId]">
            <td>{{ d.nombre }}</td>
            <td class="right">{{ d.cantidadCuentas }}</td>
            <td class="right">{{ d.saldoPendiente | moneda }}</td>
            <td class="right"><mat-icon class="clickable-row__chevron">chevron_right</mat-icon></td>
          </tr>
        }
      </tbody>
    </table>
  </mat-card>
}

<div class="toggle-row">
  <button type="button" mat-stroked-button (click)="mostrarTodas = !mostrarTodas">
    <mat-icon>{{ mostrarTodas ? 'expand_less' : 'expand_more' }}</mat-icon>
    {{ mostrarTodas ? 'Ocultar todas las cuentas' : 'Ver todas las cuentas' }}
  </button>
</div>

@if (mostrarTodas) {
<mat-card class="form-panel">
  <div class="filtros-grid">
    <div class="form-group">
      <label for="filtroTexto">Buscar</label>
      <input id="filtroTexto" type="text" placeholder="Descripción..." [(ngModel)]="filtroTexto" name="filtroTexto" />
    </div>
    <div class="form-group">
      <label for="filtroOrigen">Origen</label>
      <select id="filtroOrigen" name="filtroOrigen" [(ngModel)]="filtroOrigen">
        <option [ngValue]="null">Todos</option>
        <option value="COMPRA">Compras</option>
        <option value="GASTO">Gastos</option>
      </select>
    </div>
    <div class="form-group">
      <label for="filtroProveedor">Proveedor</label>
      <select id="filtroProveedor" name="filtroProveedor" [(ngModel)]="filtroProveedor">
        <option [ngValue]="null">Todos</option>
        @for (p of proveedores; track p.id) {
          <option [ngValue]="p.id">{{ p.nombre }}</option>
        }
      </select>
    </div>
    <div class="form-group">
      <label for="filtroCategoriaGasto">Categoría de gasto</label>
      <select id="filtroCategoriaGasto" name="filtroCategoriaGasto" [(ngModel)]="filtroCategoriaGasto">
        <option [ngValue]="null">Todas</option>
        @for (c of categorias; track c.id) {
          <option [ngValue]="c.id">{{ c.nombre }}</option>
        }
      </select>
    </div>
    <div class="form-group">
      <label for="filtroEstado">Estado</label>
      <select id="filtroEstado" name="filtroEstado" [(ngModel)]="filtroEstado">
        <option [ngValue]="null">Todos</option>
        <option value="DEUDA">En deuda</option>
        <option value="PARCIAL">Parcial</option>
        <option value="PAGADO">Pagado</option>
        <option value="ANULADO">Anulado</option>
      </select>
    </div>
  </div>

  @if (cargando) {
    <p class="empty-state">Cargando cuentas...</p>
  }

  @if (!cargando && !cuentasFiltradas.length) {
    <p class="empty-state">No hay cuentas por pagar que coincidan con el filtro.</p>
  }

  @if (!cargando && cuentasFiltradas.length) {
    <table class="historial-table">
      <thead>
        <tr>
          <th>Origen</th>
          <th>Descripción</th>
          <th class="right">Total</th>
          <th class="right">Pagado</th>
          <th class="right">Saldo</th>
          <th>Estado</th>
          <th class="right"></th>
        </tr>
      </thead>
      <tbody>
        @for (c of cuentasFiltradas; track c.id) {
          <tr>
            <td>{{ origenDe(c) === 'COMPRA' ? 'Compra' : 'Gasto' }}</td>
            <td>{{ c.descripcion }}</td>
            <td class="right">{{ c.montoTotal | moneda }}</td>
            <td class="right">{{ c.montoPagado | moneda }}</td>
            <td class="right">{{ c.saldoPendiente | moneda }}</td>
            <td><span [class]="'tag ' + tagEstado(c.estado)">{{ etiquetaEstado(c.estado) }}</span></td>
            <td class="right">
              <a mat-button routerLink="/cuentas-por-pagar/{{ c.id }}">
                {{ puedeRegistrarPago(c) && auth.tienePermiso('TESORERIA_EDITAR') ? 'Registrar pago' : 'Ver' }}
              </a>
            </td>
          </tr>
        }
      </tbody>
    </table>
  }
</mat-card>
}
```

- [ ] **Step 3: Write the stylesheet**

```scss
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: var(--space-4);
  margin-bottom: var(--space-5);
}

.kpi-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  padding: var(--space-4);
}

.kpi-label {
  font: var(--font-body-sm);
  color: var(--text-muted);
}

.kpi-value {
  font: var(--font-h3);
  color: var(--text-title);
  font-weight: 700;
}

@media (max-width: 1100px) {
  .kpi-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 640px) {
  .kpi-grid {
    grid-template-columns: 1fr 1fr;
  }
}

.filtros-grid {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr 1fr 1fr;
  gap: var(--space-4);
  margin-bottom: var(--space-4);
}

@media (max-width: 1100px) {
  .filtros-grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 640px) {
  .filtros-grid {
    grid-template-columns: 1fr;
  }
}

.historial-table {
  width: 100%;
  border-collapse: collapse;
}

.historial-table th,
.historial-table td {
  text-align: left;
  padding: var(--space-2) var(--space-3);
  border-bottom: var(--border-width-default) solid var(--border-default);
  font: var(--font-body-sm);
}

.historial-table th {
  color: var(--text-muted);
  font-weight: 600;
}

.right {
  text-align: right;
}

.clickable-row {
  cursor: pointer;
}

.clickable-row:hover {
  background: var(--surface-subtle);
}

.clickable-row__chevron {
  color: var(--text-muted);
  vertical-align: middle;
}

.toggle-row {
  display: flex;
  justify-content: center;
  margin-bottom: var(--space-5);
}
```
Note the `.filtros-grid` column count differs from `tesoreria-cuentas.component.scss`'s (`2fr 1fr 1fr`, 3 columns) because this screen has 5 filters, not 3 — everything else is copied verbatim from that file per the "component-scoped, duplicate the pattern" constraint.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/features/cuentas-por-pagar/cuentas-por-pagar.component.ts \
  frontend/src/app/features/cuentas-por-pagar/cuentas-por-pagar.component.html \
  frontend/src/app/features/cuentas-por-pagar/cuentas-por-pagar.component.scss
git commit -m "feat: pantalla principal de Cuentas por Pagar"
```

---

## Task 3: `CuentaPorPagarDetalleComponent` (detail, payments, anular)

**Files:**
- Create: `frontend/src/app/features/cuentas-por-pagar/cuenta-por-pagar-detalle.component.ts`
- Create: `frontend/src/app/features/cuentas-por-pagar/cuenta-por-pagar-detalle.component.html`
- Create: `frontend/src/app/features/cuentas-por-pagar/cuenta-por-pagar-detalle.component.scss`

**Interfaces:**
- Consumes: `CuentaPorPagarService`, `TransaccionPagoCompraService` (Task 1), existing `ProveedorService`/`CategoriaGastoService`, existing `frontend/src/app/core/constants/bancos-chile.ts` (`BANCOS_CHILE`).
- Produces: `CuentaPorPagarDetalleComponent`, standalone, selector `app-cuenta-por-pagar-detalle` — routed in Task 6.

Mirrors `frontend/src/app/features/tesoreria/tesoreria-cuenta-detalle.component.ts` almost exactly (same payment-method picker, same bank-selector pattern for transferencia/cheque, same inline anular flows for both the cuenta and individual pagos) — the one difference is the "Datos de la cuenta" panel resolves a `Proveedor` or a `CategoriaGasto` depending on `cuenta.compraId`/`cuenta.gastoId`, instead of always a `Cliente`.

- [ ] **Step 1: Write the component class**

```typescript
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { CategoriaGasto, CuentaPorPagar, EstadoCuentaPorPagar, MedioPago, Proveedor, TransaccionPagoCompra } from '../../core/models/models';
import { CuentaPorPagarService } from '../../core/services/cuenta-por-pagar.service';
import { TransaccionPagoCompraRequest, TransaccionPagoCompraService } from '../../core/services/transaccion-pago-compra.service';
import { ProveedorService } from '../../core/services/proveedor.service';
import { CategoriaGastoService } from '../../core/services/categoria-gasto.service';
import { AuthService } from '../../core/services/auth.service';
import { MonedaPipe } from '../../core/pipes/moneda.pipe';
import { cerrarCargando, mostrarCargando } from '../../core/utils/swal-loading';
import { BANCOS_CHILE } from '../../core/constants/bancos-chile';

const OTRO_BANCO = '__OTRO__';

const ETIQUETAS: Record<EstadoCuentaPorPagar, string> = {
  DEUDA: 'En deuda',
  PARCIAL: 'Parcial',
  PAGADO: 'Pagado',
  ANULADO: 'Anulado',
};

function pagoVacio(): TransaccionPagoCompraRequest {
  return { monto: 0, medioPago: 'EFECTIVO' };
}

@Component({
  selector: 'app-cuenta-por-pagar-detalle',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, MatButtonModule, MatIconModule, MatCardModule, MonedaPipe],
  templateUrl: './cuenta-por-pagar-detalle.component.html',
  styleUrl: './cuenta-por-pagar-detalle.component.scss',
})
export class CuentaPorPagarDetalleComponent implements OnInit {
  cuenta: CuentaPorPagar | null = null;
  proveedor: Proveedor | null = null;
  categoria: CategoriaGasto | null = null;
  pagos: TransaccionPagoCompra[] = [];
  cargando = true;

  formularioPagoAbierto = false;
  pagoForm: TransaccionPagoCompraRequest = pagoVacio();
  guardandoPago = false;
  errorPago = '';

  anulandoCuenta = false;
  motivoAnulacionCuenta = '';
  errorAnulacionCuenta = '';

  pagoAnulandoId: number | null = null;
  motivoAnulacionPago = '';
  errorAnulacionPago = '';

  readonly etiquetaEstado = ETIQUETAS;
  readonly mediosPago: { value: MedioPago; label: string }[] = [
    { value: 'EFECTIVO', label: 'Efectivo' },
    { value: 'TRANSFERENCIA', label: 'Transferencia' },
    { value: 'TARJETA', label: 'Tarjeta' },
    { value: 'CHEQUE', label: 'Cheque' },
  ];
  readonly bancosChile = BANCOS_CHILE;
  readonly OTRO_BANCO = OTRO_BANCO;

  bancoOrigenSeleccion = '';
  bancoDestinoSeleccion = '';
  chequeBancoSeleccion = '';

  constructor(
    private route: ActivatedRoute,
    private cuentaPorPagarService: CuentaPorPagarService,
    private transaccionPagoCompraService: TransaccionPagoCompraService,
    private proveedorService: ProveedorService,
    private categoriaGastoService: CategoriaGastoService,
    public auth: AuthService
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.cargar(id);
  }

  private cargar(id: number): void {
    this.cargando = true;
    this.cuentaPorPagarService.obtener(id).subscribe({
      next: (cuenta) => {
        this.cuenta = cuenta;
        if (cuenta.proveedorId !== null) {
          this.proveedorService.listar().subscribe((data) => {
            this.proveedor = data.find((p) => p.id === cuenta.proveedorId) ?? null;
          });
        }
        if (cuenta.categoriaGastoId !== null) {
          this.categoriaGastoService.listar().subscribe((data) => {
            this.categoria = data.find((c) => c.id === cuenta.categoriaGastoId) ?? null;
          });
        }
        this.cargarPagos(id);
      },
      error: () => (this.cargando = false),
    });
  }

  private cargarPagos(id: number): void {
    this.transaccionPagoCompraService.listarPorCuenta(id).subscribe({
      next: (data) => {
        this.pagos = data;
        this.cargando = false;
      },
      error: () => (this.cargando = false),
    });
  }

  get esOrigenCompra(): boolean {
    return !!this.cuenta && this.cuenta.compraId !== null;
  }

  get puedeRegistrarPago(): boolean {
    return !!this.cuenta && (this.cuenta.estado === 'DEUDA' || this.cuenta.estado === 'PARCIAL');
  }

  toggleFormularioPago(): void {
    this.formularioPagoAbierto = !this.formularioPagoAbierto;
    this.pagoForm = pagoVacio();
    this.bancoOrigenSeleccion = '';
    this.bancoDestinoSeleccion = '';
    this.chequeBancoSeleccion = '';
    this.errorPago = '';
  }

  seleccionarMedio(medio: MedioPago): void {
    this.pagoForm.medioPago = medio;
  }

  // Los <select> de banco usan una selección aparte del valor final: al
  // elegir "Otro" se limpia el campo para que el usuario lo escriba a mano.
  seleccionarBanco(
    campo: 'transferenciaBancoOrigen' | 'transferenciaBancoDestino' | 'chequeBanco',
    valor: string
  ): void {
    this.pagoForm[campo] = valor === OTRO_BANCO ? '' : valor;
  }

  get montoInvalido(): boolean {
    return !this.cuenta || this.pagoForm.monto <= 0 || this.pagoForm.monto > this.cuenta.saldoPendiente;
  }

  confirmarPago(): void {
    if (!this.cuenta || this.montoInvalido) return;
    this.guardandoPago = true;
    this.errorPago = '';
    mostrarCargando('Registrando pago');

    this.transaccionPagoCompraService.registrarPago(this.cuenta.id, this.pagoForm).subscribe({
      next: () => {
        cerrarCargando();
        this.guardandoPago = false;
        this.formularioPagoAbierto = false;
        this.pagoForm = pagoVacio();
        this.bancoOrigenSeleccion = '';
        this.bancoDestinoSeleccion = '';
        this.chequeBancoSeleccion = '';
        this.cargar(this.cuenta!.id);
      },
      error: (err) => {
        cerrarCargando();
        this.errorPago = err?.error?.error ?? 'Ocurrió un error al registrar el pago.';
        this.guardandoPago = false;
      },
    });
  }

  toggleAnularCuenta(): void {
    this.anulandoCuenta = !this.anulandoCuenta;
    this.motivoAnulacionCuenta = '';
    this.errorAnulacionCuenta = '';
  }

  confirmarAnulacionCuenta(): void {
    if (!this.cuenta || !this.motivoAnulacionCuenta.trim()) return;
    this.cuentaPorPagarService.anular(this.cuenta.id, this.motivoAnulacionCuenta).subscribe({
      next: () => {
        this.anulandoCuenta = false;
        this.cargar(this.cuenta!.id);
      },
      error: (err) => {
        this.errorAnulacionCuenta = err?.error?.error ?? 'Ocurrió un error al anular la cuenta.';
      },
    });
  }

  toggleAnularPago(pagoId: number): void {
    this.pagoAnulandoId = this.pagoAnulandoId === pagoId ? null : pagoId;
    this.motivoAnulacionPago = '';
    this.errorAnulacionPago = '';
  }

  confirmarAnulacionPago(pagoId: number): void {
    if (!this.motivoAnulacionPago.trim()) return;
    this.transaccionPagoCompraService.anular(pagoId, this.motivoAnulacionPago).subscribe({
      next: () => {
        this.pagoAnulandoId = null;
        this.cargar(this.cuenta!.id);
      },
      error: (err) => {
        this.errorAnulacionPago = err?.error?.error ?? 'Ocurrió un error al anular el pago.';
      },
    });
  }
}
```

- [ ] **Step 2: Write the template**

```html
@if (cargando) {
  <p class="empty-state">Cargando cuenta...</p>
}

@if (!cargando && !cuenta) {
  <p class="page-error">Cuenta por pagar no encontrada.</p>
}

@if (!cargando && cuenta) {
  <div class="page-header">
    <h1>Cuenta por pagar — {{ cuenta.descripcion }}</h1>
    <a mat-stroked-button routerLink="/cuentas-por-pagar">
      <mat-icon>arrow_back</mat-icon>
      Volver
    </a>
  </div>

  <div class="two-col">
    <mat-card class="form-panel">
      <h2>Datos de la cuenta</h2>
      <div class="form-grid">
        @if (esOrigenCompra) {
          <div class="form-group">
            <label>Proveedor</label>
            <input type="text" [value]="proveedor?.nombre || '—'" readonly />
          </div>
        } @else {
          <div class="form-group">
            <label>Categoría de gasto</label>
            <input type="text" [value]="categoria?.nombre || '—'" readonly />
          </div>
        }
        <div class="form-group">
          <label>Estado</label>
          <input type="text" [value]="etiquetaEstado[cuenta.estado]" readonly />
        </div>
        <div class="form-group">
          <label>Fecha de generación</label>
          <input type="text" [value]="cuenta.fechaGeneracion | date: 'short'" readonly />
        </div>
        <div class="form-group">
          <label>Fecha del último pago</label>
          <input type="text" [value]="(cuenta.fechaUltimoPago | date: 'short') || '—'" readonly />
        </div>
      </div>
      @if (cuenta.estado === 'ANULADO') {
        <p class="detail-obs"><strong>Motivo de anulación:</strong> {{ cuenta.motivoAnulacion }}</p>
      }
    </mat-card>

    <mat-card class="form-panel">
      <h2>Totales</h2>
      <div class="totals-group">
        <div class="totals-row">
          <span class="totals-row__label">Monto total</span>
          <span class="totals-row__value">{{ cuenta.montoTotal | moneda }}</span>
        </div>
        <div class="totals-row">
          <span class="totals-row__label">Pagado</span>
          <span class="totals-row__value">{{ cuenta.montoPagado | moneda }}</span>
        </div>
        <div class="totals-row totals-row--total">
          <span class="totals-row__label">Saldo pendiente</span>
          <span class="totals-row__value">{{ cuenta.saldoPendiente | moneda }}</span>
        </div>
      </div>
    </mat-card>
  </div>

  <mat-card class="form-panel">
    <div class="panel-header-row">
      <h2>Pagos registrados</h2>
      <div class="panel-actions">
        @if (puedeRegistrarPago && auth.tienePermiso('TESORERIA_EDITAR')) {
          <button type="button" mat-stroked-button (click)="toggleFormularioPago()">
            <mat-icon>payments</mat-icon>
            {{ formularioPagoAbierto ? 'Cancelar' : 'Registrar pago' }}
          </button>
        }
        @if (cuenta.estado !== 'ANULADO' && cuenta.estado !== 'PAGADO' && auth.tienePermiso('TESORERIA_ANULAR')) {
          <button type="button" mat-stroked-button color="warn" (click)="toggleAnularCuenta()">
            <mat-icon>block</mat-icon>
            {{ anulandoCuenta ? 'Cancelar' : 'Anular cuenta' }}
          </button>
        }
      </div>
    </div>

    @if (anulandoCuenta) {
      <div class="inline-form">
        <div class="form-group">
          <label for="motivoAnulacionCuenta">Motivo de la anulación<span class="required-mark">*</span></label>
          <input
            id="motivoAnulacionCuenta"
            type="text"
            [(ngModel)]="motivoAnulacionCuenta"
            name="motivoAnulacionCuenta"
            placeholder="Ej: compra anulada por error"
          />
        </div>
        @if (errorAnulacionCuenta) {
          <p class="field-error">{{ errorAnulacionCuenta }}</p>
        }
        <button
          type="button"
          mat-flat-button
          color="warn"
          [disabled]="!motivoAnulacionCuenta.trim()"
          (click)="confirmarAnulacionCuenta()"
        >
          Confirmar anulación
        </button>
      </div>
    }

    @if (formularioPagoAbierto) {
      <div class="inline-form">
        <div class="tipo-grid">
          @for (medio of mediosPago; track medio.value) {
            <button
              type="button"
              class="tipo-card"
              [class.tipo-card--active]="pagoForm.medioPago === medio.value"
              (click)="seleccionarMedio(medio.value)"
            >
              <span class="tipo-card__label">{{ medio.label }}</span>
            </button>
          }
        </div>

        <div class="form-grid">
          <div class="form-group">
            <label for="pagoMonto">Monto<span class="required-mark">*</span></label>
            <input id="pagoMonto" type="number" [(ngModel)]="pagoForm.monto" name="pagoMonto" [max]="cuenta.saldoPendiente" />
            <span class="hint">Saldo pendiente: {{ cuenta.saldoPendiente | moneda }}</span>
          </div>

          @if (pagoForm.medioPago === 'TRANSFERENCIA') {
            <div class="form-group">
              <label for="bancoOrigen">Banco de origen<span class="required-mark">*</span></label>
              <select
                id="bancoOrigen"
                name="bancoOrigen"
                [(ngModel)]="bancoOrigenSeleccion"
                (ngModelChange)="seleccionarBanco('transferenciaBancoOrigen', $event)"
              >
                <option value="" disabled>Selecciona un banco</option>
                @for (b of bancosChile; track b) {
                  <option [value]="b">{{ b }}</option>
                }
                <option [value]="OTRO_BANCO">Otro...</option>
              </select>
              @if (bancoOrigenSeleccion === OTRO_BANCO) {
                <input
                  type="text"
                  placeholder="Nombre del banco"
                  [(ngModel)]="pagoForm.transferenciaBancoOrigen"
                  name="bancoOrigenOtro"
                />
              }
            </div>
            <div class="form-group">
              <label for="bancoDestino">Banco de destino<span class="required-mark">*</span></label>
              <select
                id="bancoDestino"
                name="bancoDestino"
                [(ngModel)]="bancoDestinoSeleccion"
                (ngModelChange)="seleccionarBanco('transferenciaBancoDestino', $event)"
              >
                <option value="" disabled>Selecciona un banco</option>
                @for (b of bancosChile; track b) {
                  <option [value]="b">{{ b }}</option>
                }
                <option [value]="OTRO_BANCO">Otro...</option>
              </select>
              @if (bancoDestinoSeleccion === OTRO_BANCO) {
                <input
                  type="text"
                  placeholder="Nombre del banco"
                  [(ngModel)]="pagoForm.transferenciaBancoDestino"
                  name="bancoDestinoOtro"
                />
              }
            </div>
            <div class="form-group">
              <label for="numOpTransferencia">N° de operación</label>
              <input id="numOpTransferencia" type="text" [(ngModel)]="pagoForm.transferenciaNumeroOperacion" name="numOpTransferencia" />
            </div>
          }

          @if (pagoForm.medioPago === 'TARJETA') {
            <div class="form-group">
              <label for="tarjetaEntidad">Entidad<span class="required-mark">*</span></label>
              <input id="tarjetaEntidad" type="text" [(ngModel)]="pagoForm.tarjetaEntidad" name="tarjetaEntidad" />
            </div>
            <div class="form-group">
              <label for="tarjetaTipo">Tipo</label>
              <input id="tarjetaTipo" type="text" placeholder="Débito / Crédito" [(ngModel)]="pagoForm.tarjetaTipo" name="tarjetaTipo" />
            </div>
            <div class="form-group">
              <label for="numOpTarjeta">N° de operación</label>
              <input id="numOpTarjeta" type="text" [(ngModel)]="pagoForm.tarjetaNumeroOperacion" name="numOpTarjeta" />
            </div>
          }

          @if (pagoForm.medioPago === 'CHEQUE') {
            <div class="form-group">
              <label for="chequeBanco">Banco<span class="required-mark">*</span></label>
              <select
                id="chequeBanco"
                name="chequeBanco"
                [(ngModel)]="chequeBancoSeleccion"
                (ngModelChange)="seleccionarBanco('chequeBanco', $event)"
              >
                <option value="" disabled>Selecciona un banco</option>
                @for (b of bancosChile; track b) {
                  <option [value]="b">{{ b }}</option>
                }
                <option [value]="OTRO_BANCO">Otro...</option>
              </select>
              @if (chequeBancoSeleccion === OTRO_BANCO) {
                <input
                  type="text"
                  placeholder="Nombre del banco"
                  [(ngModel)]="pagoForm.chequeBanco"
                  name="chequeBancoOtro"
                />
              }
            </div>
            <div class="form-group">
              <label for="chequeNumero">N° de cheque<span class="required-mark">*</span></label>
              <input id="chequeNumero" type="text" [(ngModel)]="pagoForm.chequeNumero" name="chequeNumero" />
            </div>
          }

          <div class="form-group span-2">
            <label for="pagoObs">Observaciones <span class="hint">(opcional)</span></label>
            <textarea id="pagoObs" rows="2" [(ngModel)]="pagoForm.observaciones" name="pagoObs"></textarea>
          </div>
        </div>

        @if (errorPago) {
          <p class="field-error">{{ errorPago }}</p>
        }

        <button
          type="button"
          mat-flat-button
          color="primary"
          [disabled]="montoInvalido || guardandoPago"
          (click)="confirmarPago()"
        >
          <mat-icon>check_circle</mat-icon>
          {{ guardandoPago ? 'Guardando...' : 'Confirmar pago' }}
        </button>
      </div>
    }

    <table class="historial-table">
      <thead>
        <tr>
          <th>Fecha</th>
          <th>Medio</th>
          <th class="right">Monto</th>
          <th>Estado</th>
          <th class="right"></th>
        </tr>
      </thead>
      <tbody>
        @for (p of pagos; track p.id) {
          <tr>
            <td>{{ p.fecha | date: 'short' }}</td>
            <td>{{ p.medioPago | titlecase }}</td>
            <td class="right">{{ p.monto | moneda }}</td>
            <td>
              <span class="tag" [class.tag--success]="p.estado === 'CONFIRMADA'">
                {{ p.estado === 'CONFIRMADA' ? 'Confirmada' : 'Anulada' }}
              </span>
            </td>
            <td class="right">
              @if (p.estado === 'CONFIRMADA' && auth.tienePermiso('TESORERIA_ANULAR')) {
                <button type="button" mat-button color="warn" (click)="toggleAnularPago(p.id)">
                  {{ pagoAnulandoId === p.id ? 'Cancelar' : 'Anular' }}
                </button>
              }
            </td>
          </tr>
          @if (pagoAnulandoId === p.id) {
            <tr>
              <td colspan="5">
                <div class="inline-form">
                  <div class="form-group">
                    <label for="motivoPago{{ p.id }}">Motivo de la anulación<span class="required-mark">*</span></label>
                    <input
                      id="motivoPago{{ p.id }}"
                      type="text"
                      [(ngModel)]="motivoAnulacionPago"
                      name="motivoPago{{ p.id }}"
                    />
                  </div>
                  @if (errorAnulacionPago) {
                    <p class="field-error">{{ errorAnulacionPago }}</p>
                  }
                  <button
                    type="button"
                    mat-flat-button
                    color="warn"
                    [disabled]="!motivoAnulacionPago.trim()"
                    (click)="confirmarAnulacionPago(p.id)"
                  >
                    Confirmar anulación
                  </button>
                </div>
              </td>
            </tr>
          }
        }
        @if (!pagos.length) {
          <tr>
            <td colspan="5" class="empty-state">Aún no hay pagos registrados.</td>
          </tr>
        }
      </tbody>
    </table>
  </mat-card>
}
```

- [ ] **Step 3: Write the stylesheet**

```scss
.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-5);
  align-items: start;
}

@media (max-width: 900px) {
  .two-col {
    grid-template-columns: 1fr;
  }
}

.detail-obs {
  font: var(--font-body-sm);
  color: var(--text-body);
  margin: var(--space-3) 0 0;
}

.totals-group {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.totals-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font: var(--font-body-sm);
  color: var(--text-body);
}

.totals-row--total {
  font: var(--font-h3);
  font-weight: 700;
  color: var(--text-title);
  border-top: var(--border-width-default) solid var(--border-default);
  padding-top: var(--space-2);
}

.panel-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}

.panel-header-row h2 {
  margin: 0;
}

.panel-actions {
  display: flex;
  gap: var(--space-2);
}

.inline-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-4);
  margin-bottom: var(--space-4);
  background: var(--surface-subtle);
  border-radius: var(--radius-2);
}

.tipo-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: var(--space-3);
}

.tipo-card {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-3);
  border: var(--border-width-default) solid var(--border-default);
  border-radius: var(--radius-2);
  background: var(--surface-card);
  cursor: pointer;
  transition: border-color 0.15s, background-color 0.15s;
}

.tipo-card:hover {
  background: var(--surface-subtle);
}

.tipo-card--active {
  border-color: var(--primary-base);
  background: var(--primary-background);
}

.tipo-card__label {
  font: var(--font-body);
  font-weight: 700;
  color: var(--text-title);
}

.historial-table {
  width: 100%;
  border-collapse: collapse;
}

.historial-table th,
.historial-table td {
  text-align: left;
  padding: var(--space-2) var(--space-3);
  border-bottom: var(--border-width-default) solid var(--border-default);
  font: var(--font-body-sm);
}

.historial-table th {
  color: var(--text-muted);
  font-weight: 600;
}

.right {
  text-align: right;
}
```
Byte-for-byte copy of `tesoreria-cuenta-detalle.component.scss` — same component-scoped classes, same values.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/features/cuentas-por-pagar/cuenta-por-pagar-detalle.component.ts \
  frontend/src/app/features/cuentas-por-pagar/cuenta-por-pagar-detalle.component.html \
  frontend/src/app/features/cuentas-por-pagar/cuenta-por-pagar-detalle.component.scss
git commit -m "feat: detalle de Cuenta por Pagar con registro y anulación de pagos"
```

---

## Task 4: `ProveedorDetalleComponent` (per-supplier statement)

**Files:**
- Create: `frontend/src/app/features/cuentas-por-pagar/proveedor-detalle.component.ts`
- Create: `frontend/src/app/features/cuentas-por-pagar/proveedor-detalle.component.html`
- Create: `frontend/src/app/features/cuentas-por-pagar/proveedor-detalle.component.scss`

**Interfaces:**
- Consumes: `CuentaPorPagarService` (Task 1), existing `ProveedorService`.
- Produces: `ProveedorDetalleComponent`, standalone, selector `app-proveedor-detalle` — routed in Task 6.

Mirrors `frontend/src/app/features/tesoreria/tesoreria-cliente-detalle.component.ts`. One difference from that mirror: `ProveedorService` has no `obtener(id)` endpoint (unlike `ClienteService`, `ProveedorController` on the backend only exposes `GET /api/proveedores` — confirmed by reading the current backend controller — no `GET /api/proveedores/{id}`). Adding that backend endpoint is out of scope for a frontend-only plan, so this component resolves the `Proveedor` by filtering the already-fetched `listar()` array client-side, exactly like `GastosComponent.nombreCategoriaDe()` already does for categories elsewhere in this codebase.

- [ ] **Step 1: Write the component class**

```typescript
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { CuentaPorPagar, EstadoCuentaPorPagar, Proveedor } from '../../core/models/models';
import { CuentaPorPagarService } from '../../core/services/cuenta-por-pagar.service';
import { ProveedorService } from '../../core/services/proveedor.service';
import { AuthService } from '../../core/services/auth.service';
import { MonedaPipe } from '../../core/pipes/moneda.pipe';

const ETIQUETAS: Record<EstadoCuentaPorPagar, string> = {
  DEUDA: 'En deuda',
  PARCIAL: 'Parcial',
  PAGADO: 'Pagado',
  ANULADO: 'Anulado',
};

const TAGS: Record<EstadoCuentaPorPagar, string> = {
  DEUDA: 'tag--error',
  PARCIAL: 'tag--warning',
  PAGADO: 'tag--success',
  ANULADO: '',
};

@Component({
  selector: 'app-proveedor-detalle',
  standalone: true,
  imports: [CommonModule, RouterLink, MatButtonModule, MatIconModule, MatCardModule, MonedaPipe],
  templateUrl: './proveedor-detalle.component.html',
  styleUrl: './proveedor-detalle.component.scss',
})
export class ProveedorDetalleComponent implements OnInit {
  proveedor: Proveedor | null = null;
  cuentas: CuentaPorPagar[] = [];
  cargando = true;

  constructor(
    private route: ActivatedRoute,
    private cuentaPorPagarService: CuentaPorPagarService,
    private proveedorService: ProveedorService,
    public auth: AuthService
  ) {}

  ngOnInit(): void {
    const proveedorId = Number(this.route.snapshot.paramMap.get('proveedorId'));
    this.cargando = true;
    this.proveedorService.listar().subscribe({
      next: (data) => (this.proveedor = data.find((p) => p.id === proveedorId) ?? null),
      error: () => (this.proveedor = null),
    });
    this.cuentaPorPagarService.listar(proveedorId).subscribe({
      next: (data) => {
        this.cuentas = data;
        this.cargando = false;
      },
      error: () => (this.cargando = false),
    });
  }

  etiquetaEstado(estado: EstadoCuentaPorPagar): string {
    return ETIQUETAS[estado];
  }

  tagEstado(estado: EstadoCuentaPorPagar): string {
    return TAGS[estado];
  }

  puedeRegistrarPago(cuenta: CuentaPorPagar): boolean {
    return cuenta.estado === 'DEUDA' || cuenta.estado === 'PARCIAL';
  }

  get totalFacturado(): number {
    return this.cuentas.filter((c) => c.estado !== 'ANULADO').reduce((acc, c) => acc + c.montoTotal, 0);
  }

  get totalPagado(): number {
    return this.cuentas.filter((c) => c.estado !== 'ANULADO').reduce((acc, c) => acc + c.montoPagado, 0);
  }

  get saldoPendiente(): number {
    return this.cuentas.filter((c) => c.estado !== 'ANULADO').reduce((acc, c) => acc + c.saldoPendiente, 0);
  }

  get cuentasEnDeuda(): number {
    return this.cuentas.filter((c) => this.puedeRegistrarPago(c)).length;
  }
}
```

- [ ] **Step 2: Write the template**

```html
@if (cargando) {
  <p class="empty-state">Cargando proveedor...</p>
}

@if (!cargando && !proveedor) {
  <p class="page-error">Proveedor no encontrado.</p>
}

@if (!cargando && proveedor) {
  <div class="page-header">
    <h1>Cuenta corriente — {{ proveedor.nombre }}</h1>
    <a mat-stroked-button routerLink="/cuentas-por-pagar">
      <mat-icon>arrow_back</mat-icon>
      Volver
    </a>
  </div>

  <mat-card class="form-panel">
    <h2>Datos del proveedor</h2>
    <div class="form-grid">
      <div class="form-group">
        <label>RUT</label>
        <input type="text" [value]="proveedor.rut || '—'" readonly />
      </div>
      <div class="form-group">
        <label>Email</label>
        <input type="text" [value]="proveedor.email || '—'" readonly />
      </div>
      <div class="form-group span-2">
        <label>Dirección</label>
        <input type="text" [value]="proveedor.direccion || '—'" readonly />
      </div>
    </div>
  </mat-card>

  <section class="kpi-grid">
    <mat-card class="kpi-card">
      <span class="kpi-label">Total facturado</span>
      <span class="kpi-value">{{ totalFacturado | moneda }}</span>
    </mat-card>
    <mat-card class="kpi-card">
      <span class="kpi-label">Total pagado</span>
      <span class="kpi-value">{{ totalPagado | moneda }}</span>
    </mat-card>
    <mat-card class="kpi-card">
      <span class="kpi-label">Saldo pendiente</span>
      <span class="kpi-value">{{ saldoPendiente | moneda }}</span>
    </mat-card>
    <mat-card class="kpi-card">
      <span class="kpi-label">Cuentas en deuda</span>
      <span class="kpi-value">{{ cuentasEnDeuda }}</span>
    </mat-card>
  </section>

  <mat-card class="form-panel">
    <h2>Historial de cuentas por pagar</h2>

    @if (!cuentas.length) {
      <p class="empty-state">Este proveedor no tiene cuentas por pagar registradas.</p>
    }

    @if (cuentas.length) {
      <table class="historial-table">
        <thead>
          <tr>
            <th>Documento</th>
            <th>Fecha</th>
            <th class="right">Total</th>
            <th class="right">Pagado</th>
            <th class="right">Saldo</th>
            <th>Estado</th>
            <th class="right"></th>
          </tr>
        </thead>
        <tbody>
          @for (c of cuentas; track c.id) {
            <tr>
              <td>{{ c.descripcion }}</td>
              <td>{{ c.fechaGeneracion | date: 'short' }}</td>
              <td class="right">{{ c.montoTotal | moneda }}</td>
              <td class="right">{{ c.montoPagado | moneda }}</td>
              <td class="right">{{ c.saldoPendiente | moneda }}</td>
              <td><span [class]="'tag ' + tagEstado(c.estado)">{{ etiquetaEstado(c.estado) }}</span></td>
              <td class="right">
                <a mat-button routerLink="/cuentas-por-pagar/{{ c.id }}">
                  {{ puedeRegistrarPago(c) && auth.tienePermiso('TESORERIA_EDITAR') ? 'Registrar pago' : 'Ver' }}
                </a>
              </td>
            </tr>
          }
        </tbody>
      </table>
    }
  </mat-card>
}
```

- [ ] **Step 3: Write the stylesheet**

```scss
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-4);
  margin-bottom: var(--space-5);
}

.kpi-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  padding: var(--space-4);
}

.kpi-label {
  font: var(--font-body-sm);
  color: var(--text-muted);
}

.kpi-value {
  font: var(--font-h3);
  color: var(--text-title);
  font-weight: 700;
}

@media (max-width: 900px) {
  .kpi-grid {
    grid-template-columns: 1fr 1fr;
  }
}

.historial-table {
  width: 100%;
  border-collapse: collapse;
}

.historial-table th,
.historial-table td {
  text-align: left;
  padding: var(--space-2) var(--space-3);
  border-bottom: var(--border-width-default) solid var(--border-default);
  font: var(--font-body-sm);
}

.historial-table th {
  color: var(--text-muted);
  font-weight: 600;
}

.right {
  text-align: right;
}
```
Byte-for-byte copy of `tesoreria-cliente-detalle.component.scss`.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/features/cuentas-por-pagar/proveedor-detalle.component.ts \
  frontend/src/app/features/cuentas-por-pagar/proveedor-detalle.component.html \
  frontend/src/app/features/cuentas-por-pagar/proveedor-detalle.component.scss
git commit -m "feat: cuenta corriente por proveedor en Cuentas por Pagar"
```

---

## Task 5: `PagosCompraHistorialComponent` (payment history)

**Files:**
- Create: `frontend/src/app/features/cuentas-por-pagar/pagos-compra-historial.component.ts`
- Create: `frontend/src/app/features/cuentas-por-pagar/pagos-compra-historial.component.html`
- Create: `frontend/src/app/features/cuentas-por-pagar/pagos-compra-historial.component.scss`

**Interfaces:**
- Consumes: `TransaccionPagoCompraService` (Task 1).
- Produces: `PagosCompraHistorialComponent`, standalone, selector `app-pagos-compra-historial` — routed in Task 6.

Mirrors `frontend/src/app/features/tesoreria/tesoreria-historial.component.ts` — **without** the free-text `busqueda` box or the client-name column: `TransaccionPagoCompraService.buscar()` (backend, already merged) has no `busqueda` parameter by design (see the backend plan's Global Constraints — that in-memory search pattern existed on the CxC side only because it resolves client names, which has no equivalent here). Filters are `estado`, `medioPago`, and a date range, exactly matching what the backend actually accepts.

- [ ] **Step 1: Write the component class**

```typescript
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { EstadoTransaccion, MedioPago, TransaccionPagoCompra } from '../../core/models/models';
import { FiltrosHistorialPagoCompra, TransaccionPagoCompraService } from '../../core/services/transaccion-pago-compra.service';
import { MonedaPipe } from '../../core/pipes/moneda.pipe';

@Component({
  selector: 'app-pagos-compra-historial',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, MatButtonModule, MatIconModule, MatCardModule, MatPaginatorModule, MonedaPipe],
  templateUrl: './pagos-compra-historial.component.html',
  styleUrl: './pagos-compra-historial.component.scss',
})
export class PagosCompraHistorialComponent implements OnInit {
  readonly opcionesTamano = [10, 25, 50];

  pagos: TransaccionPagoCompra[] = [];
  cargando = true;
  total = 0;
  pagina = 0;
  tamano = 10;

  filtroEstado: EstadoTransaccion | null = null;
  filtroMedioPago: MedioPago | null = null;
  filtroFechaDesde = '';
  filtroFechaHasta = '';

  constructor(private transaccionPagoCompraService: TransaccionPagoCompraService) {}

  ngOnInit(): void {
    this.buscar();
  }

  onFiltroChange(): void {
    this.pagina = 0;
    this.buscar();
  }

  onPageChange(event: PageEvent): void {
    this.pagina = event.pageIndex;
    this.tamano = event.pageSize;
    this.buscar();
  }

  buscar(): void {
    this.cargando = true;
    const filtros: FiltrosHistorialPagoCompra = {
      estado: this.filtroEstado ?? undefined,
      medioPago: this.filtroMedioPago ?? undefined,
      fechaDesde: this.filtroFechaDesde ? `${this.filtroFechaDesde}T00:00:00` : undefined,
      fechaHasta: this.filtroFechaHasta ? `${this.filtroFechaHasta}T23:59:59` : undefined,
      pagina: this.pagina,
      tamano: this.tamano,
    };
    this.transaccionPagoCompraService.buscar(filtros).subscribe({
      next: (resp) => {
        this.pagos = resp.contenido;
        this.total = resp.total;
        this.cargando = false;
      },
      error: () => (this.cargando = false),
    });
  }

  limpiarFiltros(): void {
    this.filtroEstado = null;
    this.filtroMedioPago = null;
    this.filtroFechaDesde = '';
    this.filtroFechaHasta = '';
    this.pagina = 0;
    this.buscar();
  }

  origenDe(p: TransaccionPagoCompra): string {
    return p.compraId !== null ? `Compra C-${p.compraId}` : `Gasto #${p.gastoId}`;
  }
}
```

- [ ] **Step 2: Write the template**

```html
<div class="page-header">
  <h1>Historial de pagos a proveedores y gastos</h1>
  <a mat-stroked-button routerLink="/cuentas-por-pagar">
    <mat-icon>arrow_back</mat-icon>
    Cuentas por pagar
  </a>
</div>

<mat-card class="form-panel">
  <div class="filtros-grid">
    <div class="form-group">
      <label for="filtroEstado">Estado</label>
      <select id="filtroEstado" name="filtroEstado" [(ngModel)]="filtroEstado" (ngModelChange)="onFiltroChange()">
        <option [ngValue]="null">Todos</option>
        <option value="CONFIRMADA">Confirmada</option>
        <option value="ANULADA">Anulada</option>
      </select>
    </div>
    <div class="form-group">
      <label for="filtroMedio">Medio de pago</label>
      <select id="filtroMedio" name="filtroMedio" [(ngModel)]="filtroMedioPago" (ngModelChange)="onFiltroChange()">
        <option [ngValue]="null">Todos</option>
        <option value="EFECTIVO">Efectivo</option>
        <option value="TRANSFERENCIA">Transferencia</option>
        <option value="TARJETA">Tarjeta</option>
        <option value="CHEQUE">Cheque</option>
      </select>
    </div>
    <div class="form-group">
      <label for="fechaDesde">Desde</label>
      <input id="fechaDesde" type="date" [(ngModel)]="filtroFechaDesde" name="fechaDesde" (ngModelChange)="onFiltroChange()" />
    </div>
    <div class="form-group">
      <label for="fechaHasta">Hasta</label>
      <input id="fechaHasta" type="date" [(ngModel)]="filtroFechaHasta" name="fechaHasta" (ngModelChange)="onFiltroChange()" />
    </div>
    <div class="form-group filtros-grid__limpiar">
      <button type="button" mat-stroked-button (click)="limpiarFiltros()">Limpiar filtros</button>
    </div>
  </div>

  @if (cargando) {
    <p class="empty-state">Buscando pagos...</p>
  }

  @if (!cargando && !pagos.length) {
    <p class="empty-state">No hay pagos que coincidan con el filtro.</p>
  }

  @if (!cargando && pagos.length) {
    <table class="historial-table">
      <thead>
        <tr>
          <th>Fecha</th>
          <th>Origen</th>
          <th>Medio</th>
          <th class="right">Monto</th>
          <th>Estado</th>
          <th class="right"></th>
        </tr>
      </thead>
      <tbody>
        @for (p of pagos; track p.id) {
          <tr>
            <td>{{ p.fecha | date: 'short' }}</td>
            <td>{{ origenDe(p) }}</td>
            <td>{{ p.medioPago | titlecase }}</td>
            <td class="right">{{ p.monto | moneda }}</td>
            <td>
              <span class="tag" [class.tag--success]="p.estado === 'CONFIRMADA'">
                {{ p.estado === 'CONFIRMADA' ? 'Confirmada' : 'Anulada' }}
              </span>
            </td>
            <td class="right">
              <a mat-button routerLink="/cuentas-por-pagar/{{ p.cuentaPorPagarId }}">Ver cuenta</a>
            </td>
          </tr>
        }
      </tbody>
    </table>
    <mat-paginator
      [length]="total"
      [pageIndex]="pagina"
      [pageSize]="tamano"
      [pageSizeOptions]="opcionesTamano"
      [disabled]="cargando"
      (page)="onPageChange($event)"
      showFirstLastButtons
    ></mat-paginator>
  }
</mat-card>
```

- [ ] **Step 3: Write the stylesheet**

```scss
.filtros-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: var(--space-4);
  align-items: end;
  margin-bottom: var(--space-4);
}

.filtros-grid__limpiar {
  display: flex;
  align-items: flex-end;
}

@media (max-width: 1100px) {
  .filtros-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 700px) {
  .filtros-grid {
    grid-template-columns: 1fr;
  }
}

.historial-table {
  width: 100%;
  border-collapse: collapse;
}

.historial-table th,
.historial-table td {
  text-align: left;
  padding: var(--space-2) var(--space-3);
  border-bottom: var(--border-width-default) solid var(--border-default);
  font: var(--font-body-sm);
}

.historial-table th {
  color: var(--text-muted);
  font-weight: 600;
}

.right {
  text-align: right;
}
```
Byte-for-byte copy of `tesoreria-historial.component.scss` (this screen has 4 filters + a clear button, same as the CxC one, so the 5-column grid is unchanged).

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/features/cuentas-por-pagar/pagos-compra-historial.component.ts \
  frontend/src/app/features/cuentas-por-pagar/pagos-compra-historial.component.html \
  frontend/src/app/features/cuentas-por-pagar/pagos-compra-historial.component.scss
git commit -m "feat: historial de pagos de Cuentas por Pagar"
```

---

## Task 6: Routes, nav, and manual verification

**Files:**
- Modify: `frontend/src/app/app.routes.ts`
- Modify: `frontend/src/app/layout/layout.component.ts`

**Interfaces:**
- Consumes: all four components from Tasks 2–5.

- [ ] **Step 1: Add the routes**

In `frontend/src/app/app.routes.ts`, inside the authenticated section (alongside the existing `tesoreria/*` and `gastos` routes), add these four routes **in this exact order** — `historial` and `proveedores/:proveedorId` must come before the numeric `:id` catch-all, or Angular's router will try to parse `"historial"`/`"proveedores"` as a `CuentaPorPagar` id first:
```typescript
{
  path: 'cuentas-por-pagar',
  loadComponent: () =>
    import('./features/cuentas-por-pagar/cuentas-por-pagar.component').then((m) => m.CuentasPorPagarComponent),
},
{
  path: 'cuentas-por-pagar/historial',
  loadComponent: () =>
    import('./features/cuentas-por-pagar/pagos-compra-historial.component').then((m) => m.PagosCompraHistorialComponent),
},
{
  path: 'cuentas-por-pagar/proveedores/:proveedorId',
  loadComponent: () =>
    import('./features/cuentas-por-pagar/proveedor-detalle.component').then((m) => m.ProveedorDetalleComponent),
},
{
  path: 'cuentas-por-pagar/:id',
  loadComponent: () =>
    import('./features/cuentas-por-pagar/cuenta-por-pagar-detalle.component').then((m) => m.CuentaPorPagarDetalleComponent),
},
```

- [ ] **Step 2: Add the nav entries**

In `frontend/src/app/layout/layout.component.ts`, inside the `tesoreria` section's `items` array, add these two entries right after the existing `/tesoreria/historial` entry (before the `/gastos` entries added by the Módulo de Gastos plan):
```typescript
{ ruta: '/cuentas-por-pagar', label: 'Cuentas por pagar', icono: 'request_quote', permiso: 'TESORERIA_VER' },
{ ruta: '/cuentas-por-pagar/historial', label: 'Historial de pagos (compras)', icono: 'history', permiso: 'TESORERIA_VER' },
```

- [ ] **Step 3: Verify TypeScript compiles**

```bash
cd frontend && npx tsc --noEmit
```
Expected: no errors.

- [ ] **Step 4: Manual end-to-end verification**

Start the stack (rebuild both backend and frontend since both have changed since the last build):
```bash
docker compose up -d --build backend frontend db
```
Then in the browser (`http://localhost:4200`), logged in as a user with `TESORERIA_VER`/`TESORERIA_EDITAR`/`TESORERIA_ANULAR`:
1. Open **Tesorería > Cuentas por pagar**. Confirm the KPI cards and (if any compra-origin debt exists) the "Deuda por proveedor" table render.
2. Register a `Compra` (via the existing Compras screen) and a `Gasto` puntual (via **Tesorería > Gastos**). Confirm both appear in Cuentas por Pagar as `DEUDA`, one tagged "Compra" and one tagged "Gasto" in the "Origen" column when "Ver todas las cuentas" is expanded.
3. Filter by Origen, Proveedor, Categoría de gasto, and Estado independently; confirm the table updates correctly for each.
4. Open the compra-origin cuenta's detail page. Register a partial payment (any medio de pago — confirm the bank-selector fields appear/disappear correctly per medio) and confirm the cuenta moves to `PARCIAL` with the correct saldo. Register a second payment that completes it and confirm it moves to `PAGADO`.
5. From the "Deuda por proveedor" table, click through to a proveedor's cuenta corriente page; confirm the KPIs and cuentas list match what's expected for that proveedor.
6. Open **Historial de pagos (compras)**; confirm both payments registered in step 4 appear, filter by estado/medio/fecha, and click "Ver cuenta" to confirm it navigates back to the correct cuenta detail.
7. Anular one of the confirmed payments from the detail page; confirm the cuenta's saldo/estado recompute correctly. Anular a cuenta still in `DEUDA`; confirm it moves to `ANULADO` and no longer offers "Registrar pago".
8. Confirm **Cuentas por pagar**, **Historial de pagos (compras)**, and their sub-routes are visible in the sidebar only for a user with `TESORERIA_VER`, and disappear for a user/role without it.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/app.routes.ts frontend/src/app/layout/layout.component.ts
git commit -m "feat: rutas y navegación de Cuentas por Pagar"
```

---

## What this plan deliberately does not do

- No payment-status link on the Gastos screen — the spec (§5) calls for each row in `frontend/src/app/features/gastos/gastos.component.html` to show its `CuentaPorPagar`'s estado (`DEUDA`/`PARCIAL`/`PAGADA`) with a link into Cuentas por Pagar. That was unbuildable when the Módulo de Gastos plan merged (no Cuentas por Pagar UI existed yet) and this plan's own file scope doesn't touch `features/gastos/` — the fix belongs to a small follow-up plan that adds it there now that this frontend exists to link to.

- No Flujo de Caja sync — a further follow-up plan, now unblocked since both the backend (`2026-09-21-cuentas-por-pagar-backend.md`) and this frontend exist.
- No `GET /api/proveedores/{id}` backend endpoint — `ProveedorDetalleComponent` resolves the `Proveedor` client-side from the already-fetched `listar()` array instead (see Task 4's rationale).
- No server-side pagination on the main Cuentas por Pagar list, and no free-text search on the payments historial — both mirror the backend's actual (deliberately narrow, per the backend plan's parked findings) API surface. If either becomes a real usability problem at higher data volumes, both the backend and this frontend need a coordinated follow-up.
- No sync between an edited/deleted `Gasto` and its `CuentaPorPagar` — inherited limitation from the backend plan (see that plan's "Fuera de alcance"); a `CuentaPorPagar` viewed here may show stale data if its source `Gasto` was later edited.
