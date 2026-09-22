import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { DetalleMes, FlujoCajaService } from '../core/flujo-caja.service';
import { MonthKey } from '../core/models';
import {
  addMonths,
  currentMonthKey,
  isValidMonthKey,
  januaryOf,
  monthKeysFrom,
  parseMonth,
  toMonthKey,
} from '../core/month';
import { ClpPipe } from '../shared/clp.pipe';
import { MonthLabelPipe } from '../shared/month-label.pipe';

type MonthHealth = 'ok' | 'warning' | 'critical';

@Component({
  selector: 'app-month-detail',
  standalone: true,
  imports: [
    RouterLink,
    FormsModule,
    NgTemplateOutlet,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressBarModule,
    MatTooltipModule,
    ClpPipe,
    MonthLabelPipe,
  ],
  templateUrl: './month-detail.html',
  styleUrl: './month-detail.css',
})
export class MonthDetail {
  private readonly service = inject(FlujoCajaService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly selected = signal<MonthKey>(currentMonthKey());
  readonly detalle = signal<DetalleMes | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  constructor() {
    const param = this.route.snapshot.queryParamMap.get('mes');
    const initial = param && isValidMonthKey(param) ? (param as MonthKey) : currentMonthKey();
    this.selected.set(initial);
    this.load(initial);
  }

  readonly year = computed(() => parseMonth(this.selected()).year);
  readonly months = computed(() => monthKeysFrom(januaryOf(this.year()), 12));

  readonly saldoInicial = computed(() => {
    const d = this.detalle();
    return d ? d.saldo - d.resultado : 0;
  });

  readonly totalEgresos = computed(() => {
    const d = this.detalle();
    return d ? d.compras + d.gastos : 0;
  });

  readonly isEmpty = computed(() => {
    const d = this.detalle();
    return !!d && d.ingresos === 0 && d.compras === 0 && d.gastos === 0;
  });

  readonly status = computed<{ health: MonthHealth; reason: string } | null>(() => {
    const d = this.detalle();
    if (!d) return null;
    if (d.saldo < 0) {
      return { health: 'critical', reason: 'Este mes cerró con saldo negativo' };
    }
    if (this.totalEgresos() > 0 && d.saldo < this.totalEgresos()) {
      return {
        health: 'warning',
        reason: 'El saldo final no cubre los egresos del propio mes',
      };
    }
    return null;
  });

  selectMonth(key: MonthKey): void {
    this.selected.set(key);
    this.router.navigate([], { queryParams: { mes: key }, replaceUrl: true });
    this.load(key);
  }

  step(delta: number): void {
    this.selectMonth(addMonths(this.selected(), delta));
  }

  stepYear(delta: number): void {
    const { year, month } = parseMonth(this.selected());
    this.selectMonth(toMonthKey(year + delta, month));
  }

  formatearFecha(fecha: string): string {
    const parts = fecha.split('-').map(Number);
    const [y, m, d] = parts;
    if (parts.length !== 3 || !y || !m || !d) return fecha;
    return new Date(y, m - 1, d).toLocaleDateString('es-CL', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
    });
  }

  private load(key: MonthKey): void {
    this.loading.set(true);
    this.error.set(null);
    this.service
      .detalleMes(key)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (detalle) => this.detalle.set(detalle),
        error: () =>
          this.error.set(
            'No se pudo cargar el detalle del mes. Intenta de nuevo más tarde.',
          ),
      });
  }
}