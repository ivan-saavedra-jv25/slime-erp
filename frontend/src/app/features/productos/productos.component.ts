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
import { ProductoService, ProductoRequest } from '../../core/services/producto.service';
import { CategoriaService } from '../../core/services/categoria.service';
import { SubcategoriaService } from '../../core/services/subcategoria.service';
import { AuthService } from '../../core/services/auth.service';
import { Categoria, Producto, Subcategoria } from '../../core/models/models';
import { MonedaPipe } from '../../core/pipes/moneda.pipe';
import { cerrarCargando, mostrarCargando } from '../../core/utils/swal-loading';

@Component({
  selector: 'app-productos',
  standalone: true,
  imports: [CommonModule, FormsModule, MatTableModule, MatPaginatorModule, MatButtonModule, MatIconModule, MatCardModule, MonedaPipe],
  templateUrl: './productos.component.html',
  styleUrl: './productos.component.scss',
})
export class ProductosComponent implements OnInit, OnDestroy {
  @ViewChild('f') formulario?: NgForm;

  columnas = ['sku', 'codigoBarra', 'nombre', 'categoria', 'precioVenta', 'acciones'];
  productos: Producto[] = [];
  total = 0;
  categorias: Categoria[] = [];
  subcategoriasDisponibles: Subcategoria[] = [];
  error = '';
  guardando = false;
  editandoId: number | null = null;

  filtro = '';
  paginaActual = 0;
  tamanoPagina = 10;
  readonly opcionesTamano = [10, 25, 50];

  sku = '';
  codigoBarra = '';
  nombre = '';
  descripcion = '';
  categoriaId: number | null = null;
  subcategoriaId: number | null = null;
  precioVenta = 0;
  precioCompra = 0;
  stockMinimo = 0;

  private readonly busqueda$ = new Subject<string>();

  constructor(
    private productoService: ProductoService,
    private categoriaService: CategoriaService,
    private subcategoriaService: SubcategoriaService,
    public auth: AuthService
  ) {}

  ngOnInit(): void {
    this.categoriaService.listar().subscribe((data) => (this.categorias = data));
    this.busqueda$.pipe(debounceTime(300), distinctUntilChanged()).subscribe(() => {
      this.paginaActual = 0;
      this.cargar();
    });
    this.cargar();
  }

  ngOnDestroy(): void {
    this.busqueda$.complete();
  }

  cargar(): void {
    this.productoService.listarPagina(this.filtro, this.paginaActual, this.tamanoPagina).subscribe((resp) => {
      if (!resp.contenido.length && this.paginaActual > 0) {
        this.paginaActual = Math.max(0, this.paginaActual - 1);
        this.cargar();
        return;
      }
      this.productos = resp.contenido;
      this.total = resp.total;
    });
  }

  nombreCategoria(id: number | null): string {
    return this.categorias.find((c) => c.id === id)?.nombre ?? '';
  }

  onFiltroChange(): void {
    this.busqueda$.next(this.filtro);
  }

  onPageChange(event: PageEvent): void {
    this.paginaActual = event.pageIndex;
    this.tamanoPagina = event.pageSize;
    this.cargar();
  }

  onCategoriaChange(): void {
    this.subcategoriaId = null;
    this.subcategoriasDisponibles = [];
    if (this.categoriaId != null) {
      this.subcategoriaService.listar(this.categoriaId).subscribe((data) => (this.subcategoriasDisponibles = data));
    }
  }

  editar(producto: Producto): void {
    this.editandoId = producto.id;
    this.sku = producto.sku ?? '';
    this.codigoBarra = producto.codigoBarra ?? '';
    this.nombre = producto.nombre;
    this.descripcion = producto.descripcion ?? '';
    this.precioVenta = producto.precioVenta;
    this.precioCompra = producto.precioCompra;
    this.stockMinimo = producto.stockMinimo;
    this.categoriaId = producto.categoriaId;
    this.subcategoriaId = null;
    this.subcategoriasDisponibles = [];
    if (this.categoriaId != null) {
      this.subcategoriaService.listar(this.categoriaId).subscribe((data) => {
        this.subcategoriasDisponibles = data;
        this.subcategoriaId = producto.subcategoriaId;
      });
    }
  }

  cancelarEdicion(): void {
    this.editandoId = null;
    this.limpiarFormulario();
  }

  guardar(): void {
    if (!this.nombre) return;
    const request: ProductoRequest = {
      sku: this.sku || null,
      codigoBarra: this.codigoBarra || null,
      nombre: this.nombre,
      descripcion: this.descripcion,
      categoriaId: this.categoriaId,
      subcategoriaId: this.subcategoriaId,
      precioVenta: this.precioVenta,
      precioCompra: this.precioCompra,
      stockMinimo: this.stockMinimo,
    };
    this.guardando = true;
    mostrarCargando(this.editandoId ? 'Guardando cambios' : 'Creando producto');
    const obs = this.editandoId
      ? this.productoService.actualizar(this.editandoId, request)
      : this.productoService.crear(request);
    obs.subscribe({
      next: () => {
        cerrarCargando();
        this.error = '';
        this.guardando = false;
        this.editandoId = null;
        this.limpiarFormulario();
        this.cargar();
      },
      error: (err) => {
        cerrarCargando();
        this.guardando = false;
        this.error = this.mensajeError(err);
      },
    });
  }

  // Traduce errores del backend a un mensaje entendible para el usuario.
  // Los códigos 409 (SKU/código de barra duplicado) ya vienen con un mensaje
  // claro; cualquier otro caso usa un mensaje genérico en vez del detalle técnico.
  private mensajeError(err: any): string {
    if (err?.status === 409 && err?.error?.error) {
      return err.error.error;
    }
    return 'No se pudo guardar el producto. Verifica los datos e intenta nuevamente.';
  }

  eliminar(producto: Producto): void {
    this.productoService.eliminar(producto.id).subscribe({
      next: () => {
        this.error = '';
        if (this.editandoId === producto.id) this.cancelarEdicion();
        this.cargar();
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'Ocurrió un error. Intenta nuevamente.';
      },
    });
  }

  // Limpia valores y también el estado touched/dirty del formulario: si solo
  // se resetean los campos, Angular conserva el "touched" previo y el campo
  // Nombre vuelve a mostrarse en rojo como si fuera obligatorio, aunque el
  // producto ya se haya guardado correctamente.
  private limpiarFormulario(): void {
    this.subcategoriasDisponibles = [];
    this.formulario?.resetForm({
      sku: '',
      codigoBarra: '',
      nombre: '',
      descripcion: '',
      categoriaId: null,
      subcategoriaId: null,
      precioVenta: 0,
      precioCompra: 0,
      stockMinimo: 0,
    });
  }
}
