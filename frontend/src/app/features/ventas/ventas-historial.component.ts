import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { Cliente, FormaPago, Producto, TipoDocumentoVenta, Venta } from '../../core/models/models';
import { VentaService } from '../../core/services/venta.service';
import { ClienteService } from '../../core/services/cliente.service';
import { ProductoService } from '../../core/services/producto.service';
import { FormaPagoService } from '../../core/services/forma-pago.service';

const ETIQUETAS: Record<TipoDocumentoVenta, string> = {
  BOLETA: 'Boleta',
  FACTURA: 'Factura',
  VOUCHER: 'Voucher',
};

const TAGS: Record<TipoDocumentoVenta, string> = {
  BOLETA: 'tag--success',
  FACTURA: 'tag--info',
  VOUCHER: 'tag--warning',
};

@Component({
  selector: 'app-ventas-historial',
  standalone: true,
  imports: [CommonModule, RouterLink, MatButtonModule, MatIconModule, MatCardModule],
  templateUrl: './ventas-historial.component.html',
  styleUrl: './ventas-historial.component.scss',
})
export class VentasHistorialComponent implements OnInit {
  ventas: Venta[] = [];
  clientes: Cliente[] = [];
  productos: Producto[] = [];
  formasPago: FormaPago[] = [];
  cargando = true;
  detalleAbierto: number | null = null;

  constructor(
    private ventaService: VentaService,
    private clienteService: ClienteService,
    private productoService: ProductoService,
    private formaPagoService: FormaPagoService
  ) {}

  ngOnInit(): void {
    this.clienteService.listar().subscribe((data) => (this.clientes = data));
    this.productoService.listar().subscribe((data) => (this.productos = data));
    this.formaPagoService.listar().subscribe((data) => (this.formasPago = data));
    this.cargar();
  }

  cargar(): void {
    this.cargando = true;
    this.ventaService.listar().subscribe({
      next: (data) => {
        this.ventas = data;
        this.cargando = false;
      },
      error: () => (this.cargando = false),
    });
  }

  toggleDetalle(id: number): void {
    this.detalleAbierto = this.detalleAbierto === id ? null : id;
  }

  etiquetaTipo(tipo: TipoDocumentoVenta): string {
    return ETIQUETAS[tipo];
  }

  tagTipo(tipo: TipoDocumentoVenta): string {
    return TAGS[tipo];
  }

  sinIva(venta: Venta): boolean {
    return venta.tipoDocumento === 'VOUCHER' || venta.exento;
  }

  nombreCliente(id: number): string {
    return this.clientes.find((c) => c.id === id)?.nombre ?? String(id);
  }

  nombreFormaPago(id: number): string {
    return this.formasPago.find((fp) => fp.id === id)?.nombre ?? String(id);
  }

  nombreProducto(id: number): string {
    return this.productos.find((p) => p.id === id)?.nombre ?? String(id);
  }

  skuProducto(id: number): string {
    return this.productos.find((p) => p.id === id)?.sku ?? '—';
  }
}
