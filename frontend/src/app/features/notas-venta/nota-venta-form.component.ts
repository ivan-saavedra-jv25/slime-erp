import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { Cliente, FormaPago, NotaVentaItem, Producto } from '../../core/models/models';
import { NotaVentaService } from '../../core/services/nota-venta.service';
import { ClienteService } from '../../core/services/cliente.service';
import { ProductoService } from '../../core/services/producto.service';
import { FormaPagoService } from '../../core/services/forma-pago.service';
import { MonedaPipe } from '../../core/pipes/moneda.pipe';
import { cerrarCargando, mostrarCargando } from '../../core/utils/swal-loading';

const TASA_IVA = 0.19;
const MONEDAS = ['CLP', 'USD'];

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

function formatoFecha(fecha: Date): string {
  const anio = fecha.getFullYear();
  const mes = String(fecha.getMonth() + 1).padStart(2, '0');
  const dia = String(fecha.getDate()).padStart(2, '0');
  return `${anio}-${mes}-${dia}`;
}

@Component({
  selector: 'app-nota-venta-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, MatButtonModule, MatIconModule, MatCardModule, MonedaPipe],
  templateUrl: './nota-venta-form.component.html',
  styleUrl: './nota-venta-form.component.scss',
})
export class NotaVentaFormComponent implements OnInit, OnDestroy {
  private readonly LIMITE_RESULTADOS = 8;

  readonly monedas = MONEDAS;

  notaVentaId: number | null = null;
  clienteSeleccionado: Cliente | null = null;
  formasPago: FormaPago[] = [];
  formaPagoId: number | null = null;
  fechaEmision = formatoFecha(new Date());
  fechaEntregaEstimada = '';
  moneda = 'CLP';
  exenta = false;
  descuento = 0;
  direccionEntrega = '';
  condicionesVenta = '';
  observaciones = '';
  items: NotaVentaItem[] = [];

  filtroCliente = '';
  clientesResultados: Cliente[] = [];
  filtroProducto = '';
  productosResultados: Producto[] = [];
  itemStaged: ItemStaged = itemVacio();
  itemError: string | null = null;

  cargando = false;
  guardando = false;
  error = '';

  private readonly productosConocidos = new Map<number, Producto>();
  private readonly busquedaCliente$ = new Subject<string>();
  private readonly busquedaProducto$ = new Subject<string>();

