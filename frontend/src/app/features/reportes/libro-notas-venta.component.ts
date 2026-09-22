import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { EstadoNotaVenta, LibroNotasVentaResponse } from '../../core/models/models';
import { ReporteService } from '../../core/services/reporte.service';
import { MonedaPipe } from '../../core/pipes/moneda.pipe';
import { ESTADOS, ETIQUETAS_ESTADO, TAGS_ESTADO } from '../notas-venta/estado-nota-venta';

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
  selector: 'app-libro-notas-venta',
  standalone: true,
  imports: [CommonModule, FormsModule, MatButtonModule, MatIconModule, MatCardModule, MonedaPipe],
  templateUrl: './libro-notas-venta.component.html',
  styleUrl: './libro-notas-venta.component.scss',
})
export class LibroNotasVentaComponent implements OnInit {
  readonly estados = ESTADOS;

  desde = primerDiaDelMes();
  hasta = formatoFecha(new Date());
  estado: EstadoNotaVenta | null = null;
  libro: LibroNotasVentaResponse | null = null;
  cargando = false;
  exportando = false;
  error = '';

  constructor(private reporteService: ReporteService) {}

  ngOnInit(): void {
    this.consultar();
  }

  consultar(): void {
    if (this.desde > this.hasta) {
      this.error = 'La fecha "desde" no puede ser posterior a "hasta".';
      return;
    }
    this.error = '';
    this.cargando = true;
    this.reporteService.libroNotasVenta(this.desde, this.hasta, this.estado).subscribe({
      next: (libro) => {
        this.libro = libro;
        this.cargando = false;
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'No se pudo generar el libro de notas de venta.';
        this.cargando = false;
      },
    });
  }

  exportarExcel(): void {
    if (!this.libro) return;
    const { desde, hasta, estado } = this.libro;
    this.exportando = true;
    this.reporteService.libroNotasVentaExcel(desde, hasta, estado).subscribe({
      next: (blob) => {
        const sufijo = estado ? `-${estado.toLowerCase()}` : '';
        descargarBlob(blob, `libro-notas-venta${sufijo}-${desde}-a-${hasta}.xlsx`);
        this.exportando = false;
      },
      error: () => {
        this.error = 'No se pudo exportar el Excel.';
        this.exportando = false;
      },
    });
  }

  etiquetaEstado(estado: EstadoNotaVenta): string {
    return ETIQUETAS_ESTADO[estado];
  }

  tagEstado(estado: EstadoNotaVenta): string {
    return TAGS_ESTADO[estado];
  }
}