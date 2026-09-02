import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { CategoriaService, CategoriaRequest } from '../../core/services/categoria.service';
import { SubcategoriaService, SubcategoriaRequest } from '../../core/services/subcategoria.service';
import { AuthService } from '../../core/services/auth.service';
import { Categoria, Subcategoria } from '../../core/models/models';

@Component({
  selector: 'app-categorias',
  standalone: true,
  imports: [CommonModule, FormsModule, MatTableModule, MatButtonModule, MatIconModule, MatCardModule],
  templateUrl: './categorias.component.html',
  styleUrl: './categorias.component.scss',
})
export class CategoriasComponent implements OnInit {
  columnasCategorias = ['nombre', 'acciones'];
  columnasSubcategorias = ['nombre', 'acciones'];

  categorias: Categoria[] = [];
  subcategorias: Subcategoria[] = [];
  categoriaSeleccionada: Categoria | null = null;
  error = '';

  nombreCategoria = '';
  editandoCategoriaId: number | null = null;
  guardandoCategoria = false;

  nombreSubcategoria = '';
  editandoSubcategoriaId: number | null = null;
  guardandoSubcategoria = false;

  constructor(
    private categoriaService: CategoriaService,
    private subcategoriaService: SubcategoriaService,
    public auth: AuthService
  ) {}

  ngOnInit(): void {
    this.cargarCategorias();
  }

  cargarCategorias(): void {
    this.categoriaService.listar().subscribe((data) => (this.categorias = data));
  }

  seleccionar(categoria: Categoria): void {
    this.categoriaSeleccionada = categoria;
    this.cancelarEdicionSubcategoria();
    this.subcategoriaService.listar(categoria.id).subscribe((data) => (this.subcategorias = data));
  }

  editarCategoria(categoria: Categoria): void {
    this.editandoCategoriaId = categoria.id;
    this.nombreCategoria = categoria.nombre;
  }

  cancelarEdicionCategoria(): void {
    this.editandoCategoriaId = null;
    this.nombreCategoria = '';
  }

  guardarCategoria(): void {
    if (!this.nombreCategoria.trim()) return;
    const request: CategoriaRequest = { nombre: this.nombreCategoria.trim() };
    this.guardandoCategoria = true;
    const obs = this.editandoCategoriaId
      ? this.categoriaService.actualizar(this.editandoCategoriaId, request)
      : this.categoriaService.crear(request);
    obs.subscribe({
      next: () => {
        this.error = '';
        this.guardandoCategoria = false;
        this.editandoCategoriaId = null;
        this.nombreCategoria = '';
        this.cargarCategorias();
      },
      error: (err) => {
        this.guardandoCategoria = false;
        this.error = err?.error?.error ?? 'Ocurrió un error. Intenta nuevamente.';
      },
    });
  }

  eliminarCategoria(categoria: Categoria): void {
    this.categoriaService.eliminar(categoria.id).subscribe({
      next: () => {
        this.error = '';
        if (this.editandoCategoriaId === categoria.id) this.cancelarEdicionCategoria();
        if (this.categoriaSeleccionada?.id === categoria.id) {
          this.categoriaSeleccionada = null;
          this.subcategorias = [];
        }
        this.cargarCategorias();
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'Ocurrió un error. Intenta nuevamente.';
      },
    });
  }

  editarSubcategoria(subcategoria: Subcategoria): void {
    this.editandoSubcategoriaId = subcategoria.id;
    this.nombreSubcategoria = subcategoria.nombre;
  }

  cancelarEdicionSubcategoria(): void {
    this.editandoSubcategoriaId = null;
    this.nombreSubcategoria = '';
  }

  guardarSubcategoria(): void {
    if (!this.categoriaSeleccionada || !this.nombreSubcategoria.trim()) return;
    const request: SubcategoriaRequest = {
      categoriaId: this.categoriaSeleccionada.id,
      nombre: this.nombreSubcategoria.trim(),
    };
    this.guardandoSubcategoria = true;
    const obs = this.editandoSubcategoriaId
      ? this.subcategoriaService.actualizar(this.editandoSubcategoriaId, request)
      : this.subcategoriaService.crear(request);
    obs.subscribe({
      next: () => {
        this.error = '';
        this.guardandoSubcategoria = false;
        this.editandoSubcategoriaId = null;
        this.nombreSubcategoria = '';
        this.seleccionar(this.categoriaSeleccionada!);
      },
      error: (err) => {
        this.guardandoSubcategoria = false;
        this.error = err?.error?.error ?? 'Ocurrió un error. Intenta nuevamente.';
      },
    });
  }

  eliminarSubcategoria(subcategoria: Subcategoria): void {
    this.subcategoriaService.eliminar(subcategoria.id).subscribe({
      next: () => {
        this.error = '';
        if (this.editandoSubcategoriaId === subcategoria.id) this.cancelarEdicionSubcategoria();
        if (this.categoriaSeleccionada) this.seleccionar(this.categoriaSeleccionada);
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'Ocurrió un error. Intenta nuevamente.';
      },
    });
  }
}
