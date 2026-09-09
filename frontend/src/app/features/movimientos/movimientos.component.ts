import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { Bodega, MovimientoItem, Producto, TipoMovimiento, UsuarioBasico } from '../../core/models/models';
import { BodegaService } from '../../core/services/bodega.service';
import { ProductoService } from '../../core/services/producto.service';
import { ImportItemResuelto, ImportResultado, MovimientoService } from '../../core/services/movimiento.service';
import { AuthService } from '../../core/services/auth.service';
import { UsuarioService } from '../../core/services/usuario.service';
import { ProductoRapidoDialogComponent, ProductoRapidoDialogData } from './producto-rapido-dialog.component';
import { cerrarCargando, mostrarCargando } from '../../core/utils/swal-loading';

@Component({
  selector: 'app-movimientos',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, MatButtonModule, MatIconModule, MatCardModule, MatDialogModule],
  templateUrl: './movimientos.component.html',
  styleUrl: './movimientos.component.scss',
})
export class MovimientosComponent implements OnInit, OnDestroy {
  bodegas: Bodega[] = [];
  usuarios: UsuarioBasico[] = [];
  productosResultados: Producto[] = [];
  productosTotal = 0;
  // Map ligero (no Producto completo) para poder registrar también los productos
  // resueltos por la carga masiva de Excel, que solo trae nombre/sku, no el producto entero.
  private readonly productosConocidos = new Map<number, { nombre: string; sku: string | null }>();
  private readonly busquedaProducto$ = new Subject<string>();
  private busquedaEnCurso = false;

  tipo: TipoMovimiento = 'ENTRADA';
  bodegaOrigenId: number | null = null;
  bodegaDestinoId: number | null = null;
  observacion = '';
  responsableId: number | null = null;
  items: MovimientoItem[] = [];
  guardando = false;
  mensaje = '';
  error = '';

  filtroProducto = '';

