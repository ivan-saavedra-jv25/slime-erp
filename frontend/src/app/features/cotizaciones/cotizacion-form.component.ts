import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { Cliente, CotizacionItem, FormaPago, Producto } from '../../core/models/models';
import { CotizacionService } from '../../core/services/cotizacion.service';
import { ClienteService } from '../../core/services/cliente.service';
import { ProductoService } from '../../core/services/producto.service';
import { FormaPagoService } from '../../core/services/forma-pago.service';
import { MonedaPipe } from '../../core/pipes/moneda.pipe';
import { cerrarCargando, mostrarCargando } from '../../core/utils/swal-loading';

const TASA_IVA = 0.19;
const DIAS_VIGENCIA_POR_DEFECTO = 30;

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
  selector: 'app-cotizacion-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, MatButtonModule, MatIconModule, MatCardModule, MonedaPipe],
  templateUrl: './cotizacion-form.component.html',
  styleUrl: './cotizacion-form.component.scss',
})
export class CotizacionFormComponent implements OnInit, OnDestroy {
  private readonly LIMITE_RESULTADOS = 8;

  cotizacionId: number | null = null;
  clienteSeleccionado: Cliente | null = null;
  formasPago: FormaPago[] = [];
  formaPagoId: number | null = null;
  fechaEmision = formatoFecha(new Date());
  fechaVencimiento = formatoFecha(new Date(Date.now() + DIAS_VIGENCIA_POR_DEFECTO * 86400000));
  exenta = false;
  descuento = 0;
  condicionesComerciales = '';
  observaciones = '';
  items: CotizacionItem[] = [];

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
    private cotizacionService: CotizacionService,
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
      this.cotizacionId = Number(id);
      this.cargarCotizacion(this.cotizacionId);
    }
  }

  ngOnDestroy(): void {
    this.busquedaCliente$.complete();
    this.busquedaProducto$.complete();
  }

  get esEdicion(): boolean {
    return this.cotizacionId !== null;
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

  totalLinea(item: CotizacionItem): number {
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
    if (this.fechaVencimiento < this.fechaEmision) {
      this.error = 'La fecha de vencimiento no puede ser anterior a la fecha de emisión.';
      return;
    }
    this.guardando = true;
    this.error = '';
    mostrarCargando(this.esEdicion ? 'Guardando cotización' : 'Creando cotización');

    const request = {
      clienteId: this.clienteSeleccionado!.id,
      formaPagoId: this.formaPagoId,
      fechaEmision: this.fechaEmision,
      fechaVencimiento: this.fechaVencimiento,
      exenta: this.exenta,
      descuento: this.descuento || 0,
      condicionesComerciales: this.condicionesComerciales || undefined,
      observaciones: this.observaciones || undefined,
      items: this.items,
    };

    const peticion = this.esEdicion
      ? this.cotizacionService.actualizar(this.cotizacionId!, request)
      : this.cotizacionService.crear(request);

    peticion.subscribe({
      next: (cotizacion) => {
        cerrarCargando().then(() => this.router.navigate(['/cotizaciones', cotizacion.id]));
        this.guardando = false;
      },
      error: (err) => {
        cerrarCargando();
        this.error = err?.error?.error ?? 'No se pudo guardar la cotización.';
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

  private cargarCotizacion(id: number): void {
    this.cargando = true;
    this.cotizacionService.obtener(id).subscribe({
      next: (cotizacion) => {
        this.formaPagoId = cotizacion.formaPagoId;
        this.fechaEmision = cotizacion.fechaEmision;
        this.fechaVencimiento = cotizacion.fechaVencimiento;
        this.exenta = cotizacion.exenta;
        this.descuento = cotizacion.descuento;
        this.condicionesComerciales = cotizacion.condicionesComerciales ?? '';
        this.observaciones = cotizacion.observaciones ?? '';
        this.items = cotizacion.lineas.map((l) => ({
          productoId: l.productoId,
          cantidad: l.cantidad,
          precioUnitario: l.precioUnitario,
          descuento: l.descuento,
        }));
        // El detalle trae el snapshot de código y descripción, así que la tabla
        // puede mostrarse sin volver a consultar el catálogo.
        cotizacion.lineas.forEach((l) =>
          this.productosConocidos.set(l.productoId, {
            id: l.productoId,
            nombre: l.descripcion,
            sku: l.codigo,
          } as Producto)
        );
        this.clienteSeleccionado = {
          id: cotizacion.clienteId,
          nombre: cotizacion.clienteNombre,
          rut: cotizacion.clienteRut,
          email: cotizacion.clienteEmail,
          telefono: cotizacion.clienteTelefono,
          direccion: cotizacion.clienteDireccion,
        } as Cliente;
        this.cargando = false;
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'No se pudo cargar la cotización.';
        this.cargando = false;
      },
    });
  }
}
