import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CambiarEstadoRequest,
  CrearEmpresaRequest,
  Empresa,
  EmpresaDetalle,
  EmpresaFiltros,
  Paginated,
} from '../models/models';

@Injectable({ providedIn: 'root' })
export class EmpresaAdminService {
  private readonly base = `${environment.adminApiUrl}/admin/empresas`;

  constructor(private http: HttpClient) {}

  listar(filtros?: EmpresaFiltros): Observable<Paginated<Empresa>> {
    let params = new HttpParams();
    if (filtros) {
      if (filtros.page !== undefined) params = params.set('page', filtros.page);
      if (filtros.limit !== undefined) params = params.set('limit', filtros.limit);
      if (filtros.id !== undefined) params = params.set('id', filtros.id);
      if (filtros.rut) params = params.set('rut', filtros.rut);
      if (filtros.razonSocial) params = params.set('razonSocial', filtros.razonSocial);
      if (filtros.nombreComercial) params = params.set('nombreComercial', filtros.nombreComercial);
      if (filtros.estado) params = params.set('estado', filtros.estado);
      if (filtros.plan) params = params.set('plan', filtros.plan);
    }
    return this.http.get<Paginated<Empresa>>(this.base, { params });
  }

  detalle(id: number): Observable<EmpresaDetalle> {
    return this.http.get<EmpresaDetalle>(`${this.base}/${id}`);
  }

  crear(request: CrearEmpresaRequest): Observable<Empresa> {
    return this.http.post<Empresa>(this.base, request);
  }

  activar(id: number): Observable<Empresa> {
    return this.http.patch<Empresa>(`${this.base}/${id}/activar`, {});
  }

  desactivar(id: number): Observable<Empresa> {
    return this.http.patch<Empresa>(`${this.base}/${id}/desactivar`, {});
  }

  cambiarEstado(id: number, request: CambiarEstadoRequest): Observable<Empresa> {
    return this.http.patch<Empresa>(`${this.base}/${id}/estado`, request);
  }
}