  constructor(
    private notaVentaService: NotaVentaService,
    private clienteService: ClienteService,
    private productoService: ProductoService,
    private formaPagoService: FormaPagoService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.busquedaCliente$.pipe(debounceTime(300), distinctUntilChanged()).subscribe((texto) => {
      if (!texto.trim()) {
        this.clientesResultados = [];
        return;
      }
      this.clienteService.listarPagina(texto, 0, this.LIMITE_RESULTADOS).subscribe((resp) => {
        this.clientesResultados = resp.contenido;
      });
    });

    this.busquedaProducto$.pipe(debounceTime(300), distinctUntilChanged()).subscribe((texto) => {
      if (!texto.trim()) {
        this.productosResultados = [];
        return;
      }
      this.productoService.listarPagina(texto, 0, this.LIMITE_RESULTADOS).subscribe((resp) => {
        this.productosResultados = resp.contenido;
        resp.contenido.forEach((p) => this.productosConocidos.set(p.id, p));
      });
    });

    this.formaPagoService.listar().subscribe((data) => (this.formasPago = data));

    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.notaVentaId = Number(id);
      this.cargarNotaVenta(this.notaVentaId);
    }
  }

  ngOnDestroy(): void {
    this.busquedaCliente$.complete();
    this.busquedaProducto$.complete();
  }

  get esEdicion(): boolean {
    return this.notaVentaId !== null;
  }

  onBusquedaClienteChange(): void {
    this.busquedaCliente$.next(this.filtroCliente);
  }

  seleccionarCliente(cliente: Cliente): void {
    this.clienteSeleccionado = cliente;
    this.filtroCliente = '';
    this.clientesResultados = [];
  }

  cambiarCliente(): void {
    this.clienteSeleccionado = null;
  }

  onBusquedaProductoChange(): void {
    this.busquedaProducto$.next(this.filtroProducto);
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
    this.filtroProducto = '';
    this.productosResultados = [];
  }

  stagePrimeroFiltrado(): void {
    const primero = this.productosResultados[0];
    if (primero) this.stageProducto(primero);
  }

  confirmarStaged(): void {
    if (!this.itemStaged.productoId) return;
    this.itemError = this.agregarLinea(
      this.itemStaged.productoId,
      this.itemStaged.cantidad,
      this.itemStaged.precio,
      this.itemStaged.descuento
    );
    if (!this.itemError) {
      this.itemStaged = itemVacio();
    }
  }

  editarItem(indice: number): void {
    const item = this.items[indice];
    this.itemStaged = {
      productoId: item.productoId,
      descripcion: this.nombreProducto(item.productoId),
      precio: item.precioUnitario,
      cantidad: item.cantidad,
      descuento: item.descuento,
    };
    this.items.splice(indice, 1);
  }

  quitarItem(indice: number): void {
    this.items.splice(indice, 1);
  }

  nombreProducto(id: number): string {
    return this.productosConocidos.get(id)?.nombre ?? String(id);
  }

  skuProducto(id: number): string {
    return this.productosConocidos.get(id)?.sku ?? '—';
  }

  totalLinea(item: NotaVentaItem): number {
    return item.cantidad * item.precioUnitario - item.descuento;
  }

  get subtotalActual(): number {
    return this.items.reduce((acc, it) => acc + it.cantidad * it.precioUnitario, 0);
  }

  get descuentoLineas(): number {
    return this.items.reduce((acc, it) => acc + (it.descuento || 0), 0);
  }

  get descuentoTotal(): number {
    return this.descuentoLineas + (this.descuento || 0);
  }

  get netoActual(): number {
    return Math.max(this.subtotalActual - this.descuentoTotal, 0);
  }

  get ivaActual(): number {
    return this.exenta ? 0 : Math.round(this.netoActual * TASA_IVA);
  }

  get totalActual(): number {
    return this.netoActual + this.ivaActual;
  }

  get puedeGuardar(): boolean {
    return !this.guardando && !!this.clienteSeleccionado && this.items.length > 0;
  }

  guardar(): void {
    if (!this.puedeGuardar) return;
    if (this.fechaEntregaEstimada && this.fechaEntregaEstimada < this.fechaEmision) {
      this.error = 'La fecha de entrega estimada no puede ser anterior a la fecha de emisión.';
      return;
    }
    this.guardando = true;
    this.error = '';
    mostrarCargando(this.esEdicion ? 'Guardando nota de venta' : 'Creando nota de venta');

    const request = {
      clienteId: this.clienteSeleccionado!.id,
      formaPagoId: this.formaPagoId,
      fechaEmision: this.fechaEmision,
      fechaEntregaEstimada: this.fechaEntregaEstimada || null,
      direccionEntrega: this.direccionEntrega || undefined,
      condicionesVenta: this.condicionesVenta || undefined,
      observaciones: this.observaciones || undefined,
      exenta: this.exenta,
      moneda: this.moneda,
      descuento: this.descuento || 0,
      items: this.items,
    };

    const peticion = this.esEdicion
      ? this.notaVentaService.actualizar(this.notaVentaId!, request)
      : this.notaVentaService.crear(request);

    peticion.subscribe({
      next: (nota) => {
        cerrarCargando().then(() => this.router.navigate(['/notas-venta', nota.id]));
        this.guardando = false;
      },
      error: (err) => {
        cerrarCargando();
        this.error = err?.error?.error ?? 'No se pudo guardar la nota de venta.';
        this.guardando = false;
      },
    });
  }

  // Devuelve el mensaje de error o null si la línea es válida. Centralizado
  // para que el staging y una futura carga masiva validen igual.
  private agregarLinea(productoId: number, cantidad: number, precio: number, descuento: number): string | null {
    if (!cantidad || cantidad <= 0) {
      return 'La cantidad debe ser mayor que 0.';
    }
    if (precio < 0) {
      return 'El precio no puede ser negativo.';
    }
    const subtotalBruto = cantidad * precio;
    if (descuento < 0) {
      return 'El descuento no puede ser negativo.';
    }
    if (descuento > subtotalBruto) {
      return 'El descuento no puede superar el subtotal de la línea.';
    }

    const existente = this.items.find((it) => it.productoId === productoId);
    if (existente) {
      existente.cantidad += cantidad;
      existente.precioUnitario = precio;
      existente.descuento += descuento;
    } else {
      this.items.push({ productoId, cantidad, precioUnitario: precio, descuento });
    }
    return null;
  }

  private cargarNotaVenta(id: number): void {
    this.cargando = true;
    this.notaVentaService.obtener(id).subscribe({
      next: (nota) => {
        this.formaPagoId = nota.formaPagoId;
        this.fechaEmision = nota.fechaEmision;
        this.fechaEntregaEstimada = nota.fechaEntregaEstimada ?? '';
        this.moneda = nota.moneda || 'CLP';
        this.exenta = nota.exenta;
        this.descuento = nota.descuento;
        this.direccionEntrega = nota.direccionEntrega ?? '';
        this.condicionesVenta = nota.condicionesVenta ?? '';
        this.observaciones = nota.observaciones ?? '';
        this.items = nota.lineas.map((l) => ({
          productoId: l.productoId,
          cantidad: l.cantidad,
          precioUnitario: l.precioUnitario,
          descuento: l.descuento,
        }));
        // El detalle trae el snapshot de código y descripción, así que la tabla
        // puede mostrarse sin volver a consultar el catálogo.
        nota.lineas.forEach((l) =>
          this.productosConocidos.set(l.productoId, {
            id: l.productoId,
            nombre: l.descripcion,
            sku: l.codigo,
          } as Producto)
        );
        this.clienteSeleccionado = {
          id: nota.clienteId,
          nombre: nota.clienteNombre,
          rut: nota.clienteRut,
          email: nota.clienteEmail,
          telefono: nota.clienteTelefono,
          direccion: nota.clienteDireccion,
        } as Cliente;
        this.cargando = false;
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'No se pudo cargar la nota de venta.';
        this.cargando = false;
      },
    });
  }
}