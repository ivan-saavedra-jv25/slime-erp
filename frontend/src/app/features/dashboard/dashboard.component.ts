import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { HighchartsChartModule } from 'highcharts-angular';
import * as Highcharts from 'highcharts';
import { DashboardService } from '../../core/services/dashboard.service';
import { AuthService } from '../../core/services/auth.service';
import { AlertaDashboard, DashboardResponse, PuntoVenta, TipoDocumentoVenta } from '../../core/models/models';

const ETIQUETAS_DOCUMENTO: Record<TipoDocumentoVenta, string> = {
  BOLETA: 'Boleta',
  FACTURA: 'Factura',
  VOUCHER: 'Voucher',
};

const TAGS_DOCUMENTO: Record<TipoDocumentoVenta, string> = {
  BOLETA: 'tag--success',
  FACTURA: 'tag--info',
  VOUCHER: 'tag--warning',
};

const TAGS_SEVERIDAD: Record<AlertaDashboard['severidad'], string> = {
  ALTA: 'tag--error',
  MEDIA: 'tag--warning',
};

const RANGOS: { value: string; label: string }[] = [
  { value: 'hoy', label: 'Hoy' },
  { value: '7d', label: '7 días' },
  { value: 'mes', label: 'Este mes' },
  { value: 'mes_anterior', label: 'Mes anterior' },
  { value: 'anio', label: 'Este año' },
];

const MONEDA = new Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP', maximumFractionDigits: 0 });
const NUMERO = new Intl.NumberFormat('es-CL', { maximumFractionDigits: 0 });

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, MatCardModule, MatButtonModule, MatIconModule, HighchartsChartModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit {
  readonly rangos = RANGOS;
  rangoActivo = 'mes';

  datos: DashboardResponse | null = null;
  cargando = true;
  cargandoGrafico = true;
  tieneDatosGrafico = false;

  readonly Highcharts: typeof Highcharts = Highcharts;
  chartOptions: Highcharts.Options = {};
  actualizarGrafico = false;

  constructor(
    private dashboardService: DashboardService,
    public auth: AuthService
  ) {}

  ngOnInit(): void {
    this.dashboardService.resumen().subscribe({
      next: (data) => {
        this.datos = data;
        this.cargando = false;
      },
      error: () => (this.cargando = false),
    });
    this.cargarEvolucion();
  }

  seleccionarRango(rango: string): void {
    if (this.rangoActivo === rango) return;
    this.rangoActivo = rango;
    this.cargarEvolucion();
  }

  private cargarEvolucion(): void {
    this.cargandoGrafico = true;
    this.dashboardService.ventasEvolucion(this.rangoActivo).subscribe({
      next: (data) => {
        this.tieneDatosGrafico = data.some((p) => p.monto > 0);
        this.chartOptions = this.construirOpciones(data);
        this.actualizarGrafico = true;
        this.cargandoGrafico = false;
      },
      error: () => (this.cargandoGrafico = false),
    });
  }

  private construirOpciones(puntos: PuntoVenta[]): Highcharts.Options {
    const colorPrimario = getComputedStyle(document.documentElement).getPropertyValue('--primary-base').trim() || '#2563eb';
    const colorTexto = getComputedStyle(document.documentElement).getPropertyValue('--text-muted').trim() || '#6b7280';

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
      series: [{ type: 'column', name: 'Ventas', data: puntos.map((p) => p.monto) }],
    };
  }

  formatoMoneda(valor: number): string {
    return MONEDA.format(valor);
  }

  formatoNumero(valor: number): string {
    return NUMERO.format(valor);
  }

  etiquetaDocumento(tipo: TipoDocumentoVenta): string {
    return ETIQUETAS_DOCUMENTO[tipo];
  }

  tagDocumento(tipo: TipoDocumentoVenta): string {
    return TAGS_DOCUMENTO[tipo];
  }

  tagSeveridad(severidad: AlertaDashboard['severidad']): string {
    return TAGS_SEVERIDAD[severidad];
  }
}
