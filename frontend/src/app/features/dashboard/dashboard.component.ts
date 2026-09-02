import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { DashboardService } from '../../core/services/dashboard.service';
import { AuthService } from '../../core/services/auth.service';
import { AlertaDashboard, DashboardResponse, PuntoVenta, TipoDocumentoVenta } from '../../core/models/models';

interface Barra {
  x: number;
  y: number;
  width: number;
  height: number;
  etiqueta: string;
  monto: number;
}

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
  imports: [CommonModule, RouterLink, MatCardModule, MatButtonModule, MatIconModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit {
  readonly rangos = RANGOS;
  rangoActivo = 'mes';

  datos: DashboardResponse | null = null;
  puntos: PuntoVenta[] = [];
  cargando = true;
  cargandoGrafico = true;

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
        this.puntos = data;
        this.cargandoGrafico = false;
      },
      error: () => (this.cargandoGrafico = false),
    });
  }

  get barras(): Barra[] {
    if (!this.puntos.length) return [];
    const ancho = 600;
    const alto = 180;
    const gap = this.puntos.length > 1 ? 8 : 0;
    const anchoBarra = (ancho - gap * (this.puntos.length - 1)) / this.puntos.length;
    const max = Math.max(...this.puntos.map((p) => p.monto), 1);
    return this.puntos.map((p, i) => {
      const alturaBarra = max === 0 ? 0 : (p.monto / max) * (alto - 20);
      return {
        x: i * (anchoBarra + gap),
        y: alto - alturaBarra,
        width: anchoBarra,
        height: alturaBarra,
        etiqueta: p.etiqueta,
        monto: p.monto,
      };
    });
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
