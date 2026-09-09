import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PagoFiltros, PagoPlataforma, Paginated, RegistrarPagoManualRequest } from '../models/models';

@Injectable({ providedIn: 'root' })
export class PagosService {
  private readonly base = `${environment.adminApiUrl}/admin/pagos`;

  constructor(private http: HttpClient) {}

  listar(filtros?: PagoFiltros): Observable<Paginated<PagoPlataforma>> {
    let params = new HttpParams();
    if (filtros) {
      if (filtros.page !== undefined) params = params.set('page', filtros.page);
      if (filtros.limit !== undefined) params = params.set('limit', filtros.limit);
      if (filtros.empresaId !== undefined) params = params.set('empresaId', filtros.empresaId);
      if (filtros.estado) params = params.set('estado', filtros.estado);
    }
    return this.http.get<Paginated<PagoPlataforma>>(this.base, { params });
  }

  obtener(id: number): Observable<PagoPlataforma> {
    return this.http.get<PagoPlataforma>(`${this.base}/${id}`);
  }

  registrar(request: RegistrarPagoManualRequest): Observable<PagoPlataforma> {
    return this.http.post<PagoPlataforma>(this.base, request);
  }
}