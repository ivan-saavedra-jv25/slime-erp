import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
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
  imports: [
    CommonModule,
    FormsModule,
    MatTableModule,
    MatPaginatorModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
  ],
  templateUrl: './categorias.component.html',
  styleUrl: './categorias.component.scss',
})
export class CategoriasComponent implements OnInit, OnDestroy {
  columnasCategorias = ['nombre', 'acciones'];
  columnasSubcategorias = ['nombre', 'acciones'];

  categorias: Categoria[] = [];
  totalCategorias = 0;
  filtroCategorias = '';
  paginaCategorias = 0;
  tamanoCategorias = 10;
  readonly opcionesTamano = [10, 25, 50];

  subcategorias: Subcategoria[] = [];
  totalSubcategorias = 0;
  filtroSubcategorias = '';
  paginaSubcategorias = 0;
  tamanoSubcategorias = 10;

  categoriaSeleccionada: Categoria | null = null;
  error = '';

  nombreCategoria = '';
  editandoCategoriaId: number | null = null;
  guardandoCategoria = false;

  nombreSubcategoria = '';
  editandoSubcategoriaId: number | null = null;
  guardandoSubcategoria = false;

  private readonly busquedaCategorias$ = new Subject<string>();
  private readonly busquedaSubcategorias$ = new Subject<string>();

  constructor(
    private categoriaService: CategoriaService,
    private subcategoriaService: SubcategoriaService,
    public auth: AuthService
  ) {}

  ngOnInit(): void {
    this.busquedaCategorias$.pipe(debounceTime(300), distinctUntilChanged()).subscribe(() => {
      this.paginaCategorias = 0;
      this.cargarCategorias();
    });
    this.busquedaSubcategorias$.pipe(debounceTime(300), distinctUntilChanged()).subscribe(() => {
      this.paginaSubcategorias = 0;
      this.cargarSubcategorias();
    });
    this.cargarCategorias();
  }

  ngOnDestroy(): void {
    this.busquedaCategorias$.complete();
    this.busquedaSubcategorias$.complete();
  }

  cargarCategorias(): void {
    this.categoriaService
      .listarPagina(this.filtroCategorias, this.paginaCategorias, this.tamanoCategorias)
      .subscribe((resp) => {
        if (!resp.contenido.length && this.paginaCategorias > 0) {
          this.paginaCategorias = Math.max(0, this.paginaCategorias - 1);
          this.cargarCategorias();
          return;
        }
        this.categorias = resp.contenido;
        this.totalCategorias = resp.total;
      });
  }

  onFiltroCategoriasChange(): void {
    this.busquedaCategorias$.next(this.filtroCategorias);
  }

  onPageCategoriasChange(event: PageEvent): void {
    this.paginaCategorias = event.pageIndex;
    this.tamanoCategorias = event.pageSize;
    this.cargarCategorias();
  }

  cargarSubcategorias(): void {
    if (!this.categoriaSeleccionada) return;
    this.subcategoriaService
      .listarPagina(
        this.categoriaSeleccionada.id,
        this.filtroSubcategorias,
        this.paginaSubcategorias,
        this.tamanoSubcategorias
      )
      .subscribe((resp) => {
        if (!resp.contenido.length && this.paginaSubcategorias > 0) {
          this.paginaSubcategorias = Math.max(0, this.paginaSubcategorias - 1);
          this.cargarSubcategorias();
          return;
        }
        this.subcategorias = resp.contenido;
        this.totalSubcategorias = resp.total;
      });
  }

  onFiltroSubcategoriasChange(): void {
    this.busquedaSubcategorias$.next(this.filtroSubcategorias);
  }

  onPageSubcategoriasChange(event: PageEvent): void {
    this.paginaSubcategorias = event.pageIndex;
    this.tamanoSubcategorias = event.pageSize;
    this.cargarSubcategorias();
  }

  seleccionar(categoria: Categoria): void {
    this.categoriaSeleccionada = categoria;
    this.cancelarEdicionSubcategoria();
    this.filtroSubcategorias = '';
    this.paginaSubcategorias = 0;
    this.cargarSubcategorias();
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
          this.totalSubcategorias = 0;
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
        this.cargarSubcategorias();
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
        this.cargarSubcategorias();
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'Ocurrió un error. Intenta nuevamente.';
      },
    });
  }
}
