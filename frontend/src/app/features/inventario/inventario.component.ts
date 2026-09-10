import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { InventarioService } from '../../core/services/inventario.service';
import { BodegaService } from '../../core/services/bodega.service';
import { CategoriaService } from '../../core/services/categoria.service';
import { SubcategoriaService } from '../../core/services/subcategoria.service';
import { Bodega, Categoria, InventarioConsultaItem, Subcategoria, TipoBusquedaInventario } from '../../core/models/models';
import { cerrarCargando, mostrarCargando } from '../../core/utils/swal-loading';

function descargarBlob(blob: Blob, nombreArchivo: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = nombreArchivo;
  anchor.click();
  URL.revokeObjectURL(url);
}

@Component({
  selector: 'app-inventario',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatTableModule,
    MatSortModule,
    MatPaginatorModule,
    MatButtonModule,
    MatCardModule,
  ],
  templateUrl: './inventario.component.html',
  styleUrl: './inventario.component.scss',
})
export class InventarioComponent implements OnInit, OnDestroy {
  columnas = ['indice', 'nombre', 'sku', 'codigoBarra', 'stock'];
  readonly opcionesTamano = [10, 25, 50, 100];
  readonly tiposBusqueda: { value: TipoBusquedaInventario; label: string }[] = [
    { value: 'NOMBRE', label: 'Nombre' },
    { value: 'SKU', label: 'SKU' },
    { value: 'CODIGO_BARRA', label: 'Código de Barra' },
  ];

  bodegas: Bodega[] = [];
  familias: Categoria[] = [];
  subfamilias: Subcategoria[] = [];

  bodegaId: number | null = null;
  familiaId: number | null = null;
  subfamiliaId: number | null = null;

  tipoBusqueda: TipoBusquedaInventario = 'NOMBRE';
  busqueda = '';

  sort = 'nombre';
  dir: 'asc' | 'desc' = 'asc';

  items: InventarioConsultaItem[] = [];
  total = 0;
  pagina = 0;
  tamano = 10;

  cargando = false;
  error = '';

  private readonly busqueda$ = new Subject<string>();

  constructor(
    private inventarioService: InventarioService,
    private bodegaService: BodegaService,
    private categoriaService: CategoriaService,
    private subcategoriaService: SubcategoriaService
  ) {}

  ngOnInit(): void {
    this.bodegaService.listar().subscribe((data) => (this.bodegas = data));
    this.categoriaService.listar().subscribe((data) => (this.familias = data));
    this.busqueda$.pipe(debounceTime(300), distinctUntilChanged()).subscribe(() => this.onFiltroChange());
    this.cargar();
  }

  ngOnDestroy(): void {
    this.busqueda$.complete();
  }

  get desde(): number {
    return this.total === 0 ? 0 : this.pagina * this.tamano + 1;
  }

  get hasta(): number {
    return Math.min((this.pagina + 1) * this.tamano, this.total);
  }

  onFamiliaChange(): void {
    this.subfamiliaId = null;
    this.subfamilias = [];
    if (this.familiaId != null) {
      this.subcategoriaService.listar(this.familiaId).subscribe((data) => (this.subfamilias = data));
    }
    this.onFiltroChange();
  }

  onBusquedaChange(): void {
    this.busqueda$.next(this.busqueda);
  }

  onFiltroChange(): void {
    this.pagina = 0;
    this.cargar();
  }

  onSortChange(sort: Sort): void {
    this.sort = sort.direction ? sort.active : 'nombre';
    this.dir = sort.direction === 'desc' ? 'desc' : 'asc';
    this.pagina = 0;
    this.cargar();
  }

  onPageChange(event: PageEvent): void {
    this.pagina = event.pageIndex;
    this.tamano = event.pageSize;
    this.cargar();
  }

  cargar(): void {
    this.cargando = true;
    this.error = '';
    this.inventarioService
      .listar(this.filtroActual(), this.sort, this.dir, this.pagina, this.tamano)
      .subscribe({
        next: (respuesta) => {
          this.items = respuesta.contenido;
          this.total = respuesta.total;
          this.cargando = false;
        },
        error: () => {
          this.error = 'No fue posible cargar el inventario. Intente nuevamente.';
          this.cargando = false;
        },
      });
  }

  exportarCsv(): void {
    mostrarCargando('Generando CSV');
    this.inventarioService.exportarCsv(this.filtroActual()).subscribe({
      next: (blob) => {
        cerrarCargando();
        descargarBlob(blob, 'inventario.csv');
      },
      error: () => {
        cerrarCargando();
        this.error = 'No se pudo exportar el CSV.';
      },
    });
  }

  exportarXlsx(): void {
    mostrarCargando('Generando Excel');
    this.inventarioService.exportarXlsx(this.filtroActual()).subscribe({
      next: (blob) => {
        cerrarCargando();
        descargarBlob(blob, 'inventario.xlsx');
      },
      error: () => {
        cerrarCargando();
        this.error = 'No se pudo exportar el Excel.';
      },
    });
  }

  private filtroActual() {
    return {
      bodegaId: this.bodegaId,
      familiaId: this.familiaId,
      subfamiliaId: this.subfamiliaId,
      verDeshabilitados: false,
      tipoBusqueda: this.tipoBusqueda,
      busqueda: this.busqueda.trim(),
    };
  }
}
