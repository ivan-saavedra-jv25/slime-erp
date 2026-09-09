import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { SuscripcionRequest } from '../../../../core/models/models';
import { PlanService } from '../../../../core/services/plan.service';
import { EmpresaAdminService } from '../../../../core/services/empresa-admin.service';
import { Empresa, Plan } from '../../../../core/models/models';

@Component({
  selector: 'app-nueva-suscripcion-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, MatDialogModule, MatButtonModule, MatIconModule],
  templateUrl: './nueva-suscripcion-dialog.component.html',
  styleUrl: './nueva-suscripcion-dialog.component.scss',
})
export class NuevaSuscripcionDialog implements OnInit {
  private readonly dialogRef = inject<MatDialogRef<NuevaSuscripcionDialog>>(MatDialogRef);
  private readonly planService = inject(PlanService);
  private readonly empresaService = inject(EmpresaAdminService);

  planes: Plan[] = [];
  empresas: Empresa[] = [];
  cargandoCatalogo = true;

  companyId: number | null = null;
  planId: number | null = null;
  fechaInicio = '';
  fechaVencimiento = '';
  cicloFacturacion: 'MONTHLY' | 'ANNUAL' = 'MONTHLY';
  precio: number | null = null;
  estado: string = '';
  periodoGraciaDias: number | null = null;

  ngOnInit(): void {
    this.planService.listar().subscribe({
      next: (planes) => {
        this.planes = planes;
        this.revisarCatalogo();
      },
    });
    this.empresaService.listar({ page: 0, limit: 100 }).subscribe({
      next: (pagina) => {
        this.empresas = pagina.content;
        this.revisarCatalogo();
      },
    });
  }

  private revisarCatalogo(): void {
    if (this.planes.length && this.empresas.length) {
      this.cargandoCatalogo = false;
    }
  }

  get valido(): boolean {
    if (!this.companyId || !this.planId || this.precio === null) return false;
    if (!this.fechaInicio || !this.fechaVencimiento) return false;
    return this.fechaVencimiento > this.fechaInicio;
  }

  cancelar(): void {
    this.dialogRef.close();
  }

  confirmar(): void {
    if (!this.valido || this.companyId === null || this.planId === null || this.precio === null) return;
    const request: SuscripcionRequest = {
      companyId: this.companyId,
      planId: this.planId,
      fechaInicio: this.fechaInicio,
      fechaVencimiento: this.fechaVencimiento,
      cicloFacturacion: this.cicloFacturacion,
      precio: this.precio,
      ...(this.estado ? { estado: this.estado as never } : {}),
      ...(this.periodoGraciaDias !== null ? { periodoGraciaDias: this.periodoGraciaDias } : {}),
    };
    this.dialogRef.close(request);
  }
}