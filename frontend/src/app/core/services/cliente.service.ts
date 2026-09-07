import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Cliente, PaginaResponse } from '../models/models';

export interface ClienteRequest {
  nombre: string;
  rut?: string;
  email?: string;
  telefono?: string;
  direccion?: string;
  razonSocial?: string;
  giro?: string;
  comuna?: string;
  ciudad?: string;
}

@Injectable({ providedIn: 'root' })
export class ClienteService {
  private readonly base = `${environment.apiUrl}/clientes`;

  constructor(private http: HttpClient) {}

  listar(): Observable<Cliente[]> {
    return this.http.get<Cliente[]>(this.base);
  }

  listarPagina(q: string, pagina: number, tamano: number): Observable<PaginaResponse<Cliente>> {
    const params: Record<string, string> = { pagina: String(pagina), tamano: String(tamano) };
    if (q) params['q'] = q;
    return this.http.get<PaginaResponse<Cliente>>(`${this.base}/pagina`, { params });
  }

  obtener(id: number): Observable<Cliente> {
    return this.http.get<Cliente>(`${this.base}/${id}`);
  }

  crear(request: ClienteRequest): Observable<Cliente> {
    return this.http.post<Cliente>(this.base, request);
  }

  actualizar(id: number, request: ClienteRequest): Observable<Cliente> {
    return this.http.put<Cliente>(`${this.base}/${id}`, request);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
