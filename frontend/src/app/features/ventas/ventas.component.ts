import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import Swal from 'sweetalert2';
import { Bodega, Cliente, FormaPago, InventarioItem, Producto, TipoDocumentoVenta, VentaItem } from '../../core/models/models';
import { ClienteService } from '../../core/services/cliente.service';
import { ProductoService } from '../../core/services/producto.service';
import { BodegaService } from '../../core/services/bodega.service';
import { FormaPagoService } from '../../core/services/forma-pago.service';
import { StockService } from '../../core/services/stock.service';
import { VentaService } from '../../core/services/venta.service';
import { AuthService } from '../../core/services/auth.service';
import { VentaPdfDialogComponent } from './venta-pdf-dialog.component';
import { ProductoBuscadorDialogComponent } from './producto-buscador-dialog.component';
import { cerrarCargando, mostrarCargando } from '../../core/utils/swal-loading';
import { MonedaPipe } from '../../core/pipes/moneda.pipe';

interface ItemStaged {
  productoId: number | null;
  descripcion: string;
  precio: number;
  cantidad: number;
  descuento: number;
}

function itemVacio(): ItemStaged {
  return { productoId: null, descripcion: '', precio: 0, cantidad: 1, descuento: 0 };
}

@Component({
  selector: 'app-ventas',
  standalone: true,
  imports: [CommonModule, FormsModule, MatButtonModule, MatIconModule, MatCardModule, MatDialogModule, MonedaPipe],
  templateUrl: './ventas.component.html',
  styleUrl: './ventas.component.scss',
})
export class VentasComponent implements OnInit, OnDestroy {
  bodegas: Bodega[] = [];
  formasPago: FormaPago[] = [];
  inventarioBodega: InventarioItem[] = [];

  clienteSeleccionadoObj: Cliente | null = null;
  clientesResultados: Cliente[] = [];
  clientesTotal = 0;

  productosResultados: Producto[] = [];
  productosTotal = 0;
  private readonly productosConocidos = new Map<number, Producto>();

