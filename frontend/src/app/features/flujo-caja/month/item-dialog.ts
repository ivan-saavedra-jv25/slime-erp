import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { CashflowStore, OneOffInput, RecurringInput } from '../core/cashflow.store';
import { ItemKind, MonthKey, OneOffItem, RecurringItem } from '../core/models';
import { MonthLabelPipe } from '../shared/month-label.pipe';

export type ItemEdit =
  { source: 'recurring'; item: RecurringItem } | { source: 'oneoff'; item: OneOffItem };

export interface ItemDialogData {
  /** Mes en contexto: valor por defecto para puntuales y para el inicio de la vigencia. */
  month: MonthKey;
  /** Meses ofrecidos en los selectores. */
  monthOptions: MonthKey[];
  /** Tipo preseleccionado al crear. */
  initialKind?: ItemKind;
  edit?: ItemEdit;
}

export type ItemDialogResult =
  | { action: 'create-recurring'; value: RecurringInput }
  | { action: 'create-oneoff'; value: OneOffInput }
  | { action: 'update-recurring'; id: string; value: RecurringInput }
  | { action: 'update-oneoff'; id: string; value: OneOffInput }
  | { action: 'replace-recurring-with-oneoff'; id: string; value: OneOffInput }
  | { action: 'replace-oneoff-with-recurring'; id: string; value: RecurringInput };

@Component({
  selector: 'app-item-dialog',
  standalone: true,
  imports: [
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatButtonToggleModule,
    MonthLabelPipe,
  ],
  templateUrl: './item-dialog.html',
  styleUrl: './item-dialog.css',
})
export class ItemDialog {
  private readonly store = inject(CashflowStore);
  private readonly dialogRef = inject<MatDialogRef<ItemDialog, ItemDialogResult>>(MatDialogRef);
  readonly data = inject<ItemDialogData>(MAT_DIALOG_DATA);

  readonly isEdit = this.data.edit !== undefined;

  readonly kind = signal<ItemKind>(this.data.edit?.item.kind ?? this.data.initialKind ?? 'expense');
  readonly description = signal(this.data.edit?.item.description ?? '');
  readonly amount = signal<number | null>(this.data.edit?.item.amount ?? null);
  readonly categoryId = signal(this.data.edit?.item.categoryId ?? '');
  readonly recurrent = signal(this.data.edit?.source !== 'oneoff');
  readonly month = signal<MonthKey>(
    this.data.edit?.source === 'oneoff' ? this.data.edit.item.month : this.data.month,
  );
  readonly fromMonth = signal<MonthKey>(
    this.data.edit?.source === 'recurring' ? this.data.edit.item.fromMonth : this.data.month,
  );
  readonly hasEnd = signal(
    this.data.edit?.source === 'recurring' && this.data.edit.item.toMonth !== null,
  );
  readonly toMonth = signal<MonthKey>(
    (this.data.edit?.source === 'recurring' ? this.data.edit.item.toMonth : null) ??
      this.data.monthOptions[this.data.monthOptions.length - 1],
  );

  readonly categories = computed(() =>
    this.kind() === 'income' ? this.store.incomeCategories() : this.store.expenseCategories(),
  );

  readonly interestAmount = signal<number | null>(this.data.edit?.item.interestAmount ?? null);

  /** El desglose capital/interés sólo aplica a cuotas de deuda. */
  readonly isDebtPayment = computed(
    () =>
      this.kind() === 'expense' &&
      this.categories().find((c) => c.id === this.categoryId())?.nature === 'financing',
  );

  readonly interestInvalid = computed(() => {
    if (!this.isDebtPayment()) return false;
    const interest = this.interestAmount();
    const amount = this.amount() ?? 0;
    return interest !== null && (interest < 0 || interest > amount);
  });

  /** El rango es inválido si el término queda antes del inicio. */
  readonly rangeInvalid = computed(
    () => this.recurrent() && this.hasEnd() && this.toMonth() < this.fromMonth(),
  );

  readonly valid = computed(() => {
    const amount = this.amount();
    return (
      this.description().trim().length > 0 &&
      amount !== null &&
      Number.isFinite(amount) &&
      amount > 0 &&
      this.categories().some((c) => c.id === this.categoryId()) &&
      !this.rangeInvalid() &&
      !this.interestInvalid()
    );
  });

  constructor() {
    this.ensureCategory();
  }

  onKindChange(kind: ItemKind): void {
    this.kind.set(kind);
    this.ensureCategory();
  }

  /** Al cambiar de tipo, la categoría anterior puede no aplicar: se elige la primera válida. */
  private ensureCategory(): void {
    const options = this.categories();
    if (!options.some((c) => c.id === this.categoryId())) {
      this.categoryId.set(options[0]?.id ?? '');
    }
  }

  private recurringValue(): RecurringInput {
    return {
      kind: this.kind(),
      categoryId: this.categoryId(),
      description: this.description().trim(),
      amount: this.amount()!,
      fromMonth: this.fromMonth(),
      toMonth: this.hasEnd() ? this.toMonth() : null,
      interestAmount: this.isDebtPayment() ? this.interestAmount() : null,
    };
  }

  private oneOffValue(): OneOffInput {
    return {
      kind: this.kind(),
      categoryId: this.categoryId(),
      description: this.description().trim(),
      amount: this.amount()!,
      month: this.month(),
      interestAmount: this.isDebtPayment() ? this.interestAmount() : null,
    };
  }

  save(): void {
    if (!this.valid()) return;
    const edit = this.data.edit;
    if (!edit) {
      this.dialogRef.close(
        this.recurrent()
          ? { action: 'create-recurring', value: this.recurringValue() }
          : { action: 'create-oneoff', value: this.oneOffValue() },
      );
      return;
    }
    const id = edit.item.id;
    if (edit.source === 'recurring') {
      this.dialogRef.close(
        this.recurrent()
          ? { action: 'update-recurring', id, value: this.recurringValue() }
          : { action: 'replace-recurring-with-oneoff', id, value: this.oneOffValue() },
      );
      return;
    }
    this.dialogRef.close(
      this.recurrent()
        ? { action: 'replace-oneoff-with-recurring', id, value: this.recurringValue() }
        : { action: 'update-oneoff', id, value: this.oneOffValue() },
    );
  }
}
