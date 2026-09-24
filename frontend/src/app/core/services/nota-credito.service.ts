import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  DashboardNotasCredito,
  DocumentoAsociable,
  EstadoNotaCredito,
  LineaDocumentoOriginal,
  NotaCredito,
  NotaCreditoItem,
  NotaCreditoResumen,
  PaginaResponse,
  TipoCorreccion,
  TipoDocumentoVenta,
} from '../models/models';

// El id nunca viaja en la creación: lo genera la base de datos.
export interface NotaCreditoRequest {
  ventaId: number;
  tipoCorreccion: TipoCorreccion;
  fecha: string;
  docAsociadoRazon: string;
  motivo?: string | null;
  observaciones?: string | null;
  textoCorreccion?: string | null;
  descuento: number;
  items: NotaCreditoItem[];
}

export interface FiltrosNotaCredito {
  estado?: EstadoNotaCredito | null;
  clienteId?: number | null;
  tipoCorreccion?: TipoCorreccion | null;
  ventaId?: number | null;
  docAsociadoTipo?: TipoDocumentoVenta | null;
  desde?: string | null;
  hasta?: string | null;
  q?: string | null;
  sort?: string;
  dir?: string;
  pagina?: number;
  tamano?: number;
}

@Injectable({ providedIn: 'root' })
export class NotaCreditoService {
  private readonly base = `${environment.apiUrl}/notas-credito`;

  constructor(private http: HttpClient) {}

  listar(filtros: FiltrosNotaCredito): Observable<PaginaResponse<NotaCreditoResumen>> {
    let params = new HttpParams()
      .set('pagina', filtros.pagina ?? 0)
      .set('tamano', filtros.tamano ?? 10);
    if (filtros.estado) params = params.set('estado', filtros.estado);
    if (filtros.clienteId) params = params.set('clienteId', filtros.clienteId);
    if (filtros.tipoCorreccion) params = params.set('tipoCorreccion', filtros.tipoCorreccion);
    if (filtros.ventaId) params = params.set('ventaId', filtros.ventaId);
    if (filtros.docAsociadoTipo) params = params.set('docAsociadoTipo', filtros.docAsociadoTipo);
    if (filtros.desde) params = params.set('desde', filtros.desde);
    if (filtros.hasta) params = params.set('hasta', filtros.hasta);
    if (filtros.q) params = params.set('q', filtros.q);
    if (filtros.sort) params = params.set('sort', filtros.sort);
    if (filtros.dir) params = params.set('dir', filtros.dir);
    return this.http.get<PaginaResponse<NotaCreditoResumen>>(this.base, { params });
  }

  dashboard(desde: string, hasta: string): Observable<DashboardNotasCredito> {
    const params = new HttpParams().set('desde', desde).set('hasta', hasta);
    return this.http.get<DashboardNotasCredito>(`${this.base}/dashboard`, { params });
  }

  obtener(id: number): Observable<NotaCredito> {
    return this.http.get<NotaCredito>(`${this.base}/${id}`);
  }

  // Ventas que el usuario puede elegir como documento a corregir.
  documentosAsociables(clienteId: number | null, q?: string | null): Observable<DocumentoAsociable[]> {
    let params = new HttpParams();
    if (clienteId) params = params.set('clienteId', clienteId);
    if (q) params = params.set('q', q);
    return this.http.get<DocumentoAsociable[]>(`${this.base}/documentos-asociables`, { params });
  }

  // Al editar un borrador se pasa su propio id para que sus líneas no cuenten
  // como cantidad ya recuperada.
  lineasDocumento(
    ventaId: number,
    excluyendoNotaCreditoId?: number | null,
  ): Observable<LineaDocumentoOriginal[]> {
    let params = new HttpParams();
    if (excluyendoNotaCreditoId) {
      params = params.set('excluyendoNotaCreditoId', excluyendoNotaCreditoId);
    }
    return this.http.get<LineaDocumentoOriginal[]>(`${this.base}/ventas/${ventaId}/lineas`, { params });
  }

  crear(request: NotaCreditoRequest): Observable<NotaCredito> {
    return this.http.post<NotaCredito>(this.base, request);
  }

  actualizar(id: number, request: NotaCreditoRequest): Observable<NotaCredito> {
    return this.http.put<NotaCredito>(`${this.base}/${id}`, request);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  emitir(id: number): Observable<NotaCredito> {
    return this.http.post<NotaCredito>(`${this.base}/${id}/emitir`, {});
  }

  anular(id: number, motivo?: string | null): Observable<NotaCredito> {
    return this.http.post<NotaCredito>(`${this.base}/${id}/anular`, { motivo: motivo ?? null });
  }

  obtenerPdf(id: number): Observable<Blob> {
    return this.http.get(`${this.base}/${id}/pdf`, { responseType: 'blob' });
  }
}
