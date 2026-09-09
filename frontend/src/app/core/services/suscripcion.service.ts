import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CambiarPlanRequest,
  EstadoSuscripcion,
  ExtenderSuscripcionRequest,
  Paginated,
  Suscripcion,
  SuscripcionRequest,
  Vencimientos,
} from '../models/models';

export interface SuscripcionFiltros {
  page?: number;
  limit?: number;
  estado?: EstadoSuscripcion;
  planId?: number;
  empresaId?: number;
  proximasAVencerDias?: number;
}

@Injectable({ providedIn: 'root' })
export class SuscripcionService {
  private readonly base = `${environment.adminApiUrl}/admin/suscripciones`;

  constructor(private http: HttpClient) {}

  listar(filtros?: SuscripcionFiltros): Observable<Paginated<Suscripcion>> {
    let params = new HttpParams();
    if (filtros) {
      if (filtros.page !== undefined) params = params.set('page', filtros.page);
      if (filtros.limit !== undefined) params = params.set('limit', filtros.limit);
      if (filtros.estado) params = params.set('estado', filtros.estado);
      if (filtros.planId !== undefined) params = params.set('planId', filtros.planId);
      if (filtros.empresaId !== undefined) params = params.set('empresaId', filtros.empresaId);
      if (filtros.proximasAVencerDias !== undefined) {
        params = params.set('proximasAVencerDias', filtros.proximasAVencerDias);
      }
    }
    return this.http.get<Paginated<Suscripcion>>(this.base, { params });
  }

  obtener(id: number): Observable<Suscripcion> {
    return this.http.get<Suscripcion>(`${this.base}/${id}`);
  }

  expiring(): Observable<Vencimientos> {
    return this.http.get<Vencimientos>(`${this.base}/expiring`);
  }

  crear(request: SuscripcionRequest): Observable<Suscripcion> {
    return this.http.post<Suscripcion>(this.base, request);
  }

  extender(id: number, request: ExtenderSuscripcionRequest): Observable<Suscripcion> {
    return this.http.post<Suscripcion>(`${this.base}/${id}/extend`, request);
  }

  cambiarPlan(id: number, request: CambiarPlanRequest): Observable<Suscripcion> {
    return this.http.post<Suscripcion>(`${this.base}/${id}/cambiar-plan`, request);
  }

  suspender(id: number, motivo: string): Observable<Suscripcion> {
    return this.http.post<Suscripcion>(`${this.base}/${id}/suspend`, { motivo });
  }

  reactivar(id: number, motivo: string): Observable<Suscripcion> {
    return this.http.post<Suscripcion>(`${this.base}/${id}/reactivate`, { motivo });
  }
}