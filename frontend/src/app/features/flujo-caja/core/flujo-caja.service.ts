import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

// --- DTOs del endpoint /api/flujo-caja (espejo de cl.slimerp.flujocaja) ---

export interface GastoCategoriaTotal {
  categoriaGastoId: number | null;
  categoria: string;
  total: number;
}

export interface MesResumen {
  /** `YYYY-MM`. */
  mes: string;
  ingresos: number;
  compras: number;
  gastos: number;
  resultado: number;
  /** Saldo acumulado (caja real) al cierre del mes. */
  saldo: number;
  gastosPorCategoria: GastoCategoriaTotal[];
}

export interface ResumenAnio {
  anio: number;
  meses: MesResumen[];
}

export interface LineaIngreso {
  pagoId: number;
  cuentaPorCobrarId: number;
  fecha: string;
  descripcion: string;
  monto: number;
}

export interface LineaCompra {
  pagoId: number;
  cuentaPorPagarId: number;
  fecha: string;
  descripcion: string;
  monto: number;
}

export interface LineaGasto {
  pagoId: number;
  cuentaPorPagarId: number;
  fecha: string;
  descripcion: string;
  monto: number;
}

export interface GastoCategoriaDetalle {
  categoriaGastoId: number | null;
  categoria: string;
  total: number;
  lineas: LineaGasto[];
}

export interface DetalleMes {
  /** `YYYY-MM`. */
  mes: string;
  ingresos: number;
  compras: number;
  gastos: number;
  resultado: number;
  saldo: number;
  ingresosDetalle: LineaIngreso[];
  comprasDetalle: LineaCompra[];
  gastosPorCategoria: GastoCategoriaDetalle[];
}

/**
 * Flujo de caja calculado íntegramente en el backend a partir de Tesorería:
 * ingresos = cobros confirmados (CxC) y egresos = pagos confirmados (CxP),
 * con los gastos agrupados por categoría.
 */
@Injectable({ providedIn: 'root' })
export class FlujoCajaService {
  private readonly base = `${environment.apiUrl}/flujo-caja`;

  constructor(private readonly http: HttpClient) {}

  resumenAnio(anio: number): Observable<ResumenAnio> {
    return this.http.get<ResumenAnio>(`${this.base}/anio`, { params: { anio: String(anio) } });
  }

  detalleMes(mes: string): Observable<DetalleMes> {
    return this.http.get<DetalleMes>(`${this.base}/mes`, { params: { mes } });
  }
}