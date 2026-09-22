import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { FlujoCajaService, MesResumen, ResumenAnio } from '../core/flujo-caja.service';
import { currentYear, formatMonthLabel } from '../core/month';
import { ClpPipe } from '../shared/clp.pipe';
import { MonthLabelPipe } from '../shared/month-label.pipe';
import { BalanceChart } from './balance-chart';

type MonthHealth = 'ok' | 'warning' | 'critical';

interface MatrixRow {
  label: string;
  values: number[];
  /** Suma de la fila; `null` cuando sumar meses no tiene sentido (saldos). */
  total: number | null;
  style: 'category' | 'subtotal' | 'subcategory' | 'result' | 'balance';
}

interface MonthStatus {
  health: MonthHealth;
  reason: string;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    RouterLink,
    NgTemplateOutlet,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressBarModule,
    MatTooltipModule,
    BalanceChart,
    ClpPipe,
    MonthLabelPipe,
  ],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard {
  private readonly service = inject(FlujoCajaService);

  readonly year = signal(currentYear());
  readonly data = signal<ResumenAnio | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  constructor() {
    this.load(this.year());
  }

  /** Los 12 meses del año (el backend siempre los devuelve, con ceros si no hay datos). */
  readonly months = computed<MesResumen[]>(() => this.data()?.meses ?? []);

  readonly monthsKeys = computed(() => this.months().map((m) => m.mes));

  readonly hasData = computed(() =>
    this.months().some((m) => m.ingresos > 0 || m.compras > 0 || m.gastos > 0),
  );

  /** Umbral de holgura: gastos mensuales promedio del año. */
  private readonly avgEgresos = computed(() => {
    const months = this.months();
    if (months.length === 0) return 0;
    return months.reduce((a, m) => a + m.compras + m.gastos, 0) / months.length;
  });

  readonly statuses = computed<MonthStatus[]>(() =>
    this.months().map((m) => {
      if (m.saldo < 0) return { health: 'critical', reason: 'Este mes cerró con saldo negativo' };
      if (this.avgEgresos() > 0 && m.saldo < this.avgEgresos() * 0.3) {
        return { health: 'warning', reason: 'Saldo final bajo para el gasto mensual habitual' };
      }
      return { health: 'ok', reason: 'Sin alertas este mes' };
    }),
  );

  readonly saldoArrastrado = computed(() => {
    const first = this.months()[0];
    return first ? first.saldo - first.resultado : 0;
  });

  statusTooltip(index: number): string {
    return this.statuses()[index].reason;
  }

  readonly ingresosAnio = computed(() => this.months().reduce((a, m) => a + m.ingresos, 0));
  readonly egresosAnio = computed(() =>
    this.months().reduce((a, m) => a + m.compras + m.gastos, 0),
  );
  readonly resultadoAnio = computed(() => this.ingresosAnio() - this.egresosAnio());

  readonly saldoFinal = computed(() => {
    const months = this.months();
    return months[months.length - 1]?.saldo ?? 0;
  });

  readonly lowestMonth = computed<MesResumen | null>(() =>
    this.months().reduce<MesResumen | null>(
      (worst, m) => (worst === null || m.saldo < worst.saldo ? m : worst),
      null,
    ),
  );

  readonly chartPoints = computed(() =>
    this.months().map((m) => ({ label: formatMonthLabel(m.mes), value: m.saldo })),
  );

  readonly openingRow = computed<MatrixRow>(() => ({
    label: 'Saldo inicial',
    values: this.months().map((m) => m.saldo - m.resultado),
    total: null,
    style: 'balance',
  }));

  readonly ingresosRow = computed<MatrixRow>(() =>
    this.subtotalRow('Ingresos', this.months().map((m) => m.ingresos)),
  );

  readonly comprasRow = computed<MatrixRow>(() =>
    this.categoryRow('Compras', this.months().map((m) => m.compras)),
  );

  readonly gastosRow = computed<MatrixRow>(() =>
    this.subtotalRow('Gastos', this.months().map((m) => m.gastos)),
  );

  readonly gastosCategoriaRows = computed<MatrixRow[]>(() => {
    const months = this.months();
    const totals = new Map<string, { categoria: string; total: number }>();
    for (const mes of months) {
      for (const item of mes.gastosPorCategoria) {
        const key = String(item.categoriaGastoId ?? item.categoria);
        const acc = totals.get(key);
        if (acc) acc.total += item.total;
        else totals.set(key, { categoria: item.categoria, total: item.total });
      }
    }
    return [...totals.entries()]
      .filter(([, v]) => v.total > 0)
      .sort((a, b) => b[1].total - a[1].total)
      .map(([key, v]) => ({
        label: v.categoria,
        values: months.map(
          (m) =>
            m.gastosPorCategoria.find((g) => String(g.categoriaGastoId ?? g.categoria) === key)
              ?.total ?? 0,
        ),
        total: v.total,
        style: 'subcategory' as const,
      }));
  });

  readonly egresosRow = computed<MatrixRow>(() =>
    this.subtotalRow(
      'Total egresos',
      this.months().map((m) => m.compras + m.gastos),
    ),
  );

  readonly resultadoRow = computed<MatrixRow>(() => {
    const values = this.months().map((m) => m.resultado);
    return { label: 'Resultado del mes', values, total: values.reduce((a, b) => a + b, 0), style: 'result' };
  });

  readonly closingRow = computed<MatrixRow>(() => ({
    label: 'Saldo final',
    values: this.months().map((m) => m.saldo),
    total: null,
    style: 'balance',
  }));

  private subtotalRow(label: string, values: number[]): MatrixRow {
    const total = values.reduce((a, b) => a + b, 0);
    return { label, values, total, style: total === 0 ? 'category' : 'subtotal' };
  }

  private categoryRow(label: string, values: number[]): MatrixRow {
    return { label, values, total: values.reduce((a, b) => a + b, 0), style: 'category' };
  }

  stepYear(delta: number): void {
    const next = this.year() + delta;
    this.year.set(next);
    this.load(next);
  }

  private load(anio: number): void {
    this.loading.set(true);
    this.error.set(null);
    this.service
      .resumenAnio(anio)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (resumen) => this.data.set(resumen),
        error: () =>
          this.error.set('No se pudo cargar el flujo del año. Intenta de nuevo más tarde.'),
      });
  }
}