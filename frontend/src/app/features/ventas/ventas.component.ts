import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { Bodega, Cliente, FormaPago, InventarioItem, Producto, TipoDocumentoVenta, VentaItem } from '../../core/models/models';
import { ClienteService } from '../../core/services/cliente.service';
import { ProductoService } from '../../core/services/producto.service';
import { BodegaService } from '../../core/services/bodega.service';
import { FormaPagoService } from '../../core/services/forma-pago.service';
import { StockService } from '../../core/services/stock.service';
import { VentaService } from '../../core/services/venta.service';
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
  selector: 'app-ventas',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, MatButtonModule, MatIconModule, MatCardModule],
  templateUrl: './ventas.component.html',
  styleUrl: './ventas.component.scss',
})
export class VentasComponent implements OnInit {
  clientes: Cliente[] = [];
  productos: Producto[] = [];
  bodegas: Bodega[] = [];
  formasPago: FormaPago[] = [];
  inventarioBodega: InventarioItem[] = [];

  readonly tiposDocumento: { value: TipoDocumentoVenta; label: string; desc: string }[] = [
    { value: 'BOLETA', label: 'Boleta', desc: 'Afecta: detalle en bruto (IVA incluido), el total se desglosa. Puede marcarse como exenta.' },
    { value: 'FACTURA', label: 'Factura', desc: 'Afecta: detalle en neto, el IVA se calcula y se suma. Puede marcarse como exenta.' },
    { value: 'VOUCHER', label: 'Voucher', desc: 'Documento interno sin IVA.' },
  ];

  clienteId: number | null = null;
  bodegaId: number | null = null;
  formaPagoId: number | null = null;
  tipoDocumento: TipoDocumentoVenta = 'BOLETA';
  exento = false;
  observacion = '';
  descuento = 0;
  items: VentaItem[] = [];
  guardando = false;
  error = '';
  mensaje = '';

  filtroCliente = '';
  filtroProducto = '';
  itemStaged: ItemStaged = itemVacio();
  itemError: string | null = null;

  constructor(
    private clienteService: ClienteService,
    private productoService: ProductoService,
    private bodegaService: BodegaService,
    private formaPagoService: FormaPagoService,
    private stockService: StockService,
    private ventaService: VentaService,
    public auth: AuthService
  ) {}

  ngOnInit(): void {
    this.clienteService.listar().subscribe((data) => (this.clientes = data));
    this.productoService.listar().subscribe((data) => (this.productos = data));
    this.formaPagoService.listar().subscribe((data) => (this.formasPago = data));
    this.bodegaService.listar().subscribe((data) => {
      this.bodegas = data;
      const principal = data.find((b) => b.principal) ?? data[0];
      if (principal) {
        this.bodegaId = principal.id;
        this.onBodegaChange();
      }
    });
  }

  onBodegaChange(): void {
    if (!this.bodegaId) {
      this.inventarioBodega = [];
      return;
    }
    this.stockService.inventarioPorBodega(this.bodegaId).subscribe((data) => (this.inventarioBodega = data));
  }

  private stockEnBodega(productoId: number): number {
    return this.inventarioBodega.find((i) => i.productoId === productoId)?.cantidad ?? 0;
  }

  seleccionarTipoDocumento(tipo: TipoDocumentoVenta): void {
    this.tipoDocumento = tipo;
    this.exento = false;
  }

  get sinIva(): boolean {
    return this.tipoDocumento === 'VOUCHER' || this.exento;
  }

  get labelPrecio(): string {
    if (this.sinIva) return 'Precio';
    if (this.tipoDocumento === 'FACTURA') return 'Precio neto';
    if (this.tipoDocumento === 'BOLETA') return 'Precio bruto';
    return 'Precio';
  }

  get labelExento(): string {
    if (this.tipoDocumento === 'FACTURA') return 'Factura exenta (tipo 34, en vez de la afecta tipo 33)';
    if (this.tipoDocumento === 'BOLETA') return 'Boleta exenta (tipo 41, en vez de la afecta tipo 39)';
    return 'Venta exenta (desmarcado = venta interna)';
  }

  get clienteSeleccionado(): Cliente | null {
    return this.clientes.find((c) => c.id === this.clienteId) ?? null;
  }

  get clientesFiltrados(): Cliente[] {
    const q = this.filtroCliente.trim().toLowerCase();
    if (!q) return [];
    return this.clientes.filter((c) => c.nombre.toLowerCase().includes(q) || (c.rut ?? '').toLowerCase().includes(q));
  }

  seleccionarCliente(cliente: Cliente): void {
    this.clienteId = cliente.id;
    this.filtroCliente = '';
  }

  cambiarCliente(): void {
    this.clienteId = null;
    this.filtroCliente = '';
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
      precio: producto.precioVenta,
      cantidad: 1,
    };
    this.itemError = null;
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
    this.itemError = null;

    const producto = this.productos.find((p) => p.id === this.itemStaged.productoId)!;
    const existente = this.items.find((it) => it.productoId === this.itemStaged.productoId);
    const yaEnCarrito = existente?.cantidad ?? 0;
    const stockDisponible = this.stockEnBodega(producto.id);
    if (yaEnCarrito + this.itemStaged.cantidad > stockDisponible) {
      this.itemError = `No se puede agregar esa cantidad: el stock disponible de "${producto.nombre}" en esta bodega es ${stockDisponible}.`;
      return;
    }

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

  get subtotalActual(): number {
    return this.items.reduce((acc, it) => acc + it.cantidad * it.precioUnitario, 0);
  }

  get montoConDescuento(): number {
    return Math.max(this.subtotalActual - (this.descuento || 0), 0);
  }

  get netoActual(): number {
    if (this.sinIva) return this.montoConDescuento;
    if (this.tipoDocumento === 'FACTURA') return this.montoConDescuento;
    return Math.round(this.montoConDescuento / 1.19);
  }

  get ivaActual(): number {
    if (this.sinIva) return 0;
    if (this.tipoDocumento === 'FACTURA') return Math.round(this.netoActual * 0.19);
    return this.montoConDescuento - this.netoActual;
  }

  get totalActual(): number {
    if (this.tipoDocumento === 'FACTURA' && !this.sinIva) return this.netoActual + this.ivaActual;
    return this.montoConDescuento;
  }

  get puedeConfirmar(): boolean {
    return !this.guardando && !!this.clienteId && !!this.bodegaId && !!this.formaPagoId && this.items.length > 0;
  }

  confirmar(): void {
    if (!this.puedeConfirmar) return;
    this.guardando = true;
    this.error = '';
    this.mensaje = '';

    this.ventaService
      .crear({
        clienteId: this.clienteId!,
        formaPagoId: this.formaPagoId!,
        bodegaId: this.bodegaId,
        tipoDocumento: this.tipoDocumento,
        exento: this.exento,
        observacion: this.observacion,
        descuento: this.descuento,
        items: this.items,
      })
      .subscribe({
        next: (venta) => {
          this.mensaje = `Venta #${venta.id} registrada correctamente. Total: ${venta.montoTotal}.`;
          this.clienteId = null;
          this.formaPagoId = null;
          this.observacion = '';
          this.descuento = 0;
          this.items = [];
          this.itemStaged = itemVacio();
          this.itemError = null;
          this.guardando = false;
          this.onBodegaChange();
        },
        error: (err) => {
          this.error = err?.error?.error ?? 'Ocurrió un error al registrar la venta.';
          this.guardando = false;
        },
      });
  }
}
