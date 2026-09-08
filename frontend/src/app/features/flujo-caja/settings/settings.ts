import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { CashflowStore } from '../core/cashflow.store';
import { currentYear } from '../core/month';
import { Category, ItemKind, Nature, Variability } from '../core/models';
import { ClpPipe } from '../shared/clp.pipe';
import { ConfirmDialog, ConfirmDialogData } from '../shared/confirm-dialog';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatTooltipModule,
    ClpPipe,
  ],
  templateUrl: './settings.html',
  styleUrl: './settings.css',
})
export class SettingsPage {
  private readonly store = inject(CashflowStore);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  readonly settings = this.store.settings;
  readonly categories = this.store.categories;
  readonly incomeCategories = this.store.incomeCategories;
  readonly expenseCategories = this.store.expenseCategories;

  readonly baseYear = this.store.baseYear;

  /** Años ofrecidos como inicio del flujo. */
  readonly yearOptions = computed(() => {
    const first = currentYear() - 3;
    const options = Array.from({ length: 9 }, (_, i) => first + i);
    return options.includes(this.baseYear()) ? options : [...options, this.baseYear()].sort();
  });

  readonly provisions = computed(() => this.settings().provisions);

  readonly newCategoryName = signal('');
  readonly newCategoryKind = signal<ItemKind>('expense');
  readonly newCategoryNature = signal<Nature>('operational');
  readonly newCategoryVariability = signal<Variability>('fixed');

  readonly natureLabels: { value: Nature; label: string; hint: string }[] = [
    { value: 'operational', label: 'Operacional', hint: 'Caja que genera el giro' },
    { value: 'non_operational', label: 'No operacional', hint: 'Puntual, ajeno al giro' },
    { value: 'financing', label: 'Financiamiento', hint: 'Préstamos y sus cuotas' },
  ];

  readonly variabilityLabels: { value: Variability; label: string }[] = [
    { value: 'fixed', label: 'Fijo' },
    { value: 'variable', label: 'Variable' },
  ];

  setOpeningBalance(value: string | number | null): void {
    const amount = typeof value === 'number' ? value : Number(value);
    if (!Number.isFinite(amount)) return;
    this.store.updateSettings({ openingBalance: Math.round(amount) });
  }

  setBaseYear(year: number): void {
    this.store.setBaseYear(year);
  }

  categoryUsage(id: string): number {
    return this.store.categoryUsage(id);
  }

  addCategory(): void {
    const name = this.newCategoryName().trim();
    if (name.length === 0) return;
    this.store.addCategory({
      name,
      kind: this.newCategoryKind(),
      nature: this.newCategoryNature(),
      variability: this.newCategoryVariability(),
    });
    this.newCategoryName.set('');
  }

  updateCategory(id: string, patch: Partial<Omit<Category, 'id'>>): void {
    this.store.updateCategory(id, patch);
  }

  setTaxRate(value: string | number | null): void {
    const rate = typeof value === 'number' ? value : Number(value);
    if (!Number.isFinite(rate)) return;
    this.store.updateProvisions({ taxRatePercent: Math.min(100, Math.max(0, rate)) });
  }

  setContingencyMonths(value: string | number | null): void {
    const months = typeof value === 'number' ? value : Number(value);
    if (!Number.isFinite(months)) return;
    this.store.updateProvisions({ contingencyMonths: Math.min(24, Math.max(0, months)) });
  }

  renameCategory(id: string, name: string): void {
    const trimmed = name.trim();
    if (trimmed.length === 0) return;
    this.store.renameCategory(id, trimmed);
  }

  removeCategory(id: string, name: string): void {
    const usage = this.store.categoryUsage(id);
    if (usage > 0) {
      this.snackBar.open(
        `"${name}" está en uso por ${usage} movimiento(s). Reasígnalos antes de borrarla.`,
        'Entendido',
        { duration: 5000 },
      );
      return;
    }
    this.store.removeCategory(id);
  }

  exportBackup(): void {
    this.store.exportBackup();
  }

  importBackup(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;

    const data: ConfirmDialogData = {
      title: 'Reemplazar los datos actuales',
      message: `Se cargará "${file.name}" y se perderá el flujo que tienes ahora. ¿Continuar?`,
      confirmLabel: 'Importar',
      destructive: true,
    };
    this.dialog
      .open(ConfirmDialog, { data })
      .afterClosed()
      .subscribe(async (confirmed?: boolean) => {
        if (!confirmed) return;
        const text = await file.text();
        if (this.store.importBackup(text)) {
          this.snackBar.open('Respaldo importado.', undefined, { duration: 4000 });
        } else {
          this.snackBar.open('El archivo no es un respaldo válido.', 'Cerrar', { duration: 6000 });
        }
      });
  }

  loadSample(): void {
    const data: ConfirmDialogData = {
      title: 'Cargar flujo de ejemplo',
      message:
        'Se reemplazan los movimientos actuales por un ejemplo: ventas mensuales, sueldos, ' +
        'arriendo, servicios y algunos movimientos puntuales. Exporta un respaldo antes si ' +
        'quieres conservar lo que tienes.',
      confirmLabel: 'Cargar ejemplo',
      destructive: true,
    };
    this.dialog
      .open(ConfirmDialog, { data })
      .afterClosed()
      .subscribe((confirmed?: boolean) => {
        if (!confirmed) return;
        this.store.loadSample();
        this.snackBar.open('Flujo de ejemplo cargado.', undefined, { duration: 4000 });
      });
  }

  resetAll(): void {
    const data: ConfirmDialogData = {
      title: 'Borrar todo',
      message:
        'Se eliminarán movimientos, categorías propias y ajustes. Esta acción no se puede deshacer.',
      confirmLabel: 'Borrar todo',
      destructive: true,
    };
    this.dialog
      .open(ConfirmDialog, { data })
      .afterClosed()
      .subscribe((confirmed?: boolean) => {
        if (!confirmed) return;
        this.store.resetAll();
        this.snackBar.open('Flujo reiniciado.', undefined, { duration: 4000 });
      });
  }
}
