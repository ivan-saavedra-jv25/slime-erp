import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Alerta, AlertaFiltros, AlertaResumen, Paginated } from '../models/models';

@Injectable({ providedIn: 'root' })
export class AlertasService {
  private readonly base = `${environment.adminApiUrl}/admin/alerts`;

  constructor(private http: HttpClient) {}

  listar(filtros?: AlertaFiltros): Observable<Paginated<Alerta>> {
    let params = new HttpParams();
    if (filtros) {
      if (filtros.page !== undefined) params = params.set('page', filtros.page);
      if (filtros.limit !== undefined) params = params.set('limit', filtros.limit);
      if (filtros.severity) params = params.set('severity', filtros.severity);
      if (filtros.status) params = params.set('status', filtros.status);
      if (filtros.companyId !== undefined) params = params.set('companyId', filtros.companyId);
      if (filtros.tipo) params = params.set('tipo', filtros.tipo);
    }
    return this.http.get<Paginated<Alerta>>(this.base, { params });
  }

  summary(): Observable<AlertaResumen> {
    return this.http.get<AlertaResumen>(`${this.base}/summary`);
  }

  marcarLeida(id: number): Observable<Alerta> {
    return this.http.patch<Alerta>(`${this.base}/${id}/read`, null);
  }

  resolver(id: number, motivo?: string): Observable<Alerta> {
    return this.http.post<Alerta>(`${this.base}/${id}/resolve`, motivo ? { motivo } : null);
  }
}