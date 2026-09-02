import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { ProductoService, ProductoRequest } from '../../core/services/producto.service';
import { CategoriaService } from '../../core/services/categoria.service';
import { SubcategoriaService } from '../../core/services/subcategoria.service';
import { AuthService } from '../../core/services/auth.service';
import { Categoria, Producto, Subcategoria } from '../../core/models/models';

@Component({
  selector: 'app-productos',
  standalone: true,
  imports: [CommonModule, FormsModule, MatTableModule, MatButtonModule, MatIconModule, MatCardModule],
  templateUrl: './productos.component.html',
  styleUrl: './productos.component.scss',
})
export class ProductosComponent implements OnInit {
  columnas = ['sku', 'nombre', 'categoria', 'precioVenta', 'acciones'];
  productos: Producto[] = [];
  categorias: Categoria[] = [];
  subcategoriasDisponibles: Subcategoria[] = [];
  error = '';
  guardando = false;
  editandoId: number | null = null;

  sku = '';
  nombre = '';
  descripcion = '';
  categoriaId: number | null = null;
  subcategoriaId: number | null = null;
  precioVenta = 0;
  precioCompra = 0;

  constructor(
    private productoService: ProductoService,
    private categoriaService: CategoriaService,
    private subcategoriaService: SubcategoriaService,
    public auth: AuthService
  ) {}

  ngOnInit(): void {
    this.cargar();
    this.categoriaService.listar().subscribe((data) => (this.categorias = data));
  }

  cargar(): void {
    this.productoService.listar().subscribe((productos) => (this.productos = productos));
  }

  nombreCategoria(id: number | null): string {
    return this.categorias.find((c) => c.id === id)?.nombre ?? '';
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
    this.nombre = producto.nombre;
    this.descripcion = producto.descripcion ?? '';
    this.precioVenta = producto.precioVenta;
    this.precioCompra = producto.precioCompra;
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
      nombre: this.nombre,
      descripcion: this.descripcion,
      categoriaId: this.categoriaId,
      subcategoriaId: this.subcategoriaId,
      precioVenta: this.precioVenta,
      precioCompra: this.precioCompra,
    };
    this.guardando = true;
    const obs = this.editandoId
      ? this.productoService.actualizar(this.editandoId, request)
      : this.productoService.crear(request);
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

  private limpiarFormulario(): void {
    this.sku = '';
    this.nombre = '';
    this.descripcion = '';
    this.categoriaId = null;
    this.subcategoriaId = null;
    this.subcategoriasDisponibles = [];
    this.precioVenta = 0;
    this.precioCompra = 0;
  }
}
