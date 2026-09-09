import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialogModule, MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { ExtenderSuscripcionRequest } from '../../../../core/models/models';

@Component({
  selector: 'app-extender-suscripcion-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, MatDialogModule, MatButtonModule, MatIconModule],
  templateUrl: './extender-suscripcion-dialog.component.html',
  styleUrl: './extender-suscripcion-dialog.component.scss',
})
export class ExtenderSuscripcionDialog {
  readonly fechaVencimiento = inject<string>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<ExtenderSuscripcionDialog>>(MatDialogRef);

  modo: 'dias' | 'fecha' = 'dias';
  dias: number | null = null;
  nuevoVencimiento = '';
  motivo = '';

  get vencimientoActualLabel(): string {
    return this.fechaVencimiento ? new Date(this.fechaVencimiento).toLocaleDateString('es-CL') : '—';
  }

  get valido(): boolean {
    if (this.modo === 'dias') {
      return this.dias !== null && this.dias > 0;
    }
    return !!this.nuevoVencimiento;
  }

  cancelar(): void {
    this.dialogRef.close();
  }

  confirmar(): void {
    if (!this.valido) return;
    const request: ExtenderSuscripcionRequest = {
      motivo: this.motivo.trim() || undefined,
      ...(this.modo === 'dias'
        ? { dias: this.dias as number }
        : { nuevoVencimiento: this.nuevoVencimiento }),
    };
    this.dialogRef.close(request);
  }
}