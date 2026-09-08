import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { LibroVentasResponse } from '../../core/models/models';
import { ReporteService } from '../../core/services/reporte.service';
import { MonedaPipe } from '../../core/pipes/moneda.pipe';

function formatoFecha(fecha: Date): string {
  const anio = fecha.getFullYear();
  const mes = String(fecha.getMonth() + 1).padStart(2, '0');
  const dia = String(fecha.getDate()).padStart(2, '0');
  return `${anio}-${mes}-${dia}`;
}

function primerDiaDelMes(): string {
  const hoy = new Date();
  return formatoFecha(new Date(hoy.getFullYear(), hoy.getMonth(), 1));
}

function descargarBlob(blob: Blob, nombreArchivo: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = nombreArchivo;
  anchor.click();
  URL.revokeObjectURL(url);
}

@Component({
  selector: 'app-libro-ventas',
  standalone: true,
  imports: [CommonModule, FormsModule, MatButtonModule, MatIconModule, MatCardModule, MatPaginatorModule, MonedaPipe],
  templateUrl: './libro-ventas.component.html',
  styleUrl: './libro-ventas.component.scss',
})
export class LibroVentasComponent implements OnInit, OnDestroy {
  readonly tiposDocumento: string[] = ['Factura', 'Factura Exenta', 'Boleta', 'Boleta Exenta', 'Voucher'];
  readonly opcionesTamano = [10, 25, 50];

  desde = primerDiaDelMes();
  hasta = formatoFecha(new Date());
  tipoDocumento: string | null = null;
  busqueda = '';
  paginaActual = 0;
  tamanoPagina = 10;
  libro: LibroVentasResponse | null = null;
  cargando = false;
  exportando = false;
  error = '';

  private readonly busqueda$ = new Subject<string>();

  constructor(private reporteService: ReporteService) {}

  ngOnInit(): void {
    this.busqueda$.pipe(debounceTime(300), distinctUntilChanged()).subscribe(() => {
      if (this.desde > this.hasta) return;
      this.error = '';
      this.paginaActual = 0;
      this.cargar();
    });
    this.consultar();
  }

  ngOnDestroy(): void {
    this.busqueda$.complete();
  }

  consultar(): void {
    if (this.desde > this.hasta) {
      this.error = 'La fecha "desde" no puede ser posterior a "hasta".';
      return;
    }
    this.error = '';
    this.paginaActual = 0;
    this.cargar();
  }

  onBusquedaChange(): void {
    this.busqueda$.next(this.busqueda);
  }

  onPageChange(event: PageEvent): void {
    this.paginaActual = event.pageIndex;
    this.tamanoPagina = event.pageSize;
    this.cargar();
  }

  private cargar(): void {
    this.cargando = true;
    this.reporteService
      .libroVentas(this.desde, this.hasta, this.tipoDocumento, this.busqueda, this.paginaActual, this.tamanoPagina)
      .subscribe({
        next: (libro) => {
          this.libro = libro;
          this.cargando = false;
        },
        error: (err) => {
          this.error = err?.error?.error ?? 'No se pudo generar el libro de ventas.';
          this.cargando = false;
        },
      });
  }

  exportarExcel(): void {
    if (!this.libro) return;
    const { desde, hasta, tipoDocumento, busqueda } = this.libro;
    this.exportando = true;
    this.reporteService.libroVentasExcel(desde, hasta, tipoDocumento, busqueda).subscribe({
      next: (blob) => {
        const sufijoTipo = tipoDocumento ? `-${tipoDocumento.toLowerCase().replace(/\s+/g, '-')}` : '';
        descargarBlob(blob, `libro-ventas${sufijoTipo}-${desde}-a-${hasta}.xlsx`);
        this.exportando = false;
      },
      error: () => {
        this.error = 'No se pudo exportar el Excel.';
        this.exportando = false;
      },
    });
  }
}
