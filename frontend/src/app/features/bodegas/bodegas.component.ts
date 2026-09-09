import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { BodegaService, BodegaRequest } from '../../core/services/bodega.service';
import { StockService } from '../../core/services/stock.service';
import { AuthService } from '../../core/services/auth.service';
import { Bodega, InventarioItem, TipoBodega } from '../../core/models/models';
import { cerrarCargando, mostrarCargando } from '../../core/utils/swal-loading';

const ETIQUETAS_TIPO: Record<TipoBodega, string> = {
  PRINCIPAL: 'Principal',
  VENTAS: 'Ventas',
  BODEGAJE: 'Bodegaje',
  MIXTA: 'Mixta',
};

const TAGS_TIPO: Record<TipoBodega, string> = {
  PRINCIPAL: 'tag--info',
  VENTAS: 'tag--success',
  BODEGAJE: 'tag--warning',
  MIXTA: '',
};

@Component({
  selector: 'app-bodegas',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatTableModule,
    MatPaginatorModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
  ],
  templateUrl: './bodegas.component.html',
  styleUrl: './bodegas.component.scss',
})
export class BodegasComponent implements OnInit, OnDestroy {
  columnasBodegas = ['nombre', 'tipo', 'acciones'];
  columnasInventario = ['sku', 'nombre', 'cantidad'];
  tipos: TipoBodega[] = ['PRINCIPAL', 'VENTAS', 'BODEGAJE', 'MIXTA'];

  bodegas: Bodega[] = [];
  totalBodegas = 0;
  filtroBodegas = '';
  paginaBodegas = 0;
  tamanoBodegas = 10;
  readonly opcionesTamano = [10, 25, 50];

  inventario: InventarioItem[] = [];
  totalInventario = 0;
  filtroProducto = '';
  paginaInventario = 0;
  tamanoInventario = 10;

  bodegaSeleccionada: Bodega | null = null;
  error = '';
  guardando = false;
  editandoId: number | null = null;
  nombre = '';
  tipo: TipoBodega = 'BODEGAJE';

  private readonly busquedaBodegas$ = new Subject<string>();
  private readonly busquedaInventario$ = new Subject<string>();

  constructor(
    private bodegaService: BodegaService,
    private stockService: StockService,
    public auth: AuthService
  ) {}

  ngOnInit(): void {
    this.busquedaBodegas$.pipe(debounceTime(300), distinctUntilChanged()).subscribe(() => {
      this.paginaBodegas = 0;
      this.cargar();
    });
    this.busquedaInventario$.pipe(debounceTime(300), distinctUntilChanged()).subscribe(() => {
      this.paginaInventario = 0;
      this.cargarInventario();
    });
    this.cargar();
  }

  ngOnDestroy(): void {
    this.busquedaBodegas$.complete();
    this.busquedaInventario$.complete();
  }

  cargar(): void {
    this.bodegaService.listarPagina(this.filtroBodegas, this.paginaBodegas, this.tamanoBodegas).subscribe((resp) => {
      if (!resp.contenido.length && this.paginaBodegas > 0) {
        this.paginaBodegas = Math.max(0, this.paginaBodegas - 1);
        this.cargar();
        return;
      }
      this.bodegas = resp.contenido;
      this.totalBodegas = resp.total;
    });
  }

  onFiltroBodegasChange(): void {
    this.busquedaBodegas$.next(this.filtroBodegas);
  }

  onPageBodegasChange(event: PageEvent): void {
    this.paginaBodegas = event.pageIndex;
    this.tamanoBodegas = event.pageSize;
    this.cargar();
  }

  cargarInventario(): void {
    if (!this.bodegaSeleccionada) return;
    this.stockService
      .inventarioPorBodegaPagina(
        this.bodegaSeleccionada.id,
        this.filtroProducto,
        this.paginaInventario,
        this.tamanoInventario
      )
      .subscribe((resp) => {
        if (!resp.contenido.length && this.paginaInventario > 0) {
          this.paginaInventario = Math.max(0, this.paginaInventario - 1);
          this.cargarInventario();
          return;
        }
        this.inventario = resp.contenido;
        this.totalInventario = resp.total;
      });
  }

  onFiltroProductoChange(): void {
    this.busquedaInventario$.next(this.filtroProducto);
  }

  onPageInventarioChange(event: PageEvent): void {
    this.paginaInventario = event.pageIndex;
    this.tamanoInventario = event.pageSize;
    this.cargarInventario();
  }

  seleccionar(bodega: Bodega): void {
    this.bodegaSeleccionada = bodega;
    this.filtroProducto = '';
    this.paginaInventario = 0;
    this.cargarInventario();
  }

  etiquetaTipo(tipo: TipoBodega): string {
    return ETIQUETAS_TIPO[tipo];
  }

  tagTipo(tipo: TipoBodega): string {
    return TAGS_TIPO[tipo];
  }

  editar(bodega: Bodega): void {
    this.editandoId = bodega.id;
    this.nombre = bodega.nombre;
    this.tipo = bodega.tipo;
  }

  cancelarEdicion(): void {
    this.editandoId = null;
    this.nombre = '';
    this.tipo = 'BODEGAJE';
  }

  guardar(): void {
    if (!this.nombre.trim()) return;
    const request: BodegaRequest = { nombre: this.nombre.trim(), tipo: this.tipo };
    this.guardando = true;
    mostrarCargando(this.editandoId ? 'Guardando cambios' : 'Creando bodega');
    const obs = this.editandoId
      ? this.bodegaService.actualizar(this.editandoId, request)
      : this.bodegaService.crear(request);
    obs.subscribe({
      next: () => {
        cerrarCargando();
        this.error = '';
        this.guardando = false;
        this.editandoId = null;
        this.nombre = '';
        this.tipo = 'BODEGAJE';
        this.cargar();
      },
      error: (err) => {
        cerrarCargando();
        this.guardando = false;
        this.error = err?.error?.error ?? 'Ocurrió un error. Intenta nuevamente.';
      },
    });
  }

  marcarPrincipal(bodega: Bodega): void {
    this.bodegaService.marcarPrincipal(bodega.id).subscribe({
      next: () => {
        this.error = '';
        this.cargar();
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'Ocurrió un error. Intenta nuevamente.';
      },
    });
  }

  eliminar(bodega: Bodega): void {
    this.bodegaService.eliminar(bodega.id).subscribe({
      next: () => {
        this.error = '';
        if (this.editandoId === bodega.id) this.cancelarEdicion();
        if (this.bodegaSeleccionada?.id === bodega.id) {
          this.bodegaSeleccionada = null;
          this.inventario = [];
          this.totalInventario = 0;
        }
        this.cargar();
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'Ocurrió un error. Intenta nuevamente.';
      },
    });
  }
}
