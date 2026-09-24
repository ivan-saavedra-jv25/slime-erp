import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  DashboardNotasDebito,
  DocumentoNotaCreditoAsociable,
  EslabonCadenaDocumento,
  EstadoNotaDebito,
  LineaNotaCreditoOriginal,
  NotaDebito,
  NotaDebitoItem,
  NotaDebitoResumen,
  PaginaResponse,
  TipoReversion,
} from '../models/models';

// El id nunca viaja en la creación: lo genera la base de datos.
export interface NotaDebitoRequest {
  notaCreditoId: number;
  tipoReversion: TipoReversion;
  fecha: string;
  ncRazon: string;
  motivo?: string | null;
  observaciones?: string | null;
  textoCorreccion?: string | null;
  descuento: number;
  items: NotaDebitoItem[];
}

export interface FiltrosNotaDebito {
  estado?: EstadoNotaDebito | null;
  clienteId?: number | null;
  tipoReversion?: TipoReversion | null;
  notaCreditoId?: number | null;
  desde?: string | null;
  hasta?: string | null;
  q?: string | null;
  sort?: string;
  dir?: string;
  pagina?: number;
  tamano?: number;
}

@Injectable({ providedIn: 'root' })
export class NotaDebitoService {
  private readonly base = `${environment.apiUrl}/notas-debito`;

  constructor(private http: HttpClient) {}

  listar(filtros: FiltrosNotaDebito): Observable<PaginaResponse<NotaDebitoResumen>> {
    let params = new HttpParams()
      .set('pagina', filtros.pagina ?? 0)
      .set('tamano', filtros.tamano ?? 10);
    if (filtros.estado) params = params.set('estado', filtros.estado);
    if (filtros.clienteId) params = params.set('clienteId', filtros.clienteId);
    if (filtros.tipoReversion) params = params.set('tipoReversion', filtros.tipoReversion);
    if (filtros.notaCreditoId) params = params.set('notaCreditoId', filtros.notaCreditoId);
    if (filtros.desde) params = params.set('desde', filtros.desde);
    if (filtros.hasta) params = params.set('hasta', filtros.hasta);
    if (filtros.q) params = params.set('q', filtros.q);
    if (filtros.sort) params = params.set('sort', filtros.sort);
    if (filtros.dir) params = params.set('dir', filtros.dir);
    return this.http.get<PaginaResponse<NotaDebitoResumen>>(this.base, { params });
  }

  dashboard(desde: string, hasta: string): Observable<DashboardNotasDebito> {
    const params = new HttpParams().set('desde', desde).set('hasta', hasta);
    return this.http.get<DashboardNotasDebito>(`${this.base}/dashboard`, { params });
  }

  obtener(id: number): Observable<NotaDebito> {
    return this.http.get<NotaDebito>(`${this.base}/${id}`);
  }

  // Notas de Crédito emitidas que el usuario puede elegir para revertir.
  notasCreditoAsociables(clienteId: number | null, q?: string | null): Observable<DocumentoNotaCreditoAsociable[]> {
    let params = new HttpParams();
    if (clienteId) params = params.set('clienteId', clienteId);
    if (q) params = params.set('q', q);
    return this.http.get<DocumentoNotaCreditoAsociable[]>(`${this.base}/notas-credito-asociables`, { params });
  }

  // Al editar un borrador se pasa su propio id para que sus líneas no cuenten
  // como cantidad ya revertida.
  lineasNotaCredito(
    notaCreditoId: number,
    excluyendoNotaDebitoId?: number | null,
  ): Observable<LineaNotaCreditoOriginal[]> {
    let params = new HttpParams();
    if (excluyendoNotaDebitoId) {
      params = params.set('excluyendoNotaDebitoId', excluyendoNotaDebitoId);
    }
    return this.http.get<LineaNotaCreditoOriginal[]>(`${this.base}/notas-credito/${notaCreditoId}/lineas`, { params });
  }

  // Cadena de documentos asociados de la nota de débito (Cotización -> Nota de
  // Venta -> Venta -> Nota de Crédito) para la trazabilidad del detalle.
  cadenaDocumentos(id: number): Observable<EslabonCadenaDocumento[]> {
    return this.http.get<EslabonCadenaDocumento[]>(`${this.base}/${id}/cadena`);
  }

  crear(request: NotaDebitoRequest): Observable<NotaDebito> {
    return this.http.post<NotaDebito>(this.base, request);
  }

  actualizar(id: number, request: NotaDebitoRequest): Observable<NotaDebito> {
    return this.http.put<NotaDebito>(`${this.base}/${id}`, request);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  emitir(id: number): Observable<NotaDebito> {
    return this.http.post<NotaDebito>(`${this.base}/${id}/emitir`, {});
  }

  anular(id: number, motivo?: string | null): Observable<NotaDebito> {
    return this.http.post<NotaDebito>(`${this.base}/${id}/anular`, { motivo: motivo ?? null });
  }

  obtenerPdf(id: number): Observable<Blob> {
    return this.http.get(`${this.base}/${id}/pdf`, { responseType: 'blob' });
  }
}