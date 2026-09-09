import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { AlertasService } from '../../../core/services/alertas.service';
import { EmpresaAdminService } from '../../../core/services/empresa-admin.service';
import { AdminAuthService } from '../../../core/services/admin-auth.service';
import { ConfirmActionDialog } from '../../../core/components/confirm-action-dialog/confirm-action-dialog.component';
import { Alerta, Empresa } from '../../../core/models/models';

export const ETIQUETAS_SEVERIDAD: Record<string, string> = {
  CRITICAL: 'Crítica',
  WARNING: 'Advertencia',
  INFO: 'Informativa',
};

export const CLASES_SEVERIDAD: Record<string, string> = {
  CRITICAL: 'tag--error',
  WARNING: 'tag--warning',
  INFO: 'tag--info',
};

export const ETIQUETAS_ESTADO: Record<string, string> = {
  OPEN: 'Abierta',
  READ: 'Leída',
  RESOLVED: 'Resuelta',
};

@Component({
  selector: 'app-alertas',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatDialogModule,
  ],
  templateUrl: './alertas.component.html',
  styleUrl: './alertas.component.scss',
})
export class AlertasComponent implements OnInit {
  alertas: Alerta[] = [];
  cargando = true;
  error = '';
  totalElements = 0;
  totalPages = 0;
  pagina = 0;

  filtroSeverity: string = '';
  filtroStatus: string = '';
  filtroTipo = '';
  filtroEmpresaId: number | null = null;

  columnas = ['severidad', 'titulo', 'empresa', 'tipo', 'creada', 'estado', 'acciones'];

  empresas: Empresa[] = [];

  private readonly alertasService = inject(AlertasService);
  private readonly empresaService = inject(EmpresaAdminService);
  private readonly dialog = inject(MatDialog);
  readonly auth = inject(AdminAuthService);

  ngOnInit(): void {
    this.cargar();
    this.empresaService.listar({ page: 0, limit: 100 }).subscribe({
      next: (pagina) => (this.empresas = pagina.content),
    });
  }

  cargar(): void {
    this.cargando = true;
    this.error = '';
    this.alertasService
      .listar({
        page: this.pagina,
        limit: 20,
        severity: (this.filtroSeverity || undefined) as never,
        status: (this.filtroStatus || undefined) as never,
        tipo: this.filtroTipo || undefined,
        companyId: this.filtroEmpresaId ?? undefined,
      })
      .subscribe({
        next: (resultado) => {
          this.alertas = resultado.content;
          this.totalElements = resultado.totalElements;
          this.totalPages = resultado.totalPages;
          this.cargando = false;
        },
        error: (err: unknown) => {
          this.cargando = false;
          this.error =
            (err as { error?: { error?: string } })?.error?.error ??
            'Ocurrió un error al cargar las alertas.';
        },
      });
  }

  aplicarFiltros(): void {
    this.pagina = 0;
    this.cargar();
  }

  limpiarFiltros(): void {
    this.filtroSeverity = '';
    this.filtroStatus = '';
    this.filtroTipo = '';
    this.filtroEmpresaId = null;
    this.aplicarFiltros();
  }

  paginaAnterior(): void {
    if (this.pagina > 0) {
      this.pagina--;
      this.cargar();
    }
  }

  paginaSiguiente(): void {
    if (this.pagina < this.totalPages - 1) {
      this.pagina++;
      this.cargar();
    }
  }

  puedeEditar(): boolean {
    return this.auth.tienePermiso('ALERTAS_EDITAR');
  }

  etiquetaSeveridad(severity: string): string {
    return ETIQUETAS_SEVERIDAD[severity] ?? severity;
  }

  claseSeveridad(severity: string): string {
    return CLASES_SEVERIDAD[severity] ?? 'tag--muted';
  }

  etiquetaEstado(estado: string): string {
    return ETIQUETAS_ESTADO[estado] ?? estado;
  }

  marcarLeida(alerta: Alerta): void {
    this.error = '';
    this.alertasService.marcarLeida(alerta.id).subscribe({
      next: () => this.cargar(),
      error: (err: unknown) => {
        this.error =
          (err as { error?: { error?: string } })?.error?.error ??
          'Ocurrió un error al marcar la alerta como leída.';
      },
    });
  }

  resolver(alerta: Alerta): void {
    this.error = '';
    this.dialog
      .open(ConfirmActionDialog, {
        width: '460px',
        data: {
          titulo: 'Resolver alerta',
          entidad: alerta.title,
          accion: 'Marcar la alerta como resuelta y dejarla de mostrar como pendiente',
        },
      })
      .afterClosed()
      .subscribe((motivo?: string) => {
        if (!motivo) return;
        this.alertasService.resolver(alerta.id, motivo).subscribe({
          next: () => this.cargar(),
          error: (err: unknown) => {
            this.error =
              (err as { error?: { error?: string } })?.error?.error ??
              'Ocurrió un error al resolver la alerta.';
          },
        });
      });
  }
}