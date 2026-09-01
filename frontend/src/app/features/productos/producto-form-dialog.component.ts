import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatButtonModule } from '@angular/material/button';
import { Producto } from '../../core/models/models';
import { ProductoRequest } from '../../core/services/producto.service';

@Component({
  selector: 'app-producto-form-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatCheckboxModule, MatButtonModule],
  templateUrl: './producto-form-dialog.component.html',
})
export class ProductoFormDialogComponent {
  esEdicion: boolean;

  sku: string;
  nombre: string;
  descripcion: string;
  precioVenta: number;
  precioCompra: number;
  stock: number;
  controlaStock: boolean;

  constructor(
    private ref: MatDialogRef<ProductoFormDialogComponent, ProductoRequest>,
    @Inject(MAT_DIALOG_DATA) public data: Producto | null
  ) {
    this.esEdicion = !!this.data;
    this.sku = this.data?.sku ?? '';
    this.nombre = this.data?.nombre ?? '';
    this.descripcion = this.data?.descripcion ?? '';
    this.precioVenta = this.data?.precioVenta ?? 0;
    this.precioCompra = this.data?.precioCompra ?? 0;
    this.stock = this.data?.stock ?? 0;
    this.controlaStock = this.data?.controlaStock ?? true;
  }

  guardar(): void {
    const request: ProductoRequest = {
      sku: this.sku || null,
      nombre: this.nombre,
      descripcion: this.descripcion,
      precioVenta: this.precioVenta,
      precioCompra: this.precioCompra,
      stock: this.stock,
      controlaStock: this.controlaStock,
    };
    this.ref.close(request);
  }

  cancelar(): void {
    this.ref.close();
  }
}