  private readonly LIMITE_RESULTADOS = 8;
  private readonly busquedaCliente$ = new Subject<string>();
  private readonly busquedaProducto$ = new Subject<string>();

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
    private dialog: MatDialog,
    public auth: AuthService
  ) {}

  ngOnInit(): void {
    this.formaPagoService.listar().subscribe((data) => (this.formasPago = data));
    this.bodegaService.listar().subscribe((data) => {
      this.bodegas = data;
      const principal = data.find((b) => b.principal) ?? data[0];
      if (principal) {
        this.bodegaId = principal.id;
        this.onBodegaChange();
      }
    });

    this.busquedaCliente$.pipe(debounceTime(300), distinctUntilChanged()).subscribe((q) => this.buscarClientes(q));
    this.busquedaProducto$.pipe(debounceTime(300), distinctUntilChanged()).subscribe((q) => this.buscarProductos(q));
  }

  ngOnDestroy(): void {
    this.busquedaCliente$.complete();
    this.busquedaProducto$.complete();
  }

  onBodegaChange(): void {
    if (!this.bodegaId) {
      this.inventarioBodega = [];
      return;
    }
    this.stockService.inventarioPorBodega(this.bodegaId).subscribe((data) => (this.inventarioBodega = data));
  }

  stockEnBodega(productoId: number): number {
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
    return this.clienteSeleccionadoObj;
  }

  onFiltroClienteChange(): void {
    this.busquedaCliente$.next(this.filtroCliente);
  }

  private buscarClientes(q: string): void {
    const texto = q.trim();
    if (!texto) {
      this.clientesResultados = [];
      this.clientesTotal = 0;
      return;
    }
    this.clienteService.listarPagina(texto, 0, this.LIMITE_RESULTADOS).subscribe((resp) => {
      this.clientesResultados = resp.contenido;
      this.clientesTotal = resp.total;
    });
  }

  seleccionarCliente(cliente: Cliente): void {
    this.clienteId = cliente.id;
    this.clienteSeleccionadoObj = cliente;
    this.filtroCliente = '';
    this.clientesResultados = [];
  }

  cambiarCliente(): void {
    this.clienteId = null;
    this.clienteSeleccionadoObj = null;
    this.filtroCliente = '';
  }

  onFiltroProductoChange(): void {
    this.busquedaProducto$.next(this.filtroProducto);
  }

  private buscarProductos(q: string): void {
    const texto = q.trim();
    if (!texto) {
      this.productosResultados = [];
      this.productosTotal = 0;
      return;
    }
    this.productoService.listarPagina(texto, 0, this.LIMITE_RESULTADOS).subscribe((resp) => {
      this.productosResultados = resp.contenido;
      this.productosTotal = resp.total;
      resp.contenido.forEach((p) => this.productosConocidos.set(p.id, p));
    });
  }

  stageProducto(producto: Producto): void {
    this.productosConocidos.set(producto.id, producto);
    this.itemStaged = {
      productoId: producto.id,
      descripcion: producto.nombre,
      precio: producto.precioVenta,
      cantidad: 1,
      descuento: 0,
    };
    this.itemError = null;
    this.filtroProducto = '';
    this.productosResultados = [];
  }

  stagePrimeroFiltrado(): void {
    const primero = this.productosResultados[0];
    if (primero) this.stageProducto(primero);
  }

  get totalStaged(): number {
    return (this.itemStaged.precio || 0) * (this.itemStaged.cantidad || 0);
  }

  confirmarStaged(): void {
    if (!this.itemStaged.productoId || this.itemStaged.cantidad <= 0) return;

    const error = this.agregarAlCarrito(
      this.itemStaged.productoId,
      this.itemStaged.cantidad,
      this.itemStaged.precio,
      this.itemStaged.descuento
    );
    this.itemError = error;
    if (!error) {
      this.itemStaged = itemVacio();
    }
  }

  // Compartido entre el staging del buscador rápido y el modal de búsqueda de productos.
  private agregarAlCarrito(productoId: number, cantidad: number, precioUnitario: number, descuento: number): string | null {
    const producto = this.productosConocidos.get(productoId);
    const subtotalBruto = cantidad * precioUnitario;
    const descuentoAplicado = descuento || 0;
    if (descuentoAplicado < 0 || descuentoAplicado > subtotalBruto) {
      return `El descuento de "${producto?.nombre ?? productoId}" no puede ser negativo ni superar su subtotal (${subtotalBruto}).`;
    }

    const existente = this.items.find((it) => it.productoId === productoId);
    const yaEnCarrito = existente?.cantidad ?? 0;
    const stockDisponible = this.stockEnBodega(productoId);
    if (yaEnCarrito + cantidad > stockDisponible) {
      return `No se puede agregar esa cantidad: el stock disponible de "${producto?.nombre ?? productoId}" en esta bodega es ${stockDisponible}.`;
    }

    if (existente) {
      existente.cantidad += cantidad;
      existente.precioUnitario = precioUnitario;
      existente.descuento = descuentoAplicado;
    } else {
      this.items.push({ productoId, cantidad, precioUnitario, descuento: descuentoAplicado });
    }
    return null;
  }

  totalLinea(it: VentaItem): number {
    return it.cantidad * it.precioUnitario - (it.descuento || 0);
  }

  abrirBuscadorProductos(): void {
    const ref = this.dialog.open(ProductoBuscadorDialogComponent, {
      data: { bodegaId: this.bodegaId },
      width: '720px',
      maxWidth: '90vw',
    });
    ref.componentInstance.agregar.subscribe((producto: Producto) => {
      this.productosConocidos.set(producto.id, producto);
      const error = this.agregarAlCarrito(producto.id, 1, producto.precioVenta, 0);
      ref.componentInstance.mostrarResultado(producto.id, error);
    });
  }

  editarItem(index: number): void {
    const it = this.items[index];
    this.itemStaged = {
      productoId: it.productoId,
      descripcion: this.nombreProducto(it.productoId),
      precio: it.precioUnitario,
      cantidad: it.cantidad,
      descuento: it.descuento || 0,
    };
    this.items.splice(index, 1);
  }

  quitarItem(index: number): void {
    this.items.splice(index, 1);
  }

  nombreProducto(id: number): string {
    return this.productosConocidos.get(id)?.nombre ?? String(id);
  }

  skuProducto(id: number): string {
    return this.productosConocidos.get(id)?.sku ?? '—';
  }

  get subtotalActual(): number {
    return this.items.reduce((acc, it) => acc + this.totalLinea(it), 0);
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
    mostrarCargando('Registrando venta');

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
          this.clienteId = null;
          this.clienteSeleccionadoObj = null;
          this.filtroCliente = '';
          this.formaPagoId = null;
          this.observacion = '';
          this.descuento = 0;
          this.items = [];
          this.itemStaged = itemVacio();
          this.itemError = null;
          this.guardando = false;
          this.onBodegaChange();
          // Espera a que el overlay de carga termine de cerrarse antes de abrir
          // el Swal de éxito: si se abre mientras el close() del overlay sigue
          // pendiente, ese close() cierra el nuevo Swal sin que el usuario alcance a verlo.
          cerrarCargando().then(() => this.mostrarComprobante(venta.id));
        },
        error: (err) => {
          cerrarCargando();
          this.error = err?.error?.error ?? 'Ocurrió un error al registrar la venta.';
          this.guardando = false;
        },
      });
  }

  private mostrarComprobante(ventaId: number): void {
    Swal.fire({
      title: 'Venta generada',
      text: `La venta #${ventaId} se generó con éxito.`,
      icon: 'success',
      confirmButtonText: 'Ver comprobante',
    }).then(() => {
      this.ventaService.obtenerPdf(ventaId).subscribe({
        next: (blob) => {
          const url = URL.createObjectURL(blob);
          const dialogRef = this.dialog.open(VentaPdfDialogComponent, {
            data: { ventaId, url },
            width: '90vw',
            maxWidth: '1200px',
          });
          dialogRef.afterClosed().subscribe(() => URL.revokeObjectURL(url));
        },
        error: () => {
          Swal.fire('No se pudo cargar el comprobante', '', 'error');
        },
      });
    });
  }
}
