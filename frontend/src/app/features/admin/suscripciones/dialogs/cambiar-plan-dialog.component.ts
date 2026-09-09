import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialogModule, MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { CambiarPlanRequest, Plan } from '../../../../core/models/models';

export interface CambiarPlanData {
  planes: Plan[];
  planId: number;
}

@Component({
  selector: 'app-cambiar-plan-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, MatDialogModule, MatButtonModule, MatIconModule],
  templateUrl: './cambiar-plan-dialog.component.html',
  styleUrl: './cambiar-plan-dialog.component.scss',
})
export class CambiarPlanDialog {
  readonly data = inject<CambiarPlanData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<CambiarPlanDialog>>(MatDialogRef);

  nuevoPlanId: number | null = null;
  motivo = '';

  get puedeCambiar(): boolean {
    return this.nuevoPlanId !== null && this.nuevoPlanId !== this.data.planId;
  }

  cancelar(): void {
    this.dialogRef.close();
  }

  confirmar(): void {
    if (!this.puedeCambiar || this.nuevoPlanId === null) return;
    const request: CambiarPlanRequest = {
      planId: this.nuevoPlanId,
      motivo: this.motivo.trim() || undefined,
    };
    this.dialogRef.close(request);
  }
}