  resultadoImportacion: ImportResultado | null = null;
  importando = false;

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
    private usuarioService: UsuarioService,
    private dialog: MatDialog,
    public auth: AuthService
  ) {}

  ngOnInit(): void {
    this.bodegaService.listar().subscribe((data) => (this.bodegas = data));
    this.usuarioService.listarBasico().subscribe((data) => (this.usuarios = data));
    this.responsableId = this.auth.session()?.usuarioId ?? null;
    this.busquedaProducto$.pipe(debounceTime(300), distinctUntilChanged()).subscribe((q) => this.buscarProductos(q));
  }

  ngOnDestroy(): void {
    this.busquedaProducto$.complete();
  }

  private buscarProductos(q: string): void {
    const texto = q.trim();
    if (!texto) {
      this.productosResultados = [];
      this.productosTotal = 0;
      this.busquedaEnCurso = false;
      return;
    }
    this.productoService.listarPagina(texto, 0, 8).subscribe((resp) => {
      this.productosResultados = resp.contenido;
      this.productosTotal = resp.total;
      resp.contenido.forEach((p) => this.productosConocidos.set(p.id, p));
      this.busquedaEnCurso = false;
    });
  }

  onFiltroProductoChange(): void {
    this.busquedaEnCurso = true;
    this.busquedaProducto$.next(this.filtroProducto);
  }

  get sinResultados(): boolean {
    return this.filtroProducto.trim().length > 0 && !this.busquedaEnCurso && this.productosResultados.length === 0;
  }

  seleccionarTipo(t: TipoMovimiento): void {
    this.tipo = t;
    this.bodegaOrigenId = null;
    this.bodegaDestinoId = null;
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
    this.productosConocidos.set(producto.id, producto);
    const existente = this.items.find((it) => it.productoId === producto.id);
    if (existente) {
      existente.cantidad += 1;
    } else {
      this.items.push({ productoId: producto.id, cantidad: 1 });
    }
    this.filtroProducto = '';
    this.productosResultados = [];
    this.busquedaProducto$.next('');
  }

  seleccionarPrimero(): void {
    const primero = this.productosResultados[0];
    if (primero) {
      this.seleccionarProducto(primero);
      return;
    }
    const texto = this.filtroProducto.trim();
    if (!texto) return;
    this.productoService.listarPagina(texto, 0, 8).subscribe((resp) => {
      resp.contenido.forEach((p) => this.productosConocidos.set(p.id, p));
      if (resp.contenido[0]) {
        this.seleccionarProducto(resp.contenido[0]);
      } else {
        this.productosResultados = [];
        this.productosTotal = resp.total;
        this.busquedaEnCurso = false;
      }
    });
  }

  abrirCreacionRapida(): void {
    const data: ProductoRapidoDialogData = { textoBusqueda: this.filtroProducto };
    this.dialog
      .open(ProductoRapidoDialogComponent, { data })
      .afterClosed()
      .subscribe((producto) => {
        if (!producto) return;
        this.seleccionarProducto(producto);
      });
  }

  actualizarCantidad(index: number, valor: number): void {
    if (!valor || valor <= 0) return;
    this.items[index].cantidad = valor;
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

  get totalUnidades(): number {
    return this.items.reduce((acc, it) => acc + it.cantidad, 0);
  }

  get puedeConfirmar(): boolean {
    if (this.guardando || !this.items.length || !this.responsableId) return false;
    if (this.mostrarOrigen && !this.bodegaOrigenId) return false;
    if (this.mostrarDestino && !this.bodegaDestinoId) return false;
    return true;
  }

  confirmar(): void {
    if (!this.puedeConfirmar) return;
    this.guardando = true;
    this.error = '';
    this.mensaje = '';
    mostrarCargando('Registrando movimiento');

    this.movimientoService
      .crear({
        tipo: this.tipo,
        bodegaOrigenId: this.bodegaOrigenId,
        bodegaDestinoId: this.bodegaDestinoId,
        observacion: this.observacion,
        items: this.items,
        responsableId: this.responsableId,
      })
      .subscribe({
        next: () => {
          cerrarCargando();
          this.mensaje = 'Movimiento registrado correctamente.';
          this.items = [];
          this.observacion = '';
          this.bodegaOrigenId = null;
          this.bodegaDestinoId = null;
          this.guardando = false;
        },
        error: (err) => {
          cerrarCargando();
          this.error = err?.error?.error ?? 'Error al registrar el movimiento.';
          this.guardando = false;
        },
      });
  }

  onArchivoExcelSeleccionado(event: Event): void {
    const input = event.target as HTMLInputElement;
    const archivo = input.files?.[0];
    input.value = '';
    if (!archivo) return;
    this.importando = true;
    this.resultadoImportacion = null;
    mostrarCargando('Importando archivo');
    this.movimientoService.importarExcel(archivo).subscribe({
      next: (resultado) => {
        cerrarCargando();
        this.resultadoImportacion = resultado;
        this.importando = false;
        this.agregarItemsImportados(resultado.items);
      },
      error: (err) => {
        cerrarCargando();
        this.error = err?.error?.error ?? 'No se pudo importar el archivo.';
        this.importando = false;
      },
    });
  }

  // Suma cada fila resuelta del Excel al detalle de la operación en curso: mismo
  // criterio "sumar cantidad si el producto ya está en la lista" que el buscador manual.
  private agregarItemsImportados(itemsResueltos: ImportItemResuelto[]): void {
    for (const item of itemsResueltos) {
      this.productosConocidos.set(item.productoId, { nombre: item.productoNombre, sku: item.productoSku });
      const existente = this.items.find((it) => it.productoId === item.productoId);
      if (existente) {
        existente.cantidad += item.cantidad;
      } else {
        this.items.push({ productoId: item.productoId, cantidad: item.cantidad });
      }
    }
  }
}
