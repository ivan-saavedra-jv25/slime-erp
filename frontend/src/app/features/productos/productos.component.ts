import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';
import { ProductoService, ProductoRequest } from '../../core/services/producto.service';
import { AuthService } from '../../core/services/auth.service';
import { Producto } from '../../core/models/models';
import { ProductoFormDialogComponent } from './producto-form-dialog.component';

@Component({
  selector: 'app-productos',
  standalone: true,
  imports: [CommonModule, MatTableModule, MatButtonModule, MatIconModule],
  templateUrl: './productos.component.html',
  styleUrl: './productos.component.scss',
})
export class ProductosComponent implements OnInit {
  columnas = ['sku', 'nombre', 'precioVenta', 'stock', 'acciones'];
  productos: Producto[] = [];
  error = '';

  constructor(private productoService: ProductoService, public auth: AuthService, private dialog: MatDialog) {}

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.productoService.listar().subscribe((productos) => (this.productos = productos));
  }

  abrirCrear(): void {
    const ref = this.dialog.open(ProductoFormDialogComponent, { width: '420px' });
    ref.afterClosed().subscribe((request: ProductoRequest | undefined) => {
      if (request) {
        this.productoService.crear(request).subscribe({
          next: () => {
            this.error = '';
            this.cargar();
          },
          error: (err) => {
            this.error = err?.error?.error ?? 'Ocurrió un error. Intenta nuevamente.';
          },
        });
      }
    });
  }

  abrirEditar(producto: Producto): void {
    const ref = this.dialog.open(ProductoFormDialogComponent, { width: '420px', data: producto });
    ref.afterClosed().subscribe((request: ProductoRequest | undefined) => {
      if (request) {
        this.productoService.actualizar(producto.id, request).subscribe({
          next: () => {
            this.error = '';
            this.cargar();
          },
          error: (err) => {
            this.error = err?.error?.error ?? 'Ocurrió un error. Intenta nuevamente.';
          },
        });
      }
    });
  }

  eliminar(producto: Producto): void {
    this.productoService.eliminar(producto.id).subscribe({
      next: () => {
        this.error = '';
        this.cargar();
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'Ocurrió un error. Intenta nuevamente.';
      },
    });
  }
}
