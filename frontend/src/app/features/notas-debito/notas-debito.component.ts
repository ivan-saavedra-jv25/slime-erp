import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSortModule, Sort } from '@angular/material/sort';
import { HighchartsChartModule } from 'highcharts-angular';
import * as Highcharts from 'highcharts';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import {
  DashboardNotasDebito,
  EstadoNotaDebito,
  ImpactoInventario,
  NotaDebitoResumen,
  TipoDocumentoVenta,
  TipoReversion,
} from '../../core/models/models';
import { NotaDebitoService } from '../../core/services/nota-debito.service';
import { AuthService } from '../../core/services/auth.service';
import { MonedaPipe } from '../../core/pipes/moneda.pipe';
import {
  ESTADOS,
  ETIQUETAS_ESTADO,
  ETIQUETAS_IMPACTO,
  ETIQUETAS_TIPO_REVERSION,
  TAGS_ESTADO,
  TAGS_IMPACTO,
  TIPOS_REVERSION,
} from './estado-nota-debito';

type Periodo = 'mes' | 'mes_anterior' | 'trimestre' | 'anio' | 'personalizado';

const PERIODOS: { value: Periodo; label: string }[] = [
  { value: 'mes', label: 'Este mes' },
  { value: 'mes_anterior', label: 'Mes anterior' },
  { value: 'trimestre', label: 'Últimos 3 meses' },
  { value: 'anio', label: 'Este año' },
  { value: 'personalizado', label: 'Personalizado' },
];

const ETIQUETAS_DOCUMENTO: Record<TipoDocumentoVenta, string> = {
  FACTURA: 'Factura',
  BOLETA: 'Boleta',
  VOUCHER: 'Voucher',
};

const NUMERO = new Intl.NumberFormat('es-CL', { maximumFractionDigits: 0 });

function formatoFecha(fecha: Date): string {
  const anio = fecha.getFullYear();
  const mes = String(fecha.getMonth() + 1).padStart(2, '0');
  const dia = String(fecha.getDate()).padStart(2, '0');
  return `${anio}-${mes}-${dia}`;
}

@Component({
  selector: 'app-notas-debito',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatPaginatorModule,
    MatSortModule,
    HighchartsChartModule,
    MonedaPipe,
  ],
  templateUrl: './notas-debito.component.html',
  styleUrl: './notas-debito.component.scss',
})
export class NotasDebitoComponent implements OnInit, OnDestroy {
  readonly estados = ESTADOS;
  readonly tiposReversion = TIPOS_REVERSION;
  readonly periodos = PERIODOS;
  readonly opcionesTamano = [10, 25, 50];

  periodoActivo: Periodo = 'mes';
  desde = '';
  hasta = '';
  estado: EstadoNotaDebito | null = null;
  tipoReversion: TipoReversion | null = null;
  busqueda = '';

  dashboard: DashboardNotasDebito | null = null;
  notas: NotaDebitoResumen[] = [];
  total = 0;
  paginaActual = 0;
  tamanoPagina = 10;
  orden = 'fecha';
  direccion: 'asc' | 'desc' = 'desc';

  cargando = false;
  error = '';

  readonly Highcharts: typeof Highcharts = Highcharts;
  chartOptions: Highcharts.Options = {};
  actualizarGrafico = false;

  private readonly busqueda$ = new Subject<string>();

  constructor(
    private notaDebitoService: NotaDebitoService,
    private router: Router,
    public auth: AuthService
  ) {}

  ngOnInit(): void {
    this.busqueda$.pipe(debounceTime(300), distinctUntilChanged()).subscribe(() => {
      this.paginaActual = 0;
      this.cargarListado();
    });
    this.aplicarPeriodo('mes');
  }

  ngOnDestroy(): void {
    this.busqueda$.complete();
  }

  seleccionarPeriodo(periodo: Periodo): void {
    if (periodo === this.periodoActivo) return;
    this.aplicarPeriodo(periodo);
  }

  consultar(): void {
    if (this.desde && this.hasta && this.desde > this.hasta) {
      this.error = 'La fecha "desde" no puede ser posterior a "hasta".';
      return;
    }
    this.error = '';
    this.paginaActual = 0;
    this.cargarDashboard();
    this.cargarListado();
  }

  onBusquedaChange(): void {
    this.busqueda$.next(this.busqueda);
  }

  onPageChange(evento: PageEvent): void {
    this.paginaActual = evento.pageIndex;
    this.tamanoPagina = evento.pageSize;
    this.cargarListado();
  }

  onSortChange(sort: Sort): void {
    this.orden = sort.active;
    this.direccion = sort.direction === 'asc' ? 'asc' : 'desc';
    this.paginaActual = 0;
    this.cargarListado();
  }

  abrir(nota: NotaDebitoResumen): void {
    this.router.navigate(['/notas-debito', nota.id]);
  }

  editar(nota: NotaDebitoResumen, evento: MouseEvent): void {
    evento.stopPropagation();
    this.router.navigate(['/notas-debito', nota.id, 'editar']);
  }

