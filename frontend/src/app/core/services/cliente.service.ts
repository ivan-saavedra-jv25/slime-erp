import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Cliente } from '../models/models';

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
