import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MonthKey } from '../core/models';
import { ClpPipe } from '../shared/clp.pipe';
import { MonthLabelPipe } from '../shared/month-label.pipe';

export interface OverrideDialogData {
  description: string;
  month: MonthKey;
  /** Monto vigente en el mes (con override aplicado si existe). */
  currentAmount: number;
  /** Monto de la plantilla, para poder restaurarlo. */
  templateAmount: number;
  hasOverride: boolean;
}

export type OverrideDialogResult = { action: 'set'; amount: number } | { action: 'clear' };

@Component({
  selector: 'app-override-dialog',
  standalone: true,
  imports: [FormsModule, MatDialogModule, MatButtonModule, MonthLabelPipe, ClpPipe],
  template: `
    <h2 mat-dialog-title>Ajustar solo {{ data.month | monthLabel: 'long' }}</h2>
    <mat-dialog-content class="body">
      <p class="subject">{{ data.description }}</p>
      <div class="form-group">
        <label for="overrideAmount">Monto de este mes</label>
        <input
          id="overrideAmount"
          type="number"
          min="1"
          step="1000"
          cdkFocusInitial
          [ngModel]="amount()"
          (ngModelChange)="amount.set($event === null || $event === '' ? null : +$event)"
        />
        <span class="hint">El resto de los meses mantiene {{ data.templateAmount | clp }}</span>
      </div>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      @if (data.hasOverride) {
        <button type="button" mat-button (click)="restore()">Restaurar plantilla</button>
      }
      <button type="button" mat-button mat-dialog-close>Cancelar</button>
      <button type="button" mat-flat-button color="primary" [disabled]="!valid()" (click)="save()">
        Guardar
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .body {
      display: flex;
      flex-direction: column;
      min-width: min(360px, 80vw);
      padding-top: var(--space-2);
    }

    .subject {
      margin: 0 0 var(--space-4);
      font: var(--font-body);
      color: var(--text-body);
    }
  `,
})
export class OverrideDialog {
  private readonly dialogRef =
    inject<MatDialogRef<OverrideDialog, OverrideDialogResult>>(MatDialogRef);
  readonly data = inject<OverrideDialogData>(MAT_DIALOG_DATA);

  readonly amount = signal<number | null>(this.data.currentAmount);
  readonly valid = computed(() => {
    const value = this.amount();
    return value !== null && Number.isFinite(value) && value > 0;
  });

  save(): void {
    if (!this.valid()) return;
    this.dialogRef.close({ action: 'set', amount: this.amount()! });
  }

  restore(): void {
    this.dialogRef.close({ action: 'clear' });
  }
}
