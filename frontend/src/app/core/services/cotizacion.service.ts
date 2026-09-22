import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Cotizacion,
  CotizacionItem,
  CotizacionResumen,
  DashboardCotizaciones,
  EstadoCotizacion,
  PaginaResponse,
} from '../models/models';

export interface CotizacionRequest {
  clienteId: number;
  formaPagoId: number | null;
  fechaEmision: string;
  fechaVencimiento: string;
  exenta: boolean;
  descuento: number;
  condicionesComerciales?: string;
  observaciones?: string;
  items: CotizacionItem[];
}

export interface FiltrosCotizacion {
  estado?: EstadoCotizacion | null;
  clienteId?: number | null;
  vendedorId?: number | null;
  desde?: string | null;
  hasta?: string | null;
  q?: string | null;
  sort?: string;
  dir?: string;
  pagina?: number;
  tamano?: number;
}

@Injectable({ providedIn: 'root' })
export class CotizacionService {
  private readonly base = `${environment.apiUrl}/cotizaciones`;

  constructor(private http: HttpClient) {}

  listar(filtros: FiltrosCotizacion): Observable<PaginaResponse<CotizacionResumen>> {
    let params = new HttpParams()
      .set('pagina', filtros.pagina ?? 0)
      .set('tamano', filtros.tamano ?? 10);
    if (filtros.estado) params = params.set('estado', filtros.estado);
    if (filtros.clienteId) params = params.set('clienteId', filtros.clienteId);
    if (filtros.vendedorId) params = params.set('vendedorId', filtros.vendedorId);
    if (filtros.desde) params = params.set('desde', filtros.desde);
    if (filtros.hasta) params = params.set('hasta', filtros.hasta);
    if (filtros.q) params = params.set('q', filtros.q);
    if (filtros.sort) params = params.set('sort', filtros.sort);
    if (filtros.dir) params = params.set('dir', filtros.dir);
    return this.http.get<PaginaResponse<CotizacionResumen>>(this.base, { params });
  }

  dashboard(desde: string, hasta: string): Observable<DashboardCotizaciones> {
    return this.http.get<DashboardCotizaciones>(`${this.base}/dashboard`, { params: { desde, hasta } });
  }

  obtener(id: number): Observable<Cotizacion> {
    return this.http.get<Cotizacion>(`${this.base}/${id}`);
  }

  crear(request: CotizacionRequest): Observable<Cotizacion> {
    return this.http.post<Cotizacion>(this.base, request);
  }

  actualizar(id: number, request: CotizacionRequest): Observable<Cotizacion> {
    return this.http.put<Cotizacion>(`${this.base}/${id}`, request);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  enviar(id: number): Observable<Cotizacion> {
    return this.http.post<Cotizacion>(`${this.base}/${id}/enviar`, {});
  }

  aceptar(id: number): Observable<Cotizacion> {
    return this.http.post<Cotizacion>(`${this.base}/${id}/aceptar`, {});
  }

  rechazar(id: number, motivo: string | null): Observable<Cotizacion> {
    return this.http.post<Cotizacion>(`${this.base}/${id}/rechazar`, { motivo });
  }

  cancelar(id: number, motivo: string | null): Observable<Cotizacion> {
    return this.http.post<Cotizacion>(`${this.base}/${id}/cancelar`, { motivo });
  }

  duplicar(id: number): Observable<Cotizacion> {
    return this.http.post<Cotizacion>(`${this.base}/${id}/duplicar`, {});
  }

  obtenerPdf(id: number): Observable<Blob> {
    return this.http.get(`${this.base}/${id}/pdf`, { responseType: 'blob' });
  }
}
