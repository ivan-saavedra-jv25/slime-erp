import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  DashboardNotasVenta,
  EstadoNotaVenta,
  NotaVenta,
  NotaVentaItem,
  NotaVentaResumen,
  OrigenNotaVenta,
  PaginaResponse,
} from '../models/models';

export interface NotaVentaRequest {
  clienteId: number;
  formaPagoId: number | null;
  fechaEmision: string;
  fechaEntregaEstimada: string | null;
  direccionEntrega?: string;
  condicionesVenta?: string;
  observaciones?: string;
  exenta: boolean;
  moneda?: string;
  descuento: number;
  items: NotaVentaItem[];
}

export interface FiltrosNotaVenta {
  estado?: EstadoNotaVenta | null;
  clienteId?: number | null;
  vendedorId?: number | null;
  desde?: string | null;
  hasta?: string | null;
  origen?: OrigenNotaVenta | null;
  q?: string | null;
  sort?: string;
  dir?: string;
  pagina?: number;
  tamano?: number;
}

export interface EntregaLineaRequest {
  productoId: number;
  cantidad: number;
}

export interface EntregaRequest {
  observacion?: string;
  lineas: EntregaLineaRequest[];
}

@Injectable({ providedIn: 'root' })
export class NotaVentaService {
  private readonly base = `${environment.apiUrl}/notas-venta`;

  constructor(private http: HttpClient) {}

  listar(filtros: FiltrosNotaVenta): Observable<PaginaResponse<NotaVentaResumen>> {
    let params = new HttpParams()
      .set('pagina', filtros.pagina ?? 0)
      .set('tamano', filtros.tamano ?? 10);
    if (filtros.estado) params = params.set('estado', filtros.estado);
    if (filtros.clienteId) params = params.set('clienteId', filtros.clienteId);
    if (filtros.vendedorId) params = params.set('vendedorId', filtros.vendedorId);
    if (filtros.desde) params = params.set('desde', filtros.desde);
    if (filtros.hasta) params = params.set('hasta', filtros.hasta);
    if (filtros.origen) params = params.set('origen', filtros.origen);
    if (filtros.q) params = params.set('q', filtros.q);
    if (filtros.sort) params = params.set('sort', filtros.sort);
    if (filtros.dir) params = params.set('dir', filtros.dir);
    return this.http.get<PaginaResponse<NotaVentaResumen>>(this.base, { params });
  }

  dashboard(desde: string, hasta: string): Observable<DashboardNotasVenta> {
    return this.http.get<DashboardNotasVenta>(`${this.base}/dashboard`, { params: { desde, hasta } });
  }

  obtener(id: number): Observable<NotaVenta> {
    return this.http.get<NotaVenta>(`${this.base}/${id}`);
  }

  crear(request: NotaVentaRequest): Observable<NotaVenta> {
    return this.http.post<NotaVenta>(this.base, request);
  }

  crearDesdeCotizacion(cotizacionId: number): Observable<NotaVenta> {
    return this.http.post<NotaVenta>(`${this.base}/desde-cotizacion/${cotizacionId}`, {});
  }

  actualizar(id: number, request: NotaVentaRequest): Observable<NotaVenta> {
    return this.http.put<NotaVenta>(`${this.base}/${id}`, request);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  confirmar(id: number): Observable<NotaVenta> {
    return this.http.post<NotaVenta>(`${this.base}/${id}/confirmar`, {});
  }

  preparar(id: number): Observable<NotaVenta> {
    return this.http.post<NotaVenta>(`${this.base}/${id}/preparar`, {});
  }

  registrarEntrega(id: number, request: EntregaRequest): Observable<NotaVenta> {
    return this.http.post<NotaVenta>(`${this.base}/${id}/entregas`, request);
  }

  cancelar(id: number, motivo: string | null): Observable<NotaVenta> {
    return this.http.post<NotaVenta>(`${this.base}/${id}/cancelar`, { motivo });
  }

  duplicar(id: number): Observable<NotaVenta> {
    return this.http.post<NotaVenta>(`${this.base}/${id}/duplicar`, {});
  }

  obtenerPdf(id: number): Observable<Blob> {
    return this.http.get(`${this.base}/${id}/pdf`, { responseType: 'blob' });
  }
}