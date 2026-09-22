import { Component, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { CategoriaGastoService, CategoriaGastoRequest } from '../../core/services/categoria-gasto.service';
import { AuthService } from '../../core/services/auth.service';
import { CategoriaGasto } from '../../core/models/models';
import { cerrarCargando, mostrarCargando } from '../../core/utils/swal-loading';

@Component({
  selector: 'app-categorias-gasto',
  standalone: true,
  imports: [CommonModule, FormsModule, MatTableModule, MatButtonModule, MatIconModule, MatCardModule],
  templateUrl: './categorias-gasto.component.html',
  styleUrl: './categorias-gasto.component.scss',
})
export class CategoriasGastoComponent implements OnInit {
  @ViewChild('fc') formulario?: NgForm;

  columnas = ['nombre', 'acciones'];

  categorias: CategoriaGasto[] = [];
  nombreCategoria = '';
  editandoId: number | null = null;
  guardando = false;
  error = '';

  constructor(
    private categoriaGastoService: CategoriaGastoService,
    public auth: AuthService
  ) {}

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.categoriaGastoService.listar().subscribe((data) => (this.categorias = data));
  }

  editar(categoria: CategoriaGasto): void {
    this.editandoId = categoria.id;
    this.nombreCategoria = categoria.nombre;
  }

  cancelarEdicion(): void {
    this.editandoId = null;
    this.formulario?.resetForm({ nombreCategoria: '' });
  }

  guardar(): void {
    if (!this.nombreCategoria.trim()) return;
    const request: CategoriaGastoRequest = { nombre: this.nombreCategoria.trim() };
    this.guardando = true;
    mostrarCargando(this.editandoId ? 'Guardando cambios' : 'Creando categoría');
    const obs = this.editandoId
      ? this.categoriaGastoService.actualizar(this.editandoId, request)
      : this.categoriaGastoService.crear(request);
    obs.subscribe({
      next: () => {
        cerrarCargando();
        this.error = '';
        this.guardando = false;
        this.editandoId = null;
        this.formulario?.resetForm({ nombreCategoria: '' });
        this.cargar();
      },
      error: (err) => {
        cerrarCargando();
        this.guardando = false;
        this.error = err?.error?.error ?? 'Ocurrió un error. Intenta nuevamente.';
      },
    });
  }

  eliminar(categoria: CategoriaGasto): void {
    this.categoriaGastoService.eliminar(categoria.id).subscribe({
      next: () => {
        this.error = '';
        if (this.editandoId === categoria.id) this.cancelarEdicion();
        this.cargar();
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'Ocurrió un error. Intenta nuevamente.';
      },
    });
  }
}