import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { MatDialogModule, MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

export interface ConfirmActionData {
  titulo: string;
  entidad: string;
  accion: string;
}

@Component({
  selector: 'app-confirm-action-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, MatDialogModule, MatButtonModule, MatIconModule],
  templateUrl: './confirm-action-dialog.component.html',
  styleUrl: './confirm-action-dialog.component.scss',
})
export class ConfirmActionDialog {
  private readonly dialogRef = inject<MatDialogRef<ConfirmActionDialog>>(MatDialogRef);
  readonly data = inject<ConfirmActionData>(MAT_DIALOG_DATA);

  motivo = '';

  cancelar(): void {
    this.dialogRef.close();
  }

  confirmar(): void {
    if (!this.motivo.trim()) return;
    this.dialogRef.close(this.motivo.trim());
  }
}