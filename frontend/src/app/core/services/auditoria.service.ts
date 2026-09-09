import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuditLogFiltros, AuditLogResponse, Paginated } from '../models/models';

@Injectable({ providedIn: 'root' })
export class AuditoriaService {
  private readonly base = `${environment.adminApiUrl}/admin/audit`;

  constructor(private http: HttpClient) {}

  listar(filtros?: AuditLogFiltros): Observable<Paginated<AuditLogResponse>> {
    let params = new HttpParams();
    if (filtros) {
      if (filtros.page !== undefined) params = params.set('page', filtros.page);
      if (filtros.limit !== undefined) params = params.set('limit', filtros.limit);
      if (filtros.adminUserId !== undefined) params = params.set('adminUserId', filtros.adminUserId);
      if (filtros.companyId !== undefined) params = params.set('companyId', filtros.companyId);
      if (filtros.modulo) params = params.set('modulo', filtros.modulo);
      if (filtros.action) params = params.set('action', filtros.action);
      if (filtros.desde) params = params.set('desde', filtros.desde);
      if (filtros.hasta) params = params.set('hasta', filtros.hasta);
    }
    return this.http.get<Paginated<AuditLogResponse>>(this.base, { params });
  }
}