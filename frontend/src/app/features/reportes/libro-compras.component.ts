import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { HighchartsChartModule } from 'highcharts-angular';
import * as Highcharts from 'highcharts';
import { LibroComprasFila, LibroComprasResponse } from '../../core/models/models';
import { ReporteService } from '../../core/services/reporte.service';
import { MonedaPipe } from '../../core/pipes/moneda.pipe';

const TAGS_ESTADO: Record<string, string> = {
  'En deuda': 'tag--error',
  Parcial: 'tag--warning',
  Pagado: 'tag--success',
  Anulado: '',
  '—': '',
};

const MONEDA = new Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP', maximumFractionDigits: 0 });
const NUMERO = new Intl.NumberFormat('es-CL', { maximumFractionDigits: 0 });

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
  selector: 'app-libro-compras',
  standalone: true,
  imports: [CommonModule, FormsModule, MatButtonModule, MatIconModule, MatCardModule, HighchartsChartModule, MonedaPipe],
  templateUrl: './libro-compras.component.html',
  styleUrl: './libro-compras.component.scss',
})
export class LibroComprasComponent implements OnInit {
  desde = primerDiaDelMes();
  hasta = formatoFecha(new Date());
  libro: LibroComprasResponse | null = null;
  cargando = false;
  exportando = false;
  error = '';

  readonly Highcharts: typeof Highcharts = Highcharts;
  chartOptions: Highcharts.Options = {};
  actualizarGrafico = false;

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
    this.reporteService.libroCompras(this.desde, this.hasta).subscribe({
      next: (libro) => {
        this.libro = libro;
        this.chartOptions = this.construirOpciones(libro);
        this.actualizarGrafico = true;
        this.cargando = false;
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'No se pudo generar el libro de compras.';
        this.cargando = false;
      },
    });
  }

  exportarExcel(): void {
    if (!this.libro) return;
    this.exportando = true;
    this.reporteService.libroComprasExcel(this.desde, this.hasta).subscribe({
      next: (blob) => {
        descargarBlob(blob, `libro-compras-${this.desde}-a-${this.hasta}.xlsx`);
        this.exportando = false;
      },
      error: () => {
        this.error = 'No se pudo exportar el Excel.';
        this.exportando = false;
      },
    });
  }

  get promedioPorCompra(): number {
    if (!this.libro || !this.libro.resumen.cantidadCompras) return 0;
    return this.libro.resumen.montoTotal / this.libro.resumen.cantidadCompras;
  }

  get proveedorPrincipal(): string {
    if (!this.libro || !this.libro.filas.length) return '—';
    const totalesPorProveedor = new Map<string, number>();
    for (const fila of this.libro.filas) {
      totalesPorProveedor.set(fila.proveedorNombre, (totalesPorProveedor.get(fila.proveedorNombre) ?? 0) + fila.montoTotal);
    }
    let mejor = '—';
    let mejorMonto = -1;
    for (const [nombre, monto] of totalesPorProveedor) {
      if (monto > mejorMonto) {
        mejor = nombre;
        mejorMonto = monto;
      }
    }
    return mejor;
  }

  tagEstado(fila: LibroComprasFila): string {
    return TAGS_ESTADO[fila.estadoPago] ?? '';
  }

  formatoMoneda(valor: number): string {
    return MONEDA.format(valor);
  }

  private construirOpciones(libro: LibroComprasResponse): Highcharts.Options {
    const colorPrimario = getComputedStyle(document.documentElement).getPropertyValue('--primary-base').trim() || '#2563eb';
    const colorTexto = getComputedStyle(document.documentElement).getPropertyValue('--text-muted').trim() || '#6b7280';
    const puntos = libro.evolucion;

    return {
      chart: { type: 'column', height: 240, backgroundColor: 'transparent', style: { fontFamily: 'inherit' } },
      title: { text: undefined },
      credits: { enabled: false },
      xAxis: {
        categories: puntos.map((p) => p.etiqueta),
        labels: { style: { color: colorTexto } },
        lineColor: colorTexto,
      },
      yAxis: {
        title: { text: undefined },
        labels: {
          style: { color: colorTexto },
          formatter: function (): string {
            return NUMERO.format(Number(this.value));
          },
        },
        gridLineDashStyle: 'Dash',
      },
      legend: { enabled: false },
      tooltip: {
        formatter: function (): string {
          return `${this.key}: <b>${MONEDA.format(Number(this.y))}</b>`;
        },
      },
      plotOptions: {
        column: { borderRadius: 4, color: colorPrimario, maxPointWidth: 60 },
      },
      series: [{ type: 'column', name: 'Compras', data: puntos.map((p) => p.total) }],
    };
  }
}
