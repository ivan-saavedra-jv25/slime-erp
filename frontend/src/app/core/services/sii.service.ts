import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Paginated, SiiEstado } from '../models/models';

export interface SiiFiltros {
  page?: number;
  limit?: number;
  estado?: string;
}

@Injectable({ providedIn: 'root' })
export class SiiService {
  private readonly base = `${environment.adminApiUrl}/admin/sii`;

  constructor(private http: HttpClient) {}

  listar(filtros?: SiiFiltros): Observable<Paginated<SiiEstado>> {
    let params = new HttpParams();
    if (filtros) {
      if (filtros.page !== undefined) params = params.set('page', filtros.page);
      if (filtros.limit !== undefined) params = params.set('limit', filtros.limit);
      if (filtros.estado) params = params.set('estado', filtros.estado);
    }
    return this.http.get<Paginated<SiiEstado>>(this.base, { params });
  }

  detalle(empresaId: number): Observable<SiiEstado> {
    return this.http.get<SiiEstado>(`${this.base}/empresas/${empresaId}`);
  }
}