import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { SuscripcionService } from '../../../core/services/suscripcion.service';
import { PagosService } from '../../../core/services/pagos.service';
import { AdminAuthService } from '../../../core/services/admin-auth.service';
import { Suscripcion, Vencimientos } from '../../../core/models/models';
import { ConfirmActionDialog } from '../../../core/components/confirm-action-dialog/confirm-action-dialog.component';
import {
  ETIQUETAS_ESTADO,
  CLASES_ESTADO,
} from '../suscripciones/suscripciones.component';
import {
  ExtenderSuscripcionDialog,
} from '../suscripciones/dialogs/extender-suscripcion-dialog.component';
import {
  RegistrarPagoDialog,
} from '../pagos/dialogs/registrar-pago-dialog.component';

interface Ventana {
  titulo: string;
  descripcion: string;
  clase: string;
  listas: (v: Vencimientos) => Suscripcion[];
}

const ESTADOS_ACTIVOS = ['TRIAL', 'ACTIVE', 'PAST_DUE'];

@Component({
  selector: 'app-vencimientos',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatDialogModule,
  ],
  templateUrl: './vencimientos.component.html',
  styleUrl: './vencimientos.component.scss',
})
export class VencimientosComponent implements OnInit {
  vencimientos: Vencimientos | null = null;
  cargando = true;
  error = '';

  columnas = ['empresa', 'plan', 'estado', 'vencimiento', 'acciones'];

  readonly ventanas: Ventana[] = [
    { titulo: 'Vencen hoy', descripcion: 'Suscripciones cuyo vencimiento ocurre este día.', clase: 'tag--error', listas: (v) => v.vencenHoy },
    { titulo: 'Vencen en 3 días', descripcion: 'Próximas a vencer dentro de 3 días.', clase: 'tag--warning', listas: (v) => v.vencenEn3Dias },
    { titulo: 'Vencen en 7 días', descripcion: 'Próximas a vencer dentro de 7 días.', clase: 'tag--warning', listas: (v) => v.vencenEn7Dias },
    { titulo: 'Vencen en 30 días', descripcion: 'Próximas a vencer dentro de 30 días.', clase: 'tag--info', listas: (v) => v.vencenEn30Dias },
    { titulo: 'Vencidas', descripcion: 'Suscripciones con fecha de vencimiento pasada.', clase: 'tag--error', listas: (v) => v.vencidas },
  ];

  private readonly suscripcionService = inject(SuscripcionService);
  private readonly pagosService = inject(PagosService);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  readonly auth = inject(AdminAuthService);

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cargando = true;
    this.error = '';
    this.suscripcionService.expiring().subscribe({
      next: (ventanas) => {
        this.vencimientos = ventanas;
        this.cargando = false;
      },
      error: (err: unknown) => {
        this.cargando = false;
        this.error =
          (err as { error?: { error?: string } })?.error?.error ??
          'Ocurrió un error al cargar los vencimientos.';
      },
    });
  }

  listas(index: number): Suscripcion[] {
    return this.vencimientos ? this.ventanas[index].listas(this.vencimientos) : [];
  }

  etiquetaEstado(estado: string): string {
    return ETIQUETAS_ESTADO[estado] ?? estado;
  }

  claseEstado(estado: string): string {
    return CLASES_ESTADO[estado] ?? 'tag--muted';
  }

  esActiva(estado: string): boolean {
    return ESTADOS_ACTIVOS.includes(estado);
  }

  formatearFecha(fecha: string): string {
    return new Date(fecha).toLocaleDateString('es-CL');
  }

  verEmpresa(s: Suscripcion): void {
    this.router.navigate(['/admin/empresas', s.companyId]);
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

  registrarPago(s: Suscripcion): void {
    this.error = '';
    this.dialog
      .open(RegistrarPagoDialog, {
        width: '520px',
        data: { companyId: s.companyId, suscripcionId: s.id },
      })
      .afterClosed()
      .subscribe((request?: unknown) => {
        if (!request) return;
        this.pagosService.registrar(request as never).subscribe({
          next: () => this.cargar(),
          error: (err: unknown) => {
            this.error =
              (err as { error?: { error?: string } })?.error?.error ??
              'Ocurrió un error al registrar el pago.';
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