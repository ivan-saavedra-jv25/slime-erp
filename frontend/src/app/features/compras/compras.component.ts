import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { Bodega, CompraItem, Producto, Proveedor } from '../../core/models/models';
import { ProveedorService } from '../../core/services/proveedor.service';
import { ProductoService } from '../../core/services/producto.service';
import { BodegaService } from '../../core/services/bodega.service';
import { CompraService } from '../../core/services/compra.service';
import { AuthService } from '../../core/services/auth.service';

interface ItemStaged {
  productoId: number | null;
  descripcion: string;
  precio: number;
  cantidad: number;
}

function itemVacio(): ItemStaged {
  return { productoId: null, descripcion: '', precio: 0, cantidad: 1 };
}

@Component({
  selector: 'app-compras',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, MatButtonModule, MatIconModule, MatCardModule],
  templateUrl: './compras.component.html',
  styleUrl: './compras.component.scss',
})
export class ComprasComponent implements OnInit {
  proveedores: Proveedor[] = [];
  productos: Producto[] = [];
  bodegas: Bodega[] = [];

  proveedorId: number | null = null;
  bodegaId: number | null = null;
  observacion = '';
  items: CompraItem[] = [];
  guardando = false;
  error = '';
  mensaje = '';

  filtroProveedor = '';
  filtroProducto = '';
  itemStaged: ItemStaged = itemVacio();

  constructor(
    private proveedorService: ProveedorService,
    private productoService: ProductoService,
    private bodegaService: BodegaService,
    private compraService: CompraService,
    public auth: AuthService
  ) {}

  ngOnInit(): void {
    this.proveedorService.listar().subscribe((data) => (this.proveedores = data));
    this.productoService.listar().subscribe((data) => (this.productos = data));
    this.bodegaService.listar().subscribe((data) => {
      this.bodegas = data;
      const principal = data.find((b) => b.principal) ?? data[0];
      if (principal) this.bodegaId = principal.id;
    });
  }

  get proveedorSeleccionado(): Proveedor | null {
    return this.proveedores.find((p) => p.id === this.proveedorId) ?? null;
  }

  get proveedoresFiltrados(): Proveedor[] {
    const q = this.filtroProveedor.trim().toLowerCase();
    if (!q) return [];
    return this.proveedores.filter(
      (p) => p.nombre.toLowerCase().includes(q) || (p.rut ?? '').toLowerCase().includes(q)
    );
  }

  seleccionarProveedor(proveedor: Proveedor): void {
    this.proveedorId = proveedor.id;
    this.filtroProveedor = '';
  }

  cambiarProveedor(): void {
    this.proveedorId = null;
    this.filtroProveedor = '';
  }

  get productosFiltrados(): Producto[] {
    const q = this.filtroProducto.trim().toLowerCase();
    if (!q) return [];
    return this.productos.filter((p) => p.nombre.toLowerCase().includes(q) || (p.sku ?? '').toLowerCase().includes(q));
  }

  stageProducto(producto: Producto): void {
    this.itemStaged = {
      productoId: producto.id,
      descripcion: producto.nombre,
      precio: producto.precioCompra,
      cantidad: 1,
    };
    this.filtroProducto = '';
  }

  stagePrimeroFiltrado(): void {
    const primero = this.productosFiltrados[0];
    if (primero) this.stageProducto(primero);
  }

  get totalStaged(): number {
    return (this.itemStaged.precio || 0) * (this.itemStaged.cantidad || 0);
  }

  confirmarStaged(): void {
    if (!this.itemStaged.productoId || this.itemStaged.cantidad <= 0) return;

    const existente = this.items.find((it) => it.productoId === this.itemStaged.productoId);
    if (existente) {
      existente.cantidad += this.itemStaged.cantidad;
      existente.precioUnitario = this.itemStaged.precio;
    } else {
      this.items.push({
        productoId: this.itemStaged.productoId,
        cantidad: this.itemStaged.cantidad,
        precioUnitario: this.itemStaged.precio,
      });
    }
    this.itemStaged = itemVacio();
  }

  editarItem(index: number): void {
    const it = this.items[index];
    this.itemStaged = {
      productoId: it.productoId,
      descripcion: this.nombreProducto(it.productoId),
      precio: it.precioUnitario,
      cantidad: it.cantidad,
    };
    this.items.splice(index, 1);
  }

  quitarItem(index: number): void {
    this.items.splice(index, 1);
  }

  nombreProducto(id: number): string {
    return this.productos.find((p) => p.id === id)?.nombre ?? String(id);
  }

  skuProducto(id: number): string {
    return this.productos.find((p) => p.id === id)?.sku ?? '—';
  }

  get totalActual(): number {
    return this.items.reduce((acc, it) => acc + it.cantidad * it.precioUnitario, 0);
  }

  get puedeConfirmar(): boolean {
    return !this.guardando && !!this.proveedorId && !!this.bodegaId && this.items.length > 0;
  }

  confirmar(): void {
    if (!this.puedeConfirmar) return;
    this.guardando = true;
    this.error = '';
    this.mensaje = '';

    this.compraService
      .crear({
        proveedorId: this.proveedorId!,
        bodegaId: this.bodegaId,
        observacion: this.observacion,
        items: this.items,
      })
      .subscribe({
        next: (compra) => {
          this.mensaje = `Compra #${compra.id} registrada correctamente. Total: ${compra.total}.`;
          this.proveedorId = null;
          this.observacion = '';
          this.items = [];
          this.itemStaged = itemVacio();
          this.guardando = false;
        },
        error: (err) => {
          this.error = err?.error?.error ?? 'Ocurrió un error al registrar la compra.';
          this.guardando = false;
        },
      });
  }
}
