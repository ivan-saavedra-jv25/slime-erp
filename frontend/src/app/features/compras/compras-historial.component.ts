import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { Bodega, Compra, Producto, Proveedor } from '../../core/models/models';
import { CompraService } from '../../core/services/compra.service';
import { ProveedorService } from '../../core/services/proveedor.service';
import { ProductoService } from '../../core/services/producto.service';
import { BodegaService } from '../../core/services/bodega.service';

@Component({
  selector: 'app-compras-historial',
  standalone: true,
  imports: [CommonModule, RouterLink, MatButtonModule, MatIconModule, MatCardModule],
  templateUrl: './compras-historial.component.html',
  styleUrl: './compras-historial.component.scss',
})
export class ComprasHistorialComponent implements OnInit {
  compras: Compra[] = [];
  proveedores: Proveedor[] = [];
  productos: Producto[] = [];
  bodegas: Bodega[] = [];
  cargando = true;
  detalleAbierto: number | null = null;

  constructor(
    private compraService: CompraService,
    private proveedorService: ProveedorService,
    private productoService: ProductoService,
    private bodegaService: BodegaService
  ) {}

  ngOnInit(): void {
    this.proveedorService.listar().subscribe((data) => (this.proveedores = data));
    this.productoService.listar().subscribe((data) => (this.productos = data));
    this.bodegaService.listar().subscribe((data) => (this.bodegas = data));
    this.cargar();
  }

  cargar(): void {
    this.cargando = true;
    this.compraService.listar().subscribe({
      next: (data) => {
        this.compras = data;
        this.cargando = false;
      },
      error: () => (this.cargando = false),
    });
  }

  toggleDetalle(id: number): void {
    this.detalleAbierto = this.detalleAbierto === id ? null : id;
  }

  nombreProveedor(id: number): string {
    return this.proveedores.find((p) => p.id === id)?.nombre ?? String(id);
  }

  nombreBodega(id: number): string {
    return this.bodegas.find((b) => b.id === id)?.nombre ?? String(id);
  }

  nombreProducto(id: number): string {
    return this.productos.find((p) => p.id === id)?.nombre ?? String(id);
  }

  skuProducto(id: number): string {
    return this.productos.find((p) => p.id === id)?.sku ?? '—';
  }
}
