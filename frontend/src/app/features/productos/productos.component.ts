import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { ProductoService, ProductoRequest } from '../../core/services/producto.service';
import { AuthService } from '../../core/services/auth.service';
import { Producto } from '../../core/models/models';

@Component({
  selector: 'app-productos',
  standalone: true,
  imports: [CommonModule, FormsModule, MatTableModule, MatButtonModule, MatIconModule, MatCardModule],
  templateUrl: './productos.component.html',
  styleUrl: './productos.component.scss',
})
export class ProductosComponent implements OnInit {
  columnas = ['sku', 'nombre', 'precioVenta', 'acciones'];
  productos: Producto[] = [];
  error = '';
  guardando = false;
  editandoId: number | null = null;

  sku = '';
  nombre = '';
  descripcion = '';
  precioVenta = 0;
  precioCompra = 0;

  constructor(private productoService: ProductoService, public auth: AuthService) {}

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.productoService.listar().subscribe((productos) => (this.productos = productos));
  }

  editar(producto: Producto): void {
    this.editandoId = producto.id;
    this.sku = producto.sku ?? '';
    this.nombre = producto.nombre;
    this.descripcion = producto.descripcion ?? '';
    this.precioVenta = producto.precioVenta;
    this.precioCompra = producto.precioCompra;
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
    this.precioVenta = 0;
    this.precioCompra = 0;
  }
}
