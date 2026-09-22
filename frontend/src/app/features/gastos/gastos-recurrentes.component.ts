import { Component, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatTooltipModule } from '@angular/material/tooltip';
import { CategoriaGastoService } from '../../core/services/categoria-gasto.service';
import { GastoRecurrenteService, GastoRecurrenteRequest } from '../../core/services/gasto-recurrente.service';
import { AuthService } from '../../core/services/auth.service';
import {
  CategoriaGasto,
  ETIQUETAS_FRECUENCIA_GASTO,
  FrecuenciaGastoRecurrente,
  GastoRecurrente,
} from '../../core/models/models';
import { MonedaPipe } from '../../core/pipes/moneda.pipe';
import { cerrarCargando, mostrarCargando } from '../../core/utils/swal-loading';

@Component({
  selector: 'app-gastos-recurrentes',
  standalone: true,
  imports: [CommonModule, FormsModule, MatTableModule, MatButtonModule, MatIconModule, MatCardModule, MatTooltipModule, MonedaPipe],
  templateUrl: './gastos-recurrentes.component.html',
  styleUrl: './gastos-recurrentes.component.scss',
})
export class GastosRecurrentesComponent implements OnInit {
  @ViewChild('f') formulario?: NgForm;

  columnas = ['categoria', 'descripcion', 'monto', 'frecuencia', 'diaMes', 'vigencia', 'acciones'];

  categorias: CategoriaGasto[] = [];
  recurrentes: GastoRecurrente[] = [];

  categoriaGastoId: number | null = null;
  monto: number | null = null;
  descripcion = '';
  frecuencia: FrecuenciaGastoRecurrente = 'MENSUAL';
  diaMes: number | null = null;
  fechaInicio = '';
  fechaFin = '';
  editandoId: number | null = null;
  guardando = false;
  error = '';

  readonly etiquetaFrecuencia = ETIQUETAS_FRECUENCIA_GASTO;
  readonly frecuencias = ['DIARIO', 'SEMANAL', 'MENSUAL', 'ANUAL'] as const;

  constructor(
    private categoriaGastoService: CategoriaGastoService,
    private gastoRecurrenteService: GastoRecurrenteService,
    public auth: AuthService
  ) {}

  ngOnInit(): void {
    this.categoriaGastoService.listar().subscribe((data) => (this.categorias = data));
    this.cargar();
  }

  cargar(): void {
    this.gastoRecurrenteService.listar().subscribe((data) => (this.recurrentes = data));
  }

  nombreCategoriaDe(id: number): string {
    return this.categorias.find((c) => c.id === id)?.nombre ?? 'Sin categoría';
  }

  nombreFrecuenciaDe(r: GastoRecurrente): string {
    return ETIQUETAS_FRECUENCIA_GASTO[r.frecuencia];
  }

  necesitaDiaMes(): boolean {
    return this.frecuencia === 'MENSUAL';
  }

  seleccionarFrecuencia(frecuencia: FrecuenciaGastoRecurrente): void {
    this.frecuencia = frecuencia;
    if (frecuencia !== 'MENSUAL') this.diaMes = null;
  }

  editar(recurrente: GastoRecurrente): void {
    this.editandoId = recurrente.id;
    this.categoriaGastoId = recurrente.categoriaGastoId;
    this.monto = recurrente.monto;
    this.descripcion = recurrente.descripcion;
    this.frecuencia = recurrente.frecuencia;
    this.diaMes = recurrente.diaMes;
    this.fechaInicio = recurrente.fechaInicio;
    this.fechaFin = recurrente.fechaFin ?? '';
  }

  cancelarEdicion(): void {
    this.editandoId = null;
    this.categoriaGastoId = null;
    this.monto = null;
    this.descripcion = '';
    this.frecuencia = 'MENSUAL';
    this.diaMes = null;
    this.fechaInicio = '';
    this.fechaFin = '';
    this.formulario?.resetForm();
  }

  guardar(): void {
    if (!this.categoriaGastoId || !this.monto || !this.descripcion.trim() || !this.fechaInicio) return;
    if (this.necesitaDiaMes() && !this.diaMes) return;
    const request: GastoRecurrenteRequest = {
      categoriaGastoId: this.categoriaGastoId,
      monto: this.monto,
      descripcion: this.descripcion.trim(),
      frecuencia: this.frecuencia,
      diaMes: this.necesitaDiaMes() ? this.diaMes : null,
      fechaInicio: this.fechaInicio,
      fechaFin: this.fechaFin || null,
    };
    this.guardando = true;
    mostrarCargando(this.editandoId ? 'Guardando cambios' : 'Creando gasto recurrente');
    const obs = this.editandoId
      ? this.gastoRecurrenteService.actualizar(this.editandoId, request)
      : this.gastoRecurrenteService.crear(request);
    obs.subscribe({
      next: () => {
        cerrarCargando();
        this.error = '';
        this.guardando = false;
        this.cancelarEdicion();
        this.cargar();
      },
      error: (err) => {
        cerrarCargando();
        this.guardando = false;
        this.error = err?.error?.error ?? 'Ocurrió un error. Intenta nuevamente.';
      },
    });
  }

  eliminar(recurrente: GastoRecurrente): void {
    this.gastoRecurrenteService.eliminar(recurrente.id).subscribe({
      next: () => {
        this.error = '';
        if (this.editandoId === recurrente.id) this.cancelarEdicion();
        this.cargar();
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'Ocurrió un error. Intenta nuevamente.';
      },
    });
  }
}
