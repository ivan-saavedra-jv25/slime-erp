import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { FormsModule } from '@angular/forms';
import { CashflowStore } from '../core/cashflow.store';
import { monthStatus } from '../core/health';
import { addMonths } from '../core/month';
import { ItemKind, MonthKey } from '../core/models';
import { Line } from '../core/projection';
import { ClpPipe } from '../shared/clp.pipe';
import { MonthLabelPipe } from '../shared/month-label.pipe';
import { ConfirmDialog, ConfirmDialogData } from '../shared/confirm-dialog';
import { ItemDialog, ItemDialogData, ItemDialogResult } from './item-dialog';
import { OverrideDialog, OverrideDialogData, OverrideDialogResult } from './override-dialog';

interface CategoryGroup {
  categoryId: string;
  name: string;
  total: number;
  lines: Line[];
}

@Component({
  selector: 'app-month-detail',
  standalone: true,
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
  templateUrl: './month-detail.html',
  styleUrl: './month-detail.css',
})
export class MonthDetail {
  private readonly store = inject(CashflowStore);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  readonly months = this.store.months;
  readonly year = this.store.year;
  readonly canGoToPreviousYear = this.store.canGoToPreviousYear;
  readonly selectedMonth = this.store.selectedMonth;
  readonly projection = this.store.currentProjection;

  readonly status = computed(() => monthStatus(this.projection()));

  readonly incomeGroups = computed(() => this.group(this.projection().incomes));
  readonly expenseGroups = computed(() => this.group(this.projection().expenses));

  readonly hasPrev = computed(
    () => this.months().indexOf(this.selectedMonth()) > 0 || this.canGoToPreviousYear(),
  );

  /** Meses ofrecidos en los diálogos: el horizonte más un año de margen a cada lado. */
  private readonly monthOptions = computed(() => {
    const months = this.months();
    const start = addMonths(months[0], -12);
    return Array.from({ length: months.length + 24 }, (_, i) => addMonths(start, i));
  });

  private group(lines: readonly Line[]): CategoryGroup[] {
    const groups = new Map<string, CategoryGroup>();
    for (const line of lines) {
      let group = groups.get(line.categoryId);
      if (!group) {
        group = {
          categoryId: line.categoryId,
          name: this.store.categoryName(line.categoryId),
          total: 0,
          lines: [],
        };
        groups.set(line.categoryId, group);
      }
      group.total += line.amount;
      group.lines.push(line);
    }
    return [...groups.values()].sort((a, b) => b.total - a.total);
  }

  selectMonth(month: MonthKey): void {
    this.store.selectMonth(month);
  }

  /** Avanza de mes; al pasarse de diciembre o enero, salta de año. */
  step(delta: number): void {
    const months = this.months();
    const index = months.indexOf(this.selectedMonth()) + delta;
    if (index >= 0 && index < months.length) {
      this.store.selectMonth(months[index]);
      return;
    }
    if (index < 0 && !this.canGoToPreviousYear()) return;
    const targetYear = this.year() + (index < 0 ? -1 : 1);
    this.store.setYear(targetYear);
    const newMonths = this.months();
    this.store.selectMonth(index < 0 ? newMonths[newMonths.length - 1] : newMonths[0]);
  }

  stepYear(delta: number): void {
    this.store.stepYear(delta);
  }

  addItem(initialKind: ItemKind): void {
    const data: ItemDialogData = {
      month: this.selectedMonth(),
      monthOptions: this.monthOptions(),
      initialKind,
    };
    this.dialog
      .open(ItemDialog, { data })
      .afterClosed()
      .subscribe((result?: ItemDialogResult) => this.apply(result));
  }

  editLine(line: Line): void {
    const edit = this.findEdit(line);
    if (!edit) return;
    const data: ItemDialogData = {
      month: this.selectedMonth(),
      monthOptions: this.monthOptions(),
      edit,
    };
    this.dialog
      .open(ItemDialog, { data })
      .afterClosed()
      .subscribe((result?: ItemDialogResult) => this.apply(result));
  }

  editThisMonthOnly(line: Line): void {
    const template = this.store.state().recurring.find((r) => r.id === line.id);
    if (!template) return;
    const month = this.selectedMonth();
    const data: OverrideDialogData = {
      description: template.description,
      month,
      currentAmount: line.amount,
      templateAmount: template.amount,
      hasOverride: line.overridden,
    };
    this.dialog
      .open(OverrideDialog, { data })
      .afterClosed()
      .subscribe((result?: OverrideDialogResult) => {
        if (!result) return;
        if (result.action === 'clear') {
          this.store.clearOverride(line.id, month);
          this.snackBar.open('Se restauró el monto de la plantilla.', undefined, {
            duration: 3000,
          });
          return;
        }
        this.store.setOverride(line.id, month, result.amount);
        this.snackBar.open('Ajuste aplicado sólo a este mes.', undefined, { duration: 3000 });
      });
  }

  skipThisMonth(line: Line): void {
    this.store.setOverride(line.id, this.selectedMonth(), null);
    this.snackBar
      .open('Omitido en este mes.', 'Deshacer', { duration: 5000 })
      .onAction()
      .subscribe(() => this.store.clearOverride(line.id, this.selectedMonth()));
  }

  restoreTemplate(line: Line): void {
    this.store.clearOverride(line.id, this.selectedMonth());
  }

  removeLine(line: Line): void {
    const isSeries = line.source === 'recurring';
    const data: ConfirmDialogData = {
      title: isSeries ? 'Eliminar la serie completa' : 'Eliminar movimiento',
      message: isSeries
        ? `"${line.description}" se eliminará de todos los meses. Para sacarlo sólo de este mes usa "Omitir este mes".`
        : `"${line.description}" se eliminará de ${this.selectedMonth()}.`,
      confirmLabel: 'Eliminar',
      destructive: true,
    };
    this.dialog
      .open(ConfirmDialog, { data })
      .afterClosed()
      .subscribe((confirmed?: boolean) => {
        if (!confirmed) return;
        if (isSeries) this.store.removeRecurring(line.id);
        else this.store.removeOneOff(line.id);
      });
  }

  private findEdit(line: Line): ItemDialogData['edit'] {
    if (line.source === 'recurring') {
      const item = this.store.state().recurring.find((r) => r.id === line.id);
      return item ? { source: 'recurring', item } : undefined;
    }
    const item = this.store.state().oneOff.find((o) => o.id === line.id);
    return item ? { source: 'oneoff', item } : undefined;
  }

  private apply(result?: ItemDialogResult): void {
    if (!result) return;
    switch (result.action) {
      case 'create-recurring':
        this.store.addRecurring(result.value);
        break;
      case 'create-oneoff':
        this.store.addOneOff(result.value);
        break;
      case 'update-recurring':
        this.store.updateRecurring(result.id, result.value);
        break;
      case 'update-oneoff':
        this.store.updateOneOff(result.id, result.value);
        break;
      case 'replace-recurring-with-oneoff':
        // Dejó de ser recurrente: se borra la serie (y sus overrides) y queda un puntual.
        this.store.removeRecurring(result.id);
        this.store.addOneOff(result.value);
        break;
      case 'replace-oneoff-with-recurring':
        this.store.removeOneOff(result.id);
        this.store.addRecurring(result.value);
        break;
    }
  }
}