  get hayFiltrosActivos(): boolean {
    return !!(this.estado || this.tipoReversion || this.busqueda);
  }

  limpiarFiltros(): void {
    this.estado = null;
    this.tipoReversion = null;
    this.busqueda = '';
    this.consultar();
  }

  etiquetaEstado(estado: EstadoNotaDebito): string {
    return ETIQUETAS_ESTADO[estado];
  }

  tagEstado(estado: EstadoNotaDebito): string {
    return TAGS_ESTADO[estado];
  }

  etiquetaTipo(tipo: TipoReversion): string {
    return ETIQUETAS_TIPO_REVERSION[tipo];
  }

  etiquetaDocumento(tipo: TipoDocumentoVenta): string {
    return ETIQUETAS_DOCUMENTO[tipo];
  }

  etiquetaImpacto(impacto: ImpactoInventario): string {
    return ETIQUETAS_IMPACTO[impacto];
  }

  tagImpacto(impacto: ImpactoInventario): string {
    return TAGS_IMPACTO[impacto];
  }

  get tieneDatosGrafico(): boolean {
    return !!this.dashboard && this.dashboard.cantidad > 0;
  }

  private aplicarPeriodo(periodo: Periodo): void {
    this.periodoActivo = periodo;
    const hoy = new Date();

    if (periodo === 'mes') {
      this.desde = formatoFecha(new Date(hoy.getFullYear(), hoy.getMonth(), 1));
      this.hasta = formatoFecha(hoy);
    } else if (periodo === 'mes_anterior') {
      this.desde = formatoFecha(new Date(hoy.getFullYear(), hoy.getMonth() - 1, 1));
      this.hasta = formatoFecha(new Date(hoy.getFullYear(), hoy.getMonth(), 0));
    } else if (periodo === 'trimestre') {
      this.desde = formatoFecha(new Date(hoy.getFullYear(), hoy.getMonth() - 2, 1));
      this.hasta = formatoFecha(hoy);
    } else if (periodo === 'anio') {
      this.desde = formatoFecha(new Date(hoy.getFullYear(), 0, 1));
      this.hasta = formatoFecha(hoy);
    }
    // En "personalizado" se respetan las fechas que ya tiene el usuario.

    this.error = '';
    this.paginaActual = 0;
    this.cargarDashboard();
    this.cargarListado();
  }

  private cargarDashboard(): void {
    this.notaDebitoService.dashboard(this.desde, this.hasta).subscribe({
      next: (dashboard) => {
        this.dashboard = dashboard;
        this.chartOptions = this.construirOpciones(dashboard);
        this.actualizarGrafico = true;
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'No se pudieron cargar las métricas de notas de débito.';
      },
    });
  }

  private cargarListado(): void {
    this.cargando = true;
    this.notaDebitoService
      .listar({
        estado: this.estado,
        tipoReversion: this.tipoReversion,
        desde: this.desde || null,
        hasta: this.hasta || null,
        q: this.busqueda || null,
        sort: this.orden,
        dir: this.direccion,
        pagina: this.paginaActual,
        tamano: this.tamanoPagina,
      })
      .subscribe({
        next: (pagina) => {
          this.notas = pagina.contenido;
          this.total = pagina.total;
          this.cargando = false;
        },
        error: (err) => {
          this.error = err?.error?.error ?? 'No se pudieron cargar las notas de débito.';
          this.cargando = false;
        },
      });
  }

  private construirOpciones(dashboard: DashboardNotasDebito): Highcharts.Options {
    // Los colores salen de los tokens del sistema de diseño, nunca hardcodeados.
    const estilo = getComputedStyle(document.documentElement);
    const colorTexto = estilo.getPropertyValue('--text-muted').trim() || '#6b7280';
    const colorPorEstado: Record<EstadoNotaDebito, string> = {
      BORRADOR: estilo.getPropertyValue('--gray-400').trim() || '#9ca3af',
      EMITIDA: estilo.getPropertyValue('--success-base').trim() || '#16a34a',
      ANULADA: estilo.getPropertyValue('--error-base').trim() || '#dc2626',
    };

    return {
      chart: { type: 'column', height: 240, backgroundColor: 'transparent', style: { fontFamily: 'inherit' } },
      title: { text: undefined },
      credits: { enabled: false },
      xAxis: {
        categories: dashboard.porEstado.map((c) => ETIQUETAS_ESTADO[c.estado]),
        labels: { style: { color: colorTexto } },
        lineColor: colorTexto,
      },
      yAxis: {
        title: { text: undefined },
        allowDecimals: false,
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
          return `${this.key}: <b>${this.y}</b>`;
        },
      },
      plotOptions: { column: { borderRadius: 4, maxPointWidth: 60 } },
      series: [
        {
          type: 'column',
          name: 'Notas de débito',
          data: dashboard.porEstado.map((c) => ({ y: c.cantidad, color: colorPorEstado[c.estado] })),
        },
      ],
    };
  }
}