import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { Bodega, MovimientoItem, Producto, TipoMovimiento } from '../../core/models/models';
import { BodegaService } from '../../core/services/bodega.service';
import { ProductoService } from '../../core/services/producto.service';
import { MovimientoService } from '../../core/services/movimiento.service';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-movimientos',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, MatButtonModule, MatIconModule, MatCardModule],
  templateUrl: './movimientos.component.html',
  styleUrl: './movimientos.component.scss',
})
export class MovimientosComponent implements OnInit {
  bodegas: Bodega[] = [];
  productos: Producto[] = [];

  tipo: TipoMovimiento = 'ENTRADA';
  bodegaOrigenId: number | null = null;
  bodegaDestinoId: number | null = null;
  observacion = '';
  items: MovimientoItem[] = [];
  guardando = false;
  mensaje = '';
  error = '';

  filtroProducto = '';
  itemProductoId: number | null = null;
  itemCantidad = 1;
  itemError: string | null = null;

  readonly tipos: { value: TipoMovimiento; label: string; icon: string; desc: string }[] = [
    { value: 'ENTRADA', label: 'Entrada', icon: 'input', desc: 'Agregar stock a una bodega' },
    { value: 'SALIDA', label: 'Salida', icon: 'output', desc: 'Reducir stock de una bodega' },
    { value: 'TRASLADO', label: 'Traslado', icon: 'swap_horiz', desc: 'Mover stock entre bodegas' },
    { value: 'AJUSTE', label: 'Ajuste', icon: 'tune', desc: 'Corrección manual de inventario' },
  ];

  constructor(
    private bodegaService: BodegaService,
    private productoService: ProductoService,
    private movimientoService: MovimientoService,
    public auth: AuthService
  ) {}

  ngOnInit(): void {
    this.bodegaService.listar().subscribe((data) => (this.bodegas = data));
    this.productoService.listar().subscribe((data) => (this.productos = data));
  }

  get nombreUsuario(): string {
    return this.auth.session()?.nombre ?? '';
  }

  get productosFiltrados(): Producto[] {
    const q = this.filtroProducto.trim().toLowerCase();
    if (!q) return [];
    return this.productos.filter(
      (p) => p.nombre.toLowerCase().includes(q) || (p.sku ?? '').toLowerCase().includes(q)
    );
  }

  seleccionarTipo(t: TipoMovimiento): void {
    this.tipo = t;
    this.bodegaOrigenId = null;
    this.bodegaDestinoId = null;
    this.itemError = null;
  }

  get mostrarOrigen(): boolean {
    return this.tipo === 'SALIDA' || this.tipo === 'TRASLADO' || this.tipo === 'AJUSTE';
  }

  get mostrarDestino(): boolean {
    return this.tipo === 'ENTRADA' || this.tipo === 'TRASLADO';
  }

  get labelOrigen(): string {
    return this.tipo === 'AJUSTE' ? 'Bodega' : 'Bodega origen';
  }

  seleccionarProducto(producto: Producto): void {
    this.itemProductoId = producto.id;
    this.itemCantidad = 1;
    this.filtroProducto = '';
    this.itemError = null;
  }

  seleccionarPrimero(): void {
    const primero = this.productosFiltrados[0];
    if (primero) this.seleccionarProducto(primero);
  }

  get productoSeleccionado(): Producto | null {
    if (!this.itemProductoId) return null;
    return this.productos.find((p) => p.id === this.itemProductoId) ?? null;
  }

  agregarItem(): void {
    if (!this.itemProductoId || this.itemCantidad <= 0) return;
    this.itemError = null;

    if (this.items.some((it) => it.productoId === this.itemProductoId)) {
      this.itemError = 'Este producto ya está en la lista.';
      return;
    }

    this.items.push({ productoId: this.itemProductoId, cantidad: this.itemCantidad });
    this.itemProductoId = null;
    this.itemCantidad = 1;
    this.filtroProducto = '';
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

  get totalUnidades(): number {
    return this.items.reduce((acc, it) => acc + it.cantidad, 0);
  }

  get puedeConfirmar(): boolean {
    if (this.guardando || !this.items.length) return false;
    if (this.mostrarOrigen && !this.bodegaOrigenId) return false;
    if (this.mostrarDestino && !this.bodegaDestinoId) return false;
    return true;
  }

  confirmar(): void {
    if (!this.puedeConfirmar) return;
    this.guardando = true;
    this.itemError = '';
    this.error = '';
    this.mensaje = '';

    this.movimientoService
      .crear({
        tipo: this.tipo,
        bodegaOrigenId: this.bodegaOrigenId,
        bodegaDestinoId: this.bodegaDestinoId,
        observacion: this.observacion,
        items: this.items,
      })
      .subscribe({
        next: () => {
          this.mensaje = 'Movimiento registrado correctamente.';
          this.items = [];
          this.observacion = '';
          this.bodegaOrigenId = null;
          this.bodegaDestinoId = null;
          this.guardando = false;
        },
        error: (err) => {
          this.error = err?.error?.error ?? 'Error al registrar el movimiento.';
          this.guardando = false;
        },
      });
  }
}
