import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Empresa, CrearEmpresaRequest } from '../models/models';

@Injectable({ providedIn: 'root' })
export class EmpresaService {
  private readonly base = `${environment.apiUrl}/admin/empresas`;

  constructor(private http: HttpClient) {}

  listar(): Observable<Empresa[]> {
    return this.http.get<Empresa[]>(this.base);
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
}
