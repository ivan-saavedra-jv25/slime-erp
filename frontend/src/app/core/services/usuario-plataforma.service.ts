import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Rol, UsuarioPlataforma } from '../models/models';

export interface UsuarioAdminRequest {
  tenantId?: number;
  nombre: string;
  rut: string;
  email: string;
  password?: string;
  rol: Rol;
  activo?: boolean;
}

@Injectable({ providedIn: 'root' })
export class UsuarioPlataformaService {
  private readonly base = `${environment.adminApiUrl}/admin/usuarios`;

  constructor(private http: HttpClient) {}

  listar(tenantId?: number, activo?: boolean): Observable<UsuarioPlataforma[]> {
    const params: Record<string, string> = {};
    if (tenantId) params['tenantId'] = String(tenantId);
    if (activo !== undefined) params['activo'] = String(activo);
    return this.http.get<UsuarioPlataforma[]>(this.base, { params });
  }

  crear(request: UsuarioAdminRequest): Observable<UsuarioPlataforma> {
    return this.http.post<UsuarioPlataforma>(this.base, request);
  }

  actualizar(id: number, request: UsuarioAdminRequest): Observable<UsuarioPlataforma> {
    return this.http.put<UsuarioPlataforma>(`${this.base}/${id}`, request);
  }

  activar(id: number): Observable<UsuarioPlataforma> {
    return this.http.patch<UsuarioPlataforma>(`${this.base}/${id}/activar`, {});
  }

  desactivar(id: number): Observable<UsuarioPlataforma> {
    return this.http.patch<UsuarioPlataforma>(`${this.base}/${id}/desactivar`, {});
  }

  resetearPassword(id: number, password: string): Observable<void> {
    return this.http.patch<void>(`${this.base}/${id}/password`, { password });
  }
}