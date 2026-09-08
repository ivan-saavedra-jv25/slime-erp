import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatTabsModule } from '@angular/material/tabs';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { EmpresaAdminService } from '../../../core/services/empresa-admin.service';
import {
  EmpresaDetalle,
  EstadoEmpresa,
  ESTADOS_EMPRESA,
  ETIQUETAS_ESTADO_EMPRESA,
} from '../../../core/models/models';
import { ConfirmActionDialog } from '../../../core/components/confirm-action-dialog/confirm-action-dialog.component';

@Component({
  selector: 'app-empresas-detalle',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatTabsModule,
    MatButtonModule,
    MatIconModule,
    MatDialogModule,
  ],
  templateUrl: './empresas-detalle.component.html',
  styleUrl: './empresas-detalle.component.scss',
})
export class EmpresasDetalleComponent implements OnInit {
  empresa: EmpresaDetalle | null = null;
  cargando = true;
  error = '';
  estados = ESTADOS_EMPRESA;
  nuevoEstado: EstadoEmpresa | '' = '';

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly empresaService = inject(EmpresaAdminService);

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.cargar(id);
  }

  cargar(id: number): void {
    this.cargando = true;
    this.error = '';
    this.empresaService.detalle(id).subscribe({
      next: (empresa) => {
        this.empresa = empresa;
        this.cargando = false;
      },
      error: (err) => {
        this.cargando = false;
        this.error = err?.error?.error ?? 'No se pudo cargar la empresa.';
      },
    });
  }

  etiquetaEstado(estado: EstadoEmpresa): string {
    return ETIQUETAS_ESTADO_EMPRESA[estado] ?? estado;
  }

  claseEstado(estado: EstadoEmpresa): string {
    switch (estado) {
      case 'ACTIVE':
        return 'tag--success';
      case 'TRIAL':
        return 'tag--info';
      case 'EXPIRED':
        return 'tag--warning';
      case 'SUSPENDED':
      case 'BLOCKED':
      case 'CANCELLED':
        return 'tag--error';
      default:
        return 'tag--neutral';
    }
  }

  confirmarCambioEstado(): void {
    if (!this.empresa || !this.nuevoEstado) return;
    const dialogRef = this.dialog.open(ConfirmActionDialog, {
      width: '460px',
      data: {
        titulo: `${ETIQUETAS_ESTADO_EMPRESA[this.nuevoEstado]} empresa`,
        entidad: this.empresa.nombre,
        accion: `Cambiar el estado a "${ETIQUETAS_ESTADO_EMPRESA[this.nuevoEstado]}"`,
      },
    });
    dialogRef.afterClosed().subscribe((motivo?: string) => {
      if (!motivo || !this.empresa) return;
      this.empresaService
        .cambiarEstado(this.empresa.id, { estado: this.nuevoEstado as EstadoEmpresa, motivo })
        .subscribe({
          next: () => {
            this.nuevoEstado = '';
            this.cargar(this.empresa!.id);
          },
          error: (err) => {
            this.error = err?.error?.error ?? 'Ocurrió un error al cambiar el estado.';
          },
        });
    });
  }

  volver(): void {
    this.router.navigate(['/admin/empresas']);
  }
}