import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { SuscripcionService } from '../../../core/services/suscripcion.service';
import { PlanService } from '../../../core/services/plan.service';
import { EmpresaAdminService } from '../../../core/services/empresa-admin.service';
import { Empresa, EstadoSuscripcion, Plan, Suscripcion } from '../../../core/models/models';
import { ConfirmActionDialog } from '../../../core/components/confirm-action-dialog/confirm-action-dialog.component';
import {
  CambiarPlanDialog,
} from './dialogs/cambiar-plan-dialog.component';
import {
  ExtenderSuscripcionDialog,
} from './dialogs/extender-suscripcion-dialog.component';
import {
  NuevaSuscripcionDialog,
} from './dialogs/nueva-suscripcion-dialog.component';

const ESTADOS_ACTIVOS = ['TRIAL', 'ACTIVE', 'PAST_DUE'];

export const ETIQUETAS_ESTADO: Record<string, string> = {
  TRIAL: 'Prueba',
  ACTIVE: 'Activa',
  PAST_DUE: 'Vencida',
  SUSPENDED: 'Suspendida',
  CANCELLED: 'Cancelada',
  EXPIRED: 'Expirada',
};

export const CLASES_ESTADO: Record<string, string> = {
  TRIAL: 'tag--info',
  ACTIVE: 'tag--success',
  PAST_DUE: 'tag--warning',
  SUSPENDED: 'tag--error',
  CANCELLED: 'tag--muted',
  EXPIRED: 'tag--muted',
};

@Component({
  selector: 'app-suscripciones',
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
  templateUrl: './suscripciones.component.html',
  styleUrl: './suscripciones.component.scss',
})
export class SuscripcionesComponent implements OnInit {
  suscripciones: Suscripcion[] = [];
  cargando = true;
  error = '';
  totalElements = 0;
  totalPages = 0;
  pagina = 0;

  planes: Plan[] = [];
  empresas: Empresa[] = [];

  filtroEstado: EstadoSuscripcion | '' = '';
  filtroPlanId: number | null = null;
  filtroEmpresaId: number | null = null;
  filtroProximasAVencer: number | null = null;

  columnas = ['empresa', 'plan', 'estado', 'fechas', 'ciclo', 'precio', 'gracia', 'acciones'];

  private readonly suscripcionService = inject(SuscripcionService);
  private readonly planService = inject(PlanService);
  private readonly empresaService = inject(EmpresaAdminService);
  private readonly dialog = inject(MatDialog);

  ngOnInit(): void {
    this.cargar();
    this.cargarCatalogos();
  }

  cargar(): void {
    this.cargando = true;
    this.error = '';
    this.suscripcionService
      .listar({
        page: this.pagina,
        limit: 15,
        estado: this.filtroEstado || undefined,
        planId: this.filtroPlanId ?? undefined,
        empresaId: this.filtroEmpresaId ?? undefined,
        proximasAVencerDias: this.filtroProximasAVencer ?? undefined,
      })
      .subscribe({
        next: (resultado) => {
          this.suscripciones = resultado.content;
          this.totalElements = resultado.totalElements;
          this.totalPages = resultado.totalPages;
          this.cargando = false;
        },
        error: (err: unknown) => {
          this.cargando = false;
          this.error =
            (err as { error?: { error?: string } })?.error?.error ??
            'Ocurrió un error al cargar las suscripciones.';
        },
      });
  }

  private cargarCatalogos(): void {
    this.planService.listar().subscribe({
      next: (planes) => (this.planes = planes),
    });
    this.empresaService.listar({ page: 0, limit: 100 }).subscribe({
      next: (pagina) => (this.empresas = pagina.content),
    });
  }

  etiquetaEstado(estado: string): string {
    return ETIQUETAS_ESTADO[estado] ?? estado;
  }

  claseEstado(estado: string): string {
    return CLASES_ESTADO[estado] ?? 'tag--muted';
  }

  etiquetaCiclo(ciclo: string): string {
    return ciclo === 'ANNUAL' ? 'Anual' : 'Mensual';
  }

  esActiva(estado: string): boolean {
    return ESTADOS_ACTIVOS.includes(estado);
  }

  aplicarFiltros(): void {
    this.pagina = 0;
    this.cargar();
  }

  limpiarFiltros(): void {
    this.filtroEstado = '';
    this.filtroPlanId = null;
    this.filtroEmpresaId = null;
    this.filtroProximasAVencer = null;
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

  nueva(): void {
    this.error = '';
    this.dialog
      .open(NuevaSuscripcionDialog, { width: '620px' })
      .afterClosed()
      .subscribe((request?: unknown) => {
        if (!request) return;
        this.suscripcionService.crear(request as never).subscribe({
          next: () => this.cargar(),
          error: (err: unknown) => {
            this.error =
              (err as { error?: { error?: string } })?.error?.error ??
              'Ocurrió un error al crear la suscripción.';
          },
        });
      });
  }

  extender(s: Suscripcion): void {
    this.error = '';
    this.dialog
      .open(ExtenderSuscripcionDialog, { width: '480px', data: s.fechaVencimiento })
      .afterClosed()
      .subscribe((request?: unknown) => {
        if (!request) return;
        this.suscripcionService.extender(s.id, request as never).subscribe({
          next: () => this.cargar(),
          error: (err: unknown) => {
            this.error =
              (err as { error?: { error?: string } })?.error?.error ??
              'Ocurrió un error al extender la suscripción.';
          },
        });
      });
  }

  cambiarPlan(s: Suscripcion): void {
    this.error = '';
    this.dialog
      .open(CambiarPlanDialog, { width: '480px', data: { planes: this.planes, planId: s.planId } })
      .afterClosed()
      .subscribe((request?: unknown) => {
        if (!request) return;
        this.suscripcionService.cambiarPlan(s.id, request as never).subscribe({
          next: () => this.cargar(),
          error: (err: unknown) => {
            this.error =
              (err as { error?: { error?: string } })?.error?.error ??
              'Ocurrió un error al cambiar el plan.';
          },
        });
      });
  }

  suspender(s: Suscripcion): void {
    this.error = '';
    this.dialog
      .open(ConfirmActionDialog, {
        width: '460px',
        data: {
          titulo: 'Suspender suscripción',
          entidad: `${s.empresaNombre ?? 'Empresa'} · ${s.planNombre}`,
          accion: 'Suspender el acceso de la empresa a la plataforma',
        },
      })
      .afterClosed()
      .subscribe((motivo?: string) => {
        if (!motivo) return;
        this.suscripcionService.suspender(s.id, motivo).subscribe({
          next: () => this.cargar(),
          error: (err: unknown) => {
            this.error =
              (err as { error?: { error?: string } })?.error?.error ??
              'Ocurrió un error al suspender la suscripción.';
          },
        });
      });
  }

  reactivar(s: Suscripcion): void {
    this.error = '';
    this.dialog
      .open(ConfirmActionDialog, {
        width: '460px',
        data: {
          titulo: 'Reactivar suscripción',
          entidad: `${s.empresaNombre ?? 'Empresa'} · ${s.planNombre}`,
          accion: 'Reactivar el acceso de la empresa a la plataforma',
        },
      })
      .afterClosed()
      .subscribe((motivo?: string) => {
        if (!motivo) return;
        this.suscripcionService.reactivar(s.id, motivo).subscribe({
          next: () => this.cargar(),
          error: (err: unknown) => {
            this.error =
              (err as { error?: { error?: string } })?.error?.error ??
              'Ocurrió un error al reactivar la suscripción.';
          },
        });
      });
  }
}