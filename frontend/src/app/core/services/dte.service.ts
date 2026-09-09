import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DteDashboard, DocumentoDte, DteFiltros, Paginated } from '../models/models';

@Injectable({ providedIn: 'root' })
export class DteService {
  private readonly base = `${environment.adminApiUrl}/admin/dte`;

  constructor(private http: HttpClient) {}

  listar(filtros?: DteFiltros): Observable<Paginated<DocumentoDte>> {
    const params = this.params(filtros);
    return this.http.get<Paginated<DocumentoDte>>(this.base, { params });
  }

  dashboard(filtros?: DteFiltros): Observable<DteDashboard> {
    const params = this.params(filtros);
    return this.http.get<DteDashboard>(`${this.base}/dashboard`, { params });
  }

  private params(filtros?: DteFiltros): HttpParams {
    let params = new HttpParams();
    if (!filtros) return params;
    if (filtros.page !== undefined) params = params.set('page', filtros.page);
    if (filtros.limit !== undefined) params = params.set('limit', filtros.limit);
    if (filtros.empresaId !== undefined) params = params.set('empresaId', filtros.empresaId);
    if (filtros.tipoDte) params = params.set('tipoDte', filtros.tipoDte);
    if (filtros.estado) params = params.set('estado', filtros.estado);
    if (filtros.fechaDesde) params = params.set('fechaDesde', filtros.fechaDesde);
    if (filtros.fechaHasta) params = params.set('fechaHasta', filtros.fechaHasta);
    if (filtros.folio !== undefined) params = params.set('folio', filtros.folio);
    if (filtros.rutReceptor) params = params.set('rutReceptor', filtros.rutReceptor);
    return params;
  }
}