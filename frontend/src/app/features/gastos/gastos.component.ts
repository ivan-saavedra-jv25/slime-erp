import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatTooltipModule } from '@angular/material/tooltip';
import { CategoriaGastoService, CategoriaGastoRequest } from '../../core/services/categoria-gasto.service';
import { GastoService, GastoRequest } from '../../core/services/gasto.service';
import { AuthService } from '../../core/services/auth.service';
import { CategoriaGasto, Gasto } from '../../core/models/models';
import { MonedaPipe } from '../../core/pipes/moneda.pipe';
import { cerrarCargando, mostrarCargando } from '../../core/utils/swal-loading';

@Component({
  selector: 'app-gastos',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatTableModule,
    MatPaginatorModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatTooltipModule,
    MonedaPipe,
  ],
  templateUrl: './gastos.component.html',
  styleUrl: './gastos.component.scss',
})
export class GastosComponent implements OnInit, OnDestroy {
  @ViewChild('fc') formularioCategoria?: NgForm;
  @ViewChild('fg') formularioGasto?: NgForm;

  columnasCategorias = ['nombre', 'acciones'];
  columnasGastos = ['fecha', 'categoria', 'descripcion', 'monto', 'acciones'];

  categorias: CategoriaGasto[] = [];
  nombreCategoria = '';
  editandoCategoriaId: number | null = null;
  guardandoCategoria = false;

  gastos: Gasto[] = [];
  totalGastos = 0;
  paginaGastos = 0;
  tamanoGastos = 10;
  readonly opcionesTamano = [10, 25, 50];

  filtroCategoriaId: number | null = null;
  filtroFechaDesde = '';
  filtroFechaHasta = '';
  filtroTexto = '';

  categoriaGastoId: number | null = null;
  monto: number | null = null;
  descripcion = '';
  fecha = '';
  editandoGastoId: number | null = null;
  guardandoGasto = false;

  error = '';

  private readonly busquedaGastos$ = new Subject<string>();

  constructor(
    private categoriaGastoService: CategoriaGastoService,
    private gastoService: GastoService,
    public auth: AuthService
  ) {}

  ngOnInit(): void {
    this.busquedaGastos$.pipe(debounceTime(300), distinctUntilChanged()).subscribe(() => {
      this.paginaGastos = 0;
      this.cargarGastos();
    });
    this.cargarCategorias();
    this.cargarGastos();
  }

  ngOnDestroy(): void {
    this.busquedaGastos$.complete();
  }

  nombreCategoriaDe(id: number): string {
    return this.categorias.find((c) => c.id === id)?.nombre ?? 'Sin categoría';
  }

  // --- Categorías de gasto ---

  cargarCategorias(): void {
    this.categoriaGastoService.listar().subscribe((data) => (this.categorias = data));
  }

  editarCategoria(categoria: CategoriaGasto): void {
    this.editandoCategoriaId = categoria.id;
    this.nombreCategoria = categoria.nombre;
  }

  cancelarEdicionCategoria(): void {
    this.editandoCategoriaId = null;
    this.formularioCategoria?.resetForm({ nombreCategoria: '' });
  }

  guardarCategoria(): void {
    if (!this.nombreCategoria.trim()) return;
    const request: CategoriaGastoRequest = { nombre: this.nombreCategoria.trim() };
    this.guardandoCategoria = true;
    mostrarCargando(this.editandoCategoriaId ? 'Guardando cambios' : 'Creando categoría');
    const obs = this.editandoCategoriaId
      ? this.categoriaGastoService.actualizar(this.editandoCategoriaId, request)
      : this.categoriaGastoService.crear(request);
    obs.subscribe({
      next: () => {
        cerrarCargando();
        this.error = '';
        this.guardandoCategoria = false;
        this.editandoCategoriaId = null;
        this.formularioCategoria?.resetForm({ nombreCategoria: '' });
        this.cargarCategorias();
      },
      error: (err) => {
        cerrarCargando();
        this.guardandoCategoria = false;
        this.error = err?.error?.error ?? 'Ocurrió un error. Intenta nuevamente.';
      },
    });
  }

  eliminarCategoria(categoria: CategoriaGasto): void {
    this.categoriaGastoService.eliminar(categoria.id).subscribe({
      next: () => {
        this.error = '';
        if (this.editandoCategoriaId === categoria.id) this.cancelarEdicionCategoria();
        this.cargarCategorias();
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'Ocurrió un error. Intenta nuevamente.';
      },
    });
  }

  // --- Gastos ---

  cargarGastos(): void {
    this.gastoService
      .buscar({
        categoriaGastoId: this.filtroCategoriaId,
        fechaDesde: this.filtroFechaDesde || undefined,
        fechaHasta: this.filtroFechaHasta || undefined,
        q: this.filtroTexto || undefined,
        pagina: this.paginaGastos,
        tamano: this.tamanoGastos,
      })
      .subscribe((resp) => {
        if (!resp.contenido.length && this.paginaGastos > 0) {
          this.paginaGastos = Math.max(0, this.paginaGastos - 1);
          this.cargarGastos();
          return;
        }
        this.gastos = resp.contenido;
        this.totalGastos = resp.total;
      });
  }

  onBusquedaGastosChange(): void {
    this.busquedaGastos$.next(this.filtroTexto);
  }

  onFiltroGastosChange(): void {
    this.paginaGastos = 0;
    this.cargarGastos();
  }

  onPageGastosChange(event: PageEvent): void {
    this.paginaGastos = event.pageIndex;
    this.tamanoGastos = event.pageSize;
    this.cargarGastos();
  }

  editarGasto(gasto: Gasto): void {
    this.editandoGastoId = gasto.id;
    this.categoriaGastoId = gasto.categoriaGastoId;
    this.monto = gasto.monto;
    this.descripcion = gasto.descripcion;
    this.fecha = gasto.fecha;
  }

  cancelarEdicionGasto(): void {
    this.editandoGastoId = null;
    this.categoriaGastoId = null;
    this.monto = null;
    this.descripcion = '';
    this.fecha = '';
    this.formularioGasto?.resetForm();
  }

  guardarGasto(): void {
    if (!this.categoriaGastoId || !this.monto || !this.descripcion.trim() || !this.fecha) return;
    const request: GastoRequest = {
      categoriaGastoId: this.categoriaGastoId,
      monto: this.monto,
      descripcion: this.descripcion.trim(),
      fecha: this.fecha,
    };
    this.guardandoGasto = true;
    mostrarCargando(this.editandoGastoId ? 'Guardando cambios' : 'Creando gasto');
    const obs = this.editandoGastoId
      ? this.gastoService.actualizar(this.editandoGastoId, request)
      : this.gastoService.crear(request);
    obs.subscribe({
      next: () => {
        cerrarCargando();
        this.error = '';
        this.guardandoGasto = false;
        this.cancelarEdicionGasto();
        this.cargarGastos();
      },
      error: (err) => {
        cerrarCargando();
        this.guardandoGasto = false;
        this.error = err?.error?.error ?? 'Ocurrió un error. Intenta nuevamente.';
      },
    });
  }

  eliminarGasto(gasto: Gasto): void {
    this.gastoService.eliminar(gasto.id).subscribe({
      next: () => {
        this.error = '';
        if (this.editandoGastoId === gasto.id) this.cancelarEdicionGasto();
        this.cargarGastos();
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'Ocurrió un error. Intenta nuevamente.';
      },
    });
  }
}
