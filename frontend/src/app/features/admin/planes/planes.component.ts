import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { PlanService } from '../../../core/services/plan.service';
import { Plan, PlanRequest } from '../../../core/models/models';
import { ConfirmActionDialog } from '../../../core/components/confirm-action-dialog/confirm-action-dialog.component';
import { cerrarCargando, mostrarCargando } from '../../../core/utils/swal-loading';

export const MODULOS_DISPONIBLES = [
  'ventas',
  'compras',
  'inventario',
  'contabilidad',
  'dte',
] as const;

const ETIQUETAS_MODULO: Record<string, string> = {
  ventas: 'Ventas',
  compras: 'Compras',
  inventario: 'Inventario',
  contabilidad: 'Contabilidad',
  dte: 'DTE',
};

@Component({
  selector: 'app-planes',
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
  templateUrl: './planes.component.html',
  styleUrl: './planes.component.scss',
})
export class PlanesComponent implements OnInit {
  planes: Plan[] = [];
  cargando = true;
  error = '';
  guardando = false;

  columnas = ['nombre', 'precios', 'limites', 'modulos', 'estado', 'acciones'];

  modulosDisponibles = MODULOS_DISPONIBLES;

  editandoId: number | null = null;
  nombre = '';
  descripcion = '';
  precioMensual: number | null = null;
  precioAnual: number | null = null;
  maxUsuarios: number | null = null;
  maxDocumentos: number | null = null;
  modulos: string[] = [];
  caracteristicas = '';
  estado: 'ACTIVE' | 'INACTIVE' = 'ACTIVE';

  private readonly planService = inject(PlanService);
  private readonly dialog = inject(MatDialog);

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cargando = true;
    this.error = '';
    this.planService.listar().subscribe({
      next: (planes) => {
        this.planes = planes;
        this.cargando = false;
      },
      error: (err: unknown) => {
        this.cargando = false;
        this.error = (err as { error?: { error?: string } })?.error?.error ?? 'Ocurrió un error al cargar los planes.';
      },
    });
  }

  etiquetaModulo(modulo: string): string {
    return ETIQUETAS_MODULO[modulo] ?? modulo;
  }

  toggleModulo(modulo: string, marcado: boolean): void {
    this.modulos = marcado
      ? [...this.modulos, modulo]
      : this.modulos.filter((m) => m !== modulo);
  }

  onModuloToggle(modulo: string, event: Event): void {
    this.toggleModulo(modulo, (event.target as HTMLInputElement).checked);
  }

  editar(plan: Plan): void {
    this.editandoId = plan.id;
    this.nombre = plan.nombre;
    this.descripcion = plan.descripcion ?? '';
    this.precioMensual = plan.precioMensual;
    this.precioAnual = plan.precioAnual;
    this.maxUsuarios = plan.maxUsuarios;
    this.maxDocumentos = plan.maxDocumentos;
    this.modulos = [...plan.modulos];
    this.caracteristicas = (plan.caracteristicas ?? []).join('\n');
    this.estado = plan.estado;
  }

  cancelarEdicion(): void {
    this.editandoId = null;
    this.limpiar();
  }

  private limpiar(): void {
    this.nombre = '';
    this.descripcion = '';
    this.precioMensual = null;
    this.precioAnual = null;
    this.maxUsuarios = null;
    this.maxDocumentos = null;
    this.modulos = [];
    this.caracteristicas = '';
    this.estado = 'ACTIVE';
  }

  formularioCompleto(): boolean {
    return !!(this.nombre.trim() && this.precioMensual !== null && this.maxUsuarios !== null
      && this.maxDocumentos !== null && this.precioMensual >= 0 && this.maxUsuarios >= 1
      && this.maxDocumentos >= 1);
  }

  guardar(): void {
    if (!this.formularioCompleto() || this.precioMensual === null || this.maxUsuarios === null || this.maxDocumentos === null) {
      return;
    }
    const request: PlanRequest = {
      nombre: this.nombre.trim(),
      descripcion: this.descripcion.trim() || undefined,
      precioMensual: this.precioMensual,
      precioAnual: this.precioAnual === null ? undefined : this.precioAnual,
      maxUsuarios: this.maxUsuarios,
      maxDocumentos: this.maxDocumentos,
      modulos: this.modulos,
      caracteristicas: this.caracteristicas.split('\n').map((c) => c.trim()).filter(Boolean),
      ...(this.editandoId ? { estado: this.estado } : {}),
    };

    this.guardando = true;
    this.error = '';
    mostrarCargando(this.editandoId ? 'Guardando cambios' : 'Creando plan');
    const obs = this.editandoId
      ? this.planService.actualizar(this.editandoId, request)
      : this.planService.crear(request);
    obs.subscribe({
      next: () => {
        cerrarCargando();
        this.guardando = false;
        this.editandoId = null;
        this.limpiar();
        this.cargar();
      },
      error: (err: unknown) => {
        cerrarCargando();
        this.guardando = false;
        this.error = (err as { error?: { error?: string } })?.error?.error ?? 'Ocurrió un error. Intenta nuevamente.';
      },
    });
  }

  cambiarEstado(plan: Plan): void {
    const estadoNuevo: 'INACTIVE' = 'INACTIVE';
    const titulo = 'Desactivar plan';
    const accion = 'Cambiar el estado a "Inactivo"';
    this.error = '';
    this.dialog.open(ConfirmActionDialog, {
      width: '460px',
      data: { titulo, entidad: plan.nombre, accion },
    }).afterClosed().subscribe((motivo?: string) => {
      if (!motivo) return;
      this.planService.cambiarEstado(plan.id, estadoNuevo).subscribe({
        next: () => this.cargar(),
        error: (err: unknown) => {
          this.error = (err as { error?: { error?: string } })?.error?.error ?? 'Ocurrió un error. Intenta nuevamente.';
        },
      });
    });
  }

  reactivar(plan: Plan): void {
    this.error = '';
    this.planService.cambiarEstado(plan.id, 'ACTIVE').subscribe({
      next: () => this.cargar(),
      error: (err: unknown) => {
        this.error = (err as { error?: { error?: string } })?.error?.error ?? 'Ocurrió un error. Intenta nuevamente.';
      },
    });
  }
}