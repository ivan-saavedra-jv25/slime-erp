import { Component, EventEmitter, Inject, OnDestroy, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Categoria, InventarioItem, Producto } from '../../core/models/models';
import { ProductoService } from '../../core/services/producto.service';
import { CategoriaService } from '../../core/services/categoria.service';
import { StockService } from '../../core/services/stock.service';
import { MonedaPipe } from '../../core/pipes/moneda.pipe';

export interface ProductoBuscadorDialogData {
  bodegaId: number | null;
}

@Component({
  selector: 'app-producto-buscador-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, MatDialogModule, MatButtonModule, MatIconModule, MonedaPipe],
  templateUrl: './producto-buscador-dialog.component.html',
  styleUrl: './producto-buscador-dialog.component.scss',
})
export class ProductoBuscadorDialogComponent implements OnInit, OnDestroy {
  @Output() agregar = new EventEmitter<Producto>();

  private readonly TAMANO = 10;
  private readonly categoriasPorId = new Map<number, string>();
  private readonly stockPorProducto = new Map<number, number>();
  private readonly busqueda$ = new Subject<string>();

  busqueda = '';
  productos: Producto[] = [];
  total = 0;
  pagina = 0;
  cargando = false;

  filaConFeedback: { productoId: number; error: string | null } | null = null;
  private feedbackTimeout: ReturnType<typeof setTimeout> | null = null;

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: ProductoBuscadorDialogData,
    private productoService: ProductoService,
    private categoriaService: CategoriaService,
    private stockService: StockService
  ) {}

  ngOnInit(): void {
    this.categoriaService.listar().subscribe((categorias) => {
      categorias.forEach((c) => this.categoriasPorId.set(c.id, c.nombre));
    });
    if (this.data.bodegaId) {
      this.stockService.inventarioPorBodega(this.data.bodegaId).subscribe((items: InventarioItem[]) => {
        items.forEach((i) => this.stockPorProducto.set(i.productoId, i.cantidad));
      });
    }
    this.busqueda$.pipe(debounceTime(300), distinctUntilChanged()).subscribe(() => {
      this.pagina = 0;
      this.cargar();
    });
    this.cargar();
  }

  ngOnDestroy(): void {
    this.busqueda$.complete();
    if (this.feedbackTimeout) clearTimeout(this.feedbackTimeout);
  }

  onBusquedaChange(): void {
    this.busqueda$.next(this.busqueda);
  }

  get desde(): number {
    return this.total === 0 ? 0 : this.pagina * this.TAMANO + 1;
  }

  get hasta(): number {
    return Math.min((this.pagina + 1) * this.TAMANO, this.total);
  }

  get tienePaginaAnterior(): boolean {
    return this.pagina > 0;
  }

  get tienePaginaSiguiente(): boolean {
    return this.hasta < this.total;
  }

  paginaAnterior(): void {
    if (!this.tienePaginaAnterior) return;
    this.pagina--;
    this.cargar();
  }

  paginaSiguiente(): void {
    if (!this.tienePaginaSiguiente) return;
    this.pagina++;
    this.cargar();
  }

  private cargar(): void {
    this.cargando = true;
    this.productoService.listarPagina(this.busqueda.trim(), this.pagina, this.TAMANO).subscribe((resp) => {
      this.productos = resp.contenido;
      this.total = resp.total;
      this.cargando = false;
    });
  }

  categoriaDe(producto: Producto): string {
    if (!producto.categoriaId) return '—';
    return this.categoriasPorId.get(producto.categoriaId) ?? '—';
  }

  stockDe(producto: Producto): number | null {
    if (!this.data.bodegaId) return null;
    return this.stockPorProducto.get(producto.id) ?? 0;
  }

  agregarProducto(producto: Producto): void {
    this.agregar.emit(producto);
  }

  mostrarResultado(productoId: number, error: string | null): void {
    if (this.feedbackTimeout) clearTimeout(this.feedbackTimeout);
    this.filaConFeedback = { productoId, error };
    this.feedbackTimeout = setTimeout(() => {
      this.filaConFeedback = null;
    }, 2500);
  }
}
