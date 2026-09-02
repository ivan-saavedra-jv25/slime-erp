import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { FormaPagoService, FormaPagoRequest } from '../../core/services/forma-pago.service';
import { AuthService } from '../../core/services/auth.service';
import { CategoriaFormaPago, FormaPago } from '../../core/models/models';

const ETIQUETAS: Record<CategoriaFormaPago, string> = {
  GRATIS: 'Gratis',
  CREDITO: 'Crédito',
  CONTADO: 'Contado',
};

const TAGS: Record<CategoriaFormaPago, string> = {
  GRATIS: 'tag--info',
  CREDITO: 'tag--warning',
  CONTADO: 'tag--success',
};

@Component({
  selector: 'app-formas-pago',
  standalone: true,
  imports: [CommonModule, FormsModule, MatTableModule, MatButtonModule, MatIconModule, MatCardModule],
  templateUrl: './formas-pago.component.html',
  styleUrl: './formas-pago.component.scss',
})
export class FormasPagoComponent implements OnInit {
  categorias: CategoriaFormaPago[] = ['CONTADO', 'CREDITO', 'GRATIS'];
  columnas = ['nombre', 'categoria', 'acciones'];
  formasPago: FormaPago[] = [];
  error = '';
  guardando = false;
  editandoId: number | null = null;

  nombre = '';
  categoria: CategoriaFormaPago = 'CONTADO';

  constructor(private formaPagoService: FormaPagoService, public auth: AuthService) {}

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.formaPagoService.listar().subscribe((data) => (this.formasPago = data));
  }

  etiquetaCategoria(categoria: CategoriaFormaPago): string {
    return ETIQUETAS[categoria];
  }

  tagCategoria(categoria: CategoriaFormaPago): string {
    return TAGS[categoria];
  }

  editar(formaPago: FormaPago): void {
    this.editandoId = formaPago.id;
    this.nombre = formaPago.nombre;
    this.categoria = formaPago.categoria;
  }

  cancelarEdicion(): void {
    this.editandoId = null;
    this.limpiarFormulario();
  }

  guardar(): void {
    if (!this.nombre.trim()) return;
    const request: FormaPagoRequest = { nombre: this.nombre.trim(), categoria: this.categoria };
    this.guardando = true;
    const obs = this.editandoId
      ? this.formaPagoService.actualizar(this.editandoId, request)
      : this.formaPagoService.crear(request);
    obs.subscribe({
      next: () => {
        this.error = '';
        this.guardando = false;
        this.editandoId = null;
        this.limpiarFormulario();
        this.cargar();
      },
      error: (err) => {
        this.guardando = false;
        this.error = err?.error?.error ?? 'Ocurrió un error. Intenta nuevamente.';
      },
    });
  }

  eliminar(formaPago: FormaPago): void {
    this.formaPagoService.eliminar(formaPago.id).subscribe({
      next: () => {
        this.error = '';
        if (this.editandoId === formaPago.id) this.cancelarEdicion();
        this.cargar();
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'Ocurrió un error. Intenta nuevamente.';
      },
    });
  }

  private limpiarFormulario(): void {
    this.nombre = '';
    this.categoria = 'CONTADO';
  }
}
