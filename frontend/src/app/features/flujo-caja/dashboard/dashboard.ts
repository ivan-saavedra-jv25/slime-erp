import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { CashflowStore } from '../core/cashflow.store';
import { MonthHealth, monthStatus, yearAlerts } from '../core/health';
import { MonthKey } from '../core/models';
import { ClpPipe } from '../shared/clp.pipe';
import { MonthLabelPipe } from '../shared/month-label.pipe';
import { BalanceChart } from './balance-chart';

interface MatrixRow {
  label: string;
  values: number[];
  /** Suma de la fila; `null` cuando sumar meses no tiene sentido (saldos). */
  total: number | null;
  style: 'category' | 'subtotal' | 'result' | 'balance';
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
    MatTooltipModule,
    BalanceChart,
    ClpPipe,
    MonthLabelPipe,
  ],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard {
  private readonly store = inject(CashflowStore);

  readonly projection = this.store.projection;
  readonly statuses = computed(() => this.projection().map((m) => monthStatus(m)));
  readonly alerts = computed(() => yearAlerts(this.projection()));

  /** Peor semáforo de los meses con alerta, para colorear el contador. */
  readonly worstHealth = computed<MonthHealth>(() => {
    const alerts = this.alerts();
    if (alerts.critical > 0) return 'critical';
    return alerts.warning > 0 ? 'warning' : 'ok';
  });
  readonly year = this.store.year;
  readonly baseYear = this.store.baseYear;
  readonly canGoToPreviousYear = this.store.canGoToPreviousYear;
  readonly isCarriedOver = this.store.isCarriedOver;
  readonly months = computed(() => this.projection().map((m) => m.month));

  readonly isEmpty = computed(() => {
    const state = this.store.state();
    return state.recurring.length === 0 && state.oneOff.length === 0;
  });

  readonly openingRow = computed<MatrixRow>(() => ({
    label: 'Saldo inicial',
    values: this.projection().map((m) => m.openingBalance),
    total: null,
    style: 'balance',
  }));

  readonly incomeRows = computed(() => this.categoryRows('income'));
  readonly expenseRows = computed(() => this.categoryRows('expense'));

  readonly incomeTotalRow = computed<MatrixRow>(() =>
    this.subtotalRow(
      'Total ingresos',
      this.projection().map((m) => m.totalIncome),
    ),
  );

  readonly expenseTotalRow = computed<MatrixRow>(() =>
    this.subtotalRow(
      'Total gastos',
      this.projection().map((m) => m.totalExpense),
    ),
  );

  readonly operationalRow = computed<MatrixRow>(() => {
    const values = this.projection().map((m) => m.operationalNet);
    return {
      label: 'Resultado operacional',
      values,
      total: values.reduce((a, b) => a + b, 0),
      style: 'result',
    };
  });

  /** Las filas de provisión sólo aparecen si hay algo provisionado. */
  readonly hasProvisions = computed(() => {
    const { taxRatePercent, contingencyMonths } = this.store.settings().provisions;
    return taxRatePercent > 0 || contingencyMonths > 0;
  });

  readonly provisionRow = computed<MatrixRow>(() => ({
    label: 'Impuestos provisionados',
    values: this.projection().map((m) => m.accumulatedTaxProvision),
    total: null,
    style: 'subtotal',
  }));

  readonly availableRow = computed<MatrixRow>(() => ({
    label: 'Disponible real',
    values: this.projection().map((m) => m.availableBalance),
    total: null,
    style: 'balance',
  }));

  readonly finalAvailable = computed(() => {
    const months = this.projection();
    return months[months.length - 1]?.availableBalance ?? 0;
  });

  readonly netRow = computed<MatrixRow>(() => {
    const values = this.projection().map((m) => m.net);
    return {
      label: 'Resultado del mes',
      values,
      total: values.reduce((a, b) => a + b, 0),
      style: 'result',
    };
  });

  readonly closingRow = computed<MatrixRow>(() => ({
    label: 'Saldo final',
    values: this.projection().map((m) => m.closingBalance),
    total: null,
    style: 'balance',
  }));

  readonly finalBalance = computed(() => {
    const months = this.projection();
    return months[months.length - 1]?.closingBalance ?? 0;
  });

  readonly lowestMonth = computed(() =>
    this.projection().reduce((worst, m) => (m.closingBalance < worst.closingBalance ? m : worst)),
  );

  private subtotalRow(label: string, values: number[]): MatrixRow {
    return { label, values, total: values.reduce((a, b) => a + b, 0), style: 'subtotal' };
  }

  private categoryRows(kind: 'income' | 'expense'): MatrixRow[] {
    const months = this.projection();
    const categories =
      kind === 'income' ? this.store.incomeCategories() : this.store.expenseCategories();
    return (
      categories
        .map((category) => {
          const values = months.map(
            (m) =>
              (kind === 'income' ? m.incomeByCategory : m.expenseByCategory).get(category.id) ?? 0,
          );
          return {
            label: category.name,
            values,
            total: values.reduce((a, b) => a + b, 0),
            style: 'category' as const,
          };
        })
        // Una categoría sin montos en todo el horizonte sólo agrega ruido.
        .filter((row) => row.total !== 0)
    );
  }

  goToMonth(month: MonthKey): void {
    this.store.selectMonth(month);
  }

  stepYear(delta: number): void {
    this.store.stepYear(delta);
  }

  statusTooltip(index: number): string {
    const reasons = this.statuses()[index].reasons;
    return reasons.length === 0 ? 'Sin alertas este mes' : reasons.join('. ');
  }

  loadSample(): void {
    this.store.loadSample();
  }
}
