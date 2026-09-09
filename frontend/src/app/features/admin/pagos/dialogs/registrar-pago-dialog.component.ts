import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialogModule, MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Empresa, MedioPago, RegistrarPagoManualRequest } from '../../../../core/models/models';
import { EmpresaAdminService } from '../../../../core/services/empresa-admin.service';

export const METODOS_PAGO: Array<{ valor: MedioPago; etiqueta: string }> = [
  { valor: 'TRANSFERENCIA', etiqueta: 'Transferencia' },
  { valor: 'TARJETA', etiqueta: 'Tarjeta' },
  { valor: 'EFECTIVO', etiqueta: 'Efectivo' },
  { valor: 'CHEQUE', etiqueta: 'Cheque' },
];

export interface RegistrarPagoData {
  companyId?: number;
  suscripcionId?: number;
}

@Component({
  selector: 'app-registrar-pago-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, MatDialogModule, MatButtonModule, MatIconModule],
  templateUrl: './registrar-pago-dialog.component.html',
  styleUrl: './registrar-pago-dialog.component.scss',
})
export class RegistrarPagoDialog implements OnInit {
  private readonly dialogRef = inject<MatDialogRef<RegistrarPagoDialog>>(MatDialogRef);
  private readonly empresaService = inject(EmpresaAdminService);
  private readonly data = inject<RegistrarPagoData | undefined>(MAT_DIALOG_DATA, { optional: true });

  readonly metodos = METODOS_PAGO;

  empresas: Empresa[] = [];
  cargandoCatalogo = true;

  companyId: number | null = this.data?.companyId ?? null;
  suscripcionId = this.data?.suscripcionId;
  monto: number | null = null;
  metodo: MedioPago | '' = '';
  referencia = '';
  fecha = '';

  ngOnInit(): void {
    this.empresaService.listar({ page: 0, limit: 100 }).subscribe({
      next: (pagina) => {
        this.empresas = pagina.content;
        this.cargandoCatalogo = false;
      },
    });
  }

  get valido(): boolean {
    return this.companyId !== null && this.metodo !== '' && this.monto !== null && this.monto > 0;
  }

  cancelar(): void {
    this.dialogRef.close();
  }

  confirmar(): void {
    if (!this.valido || this.companyId === null || this.metodo === '' || this.monto === null) return;
    const request: RegistrarPagoManualRequest = {
      companyId: this.companyId,
      ...(this.suscripcionId !== undefined ? { suscripcionId: this.suscripcionId } : {}),
      monto: this.monto,
      metodo: this.metodo as MedioPago,
      ...(this.referencia.trim() ? { referencia: this.referencia.trim() } : {}),
      ...(this.fecha ? { fecha: `${this.fecha}T12:00:00` } : {}),
    };
    this.dialogRef.close(request);
  }
}