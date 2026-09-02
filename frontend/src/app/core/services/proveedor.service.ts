import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Proveedor } from '../models/models';

export interface ProveedorRequest {
  nombre: string;
  rut?: string;
  email?: string;
  telefono?: string;
  direccion?: string;
}

@Injectable({ providedIn: 'root' })
export class ProveedorService {
  private readonly base = `${environment.apiUrl}/proveedores`;

  constructor(private http: HttpClient) {}

  listar(): Observable<Proveedor[]> {
    return this.http.get<Proveedor[]>(this.base);
  }

  crear(request: ProveedorRequest): Observable<Proveedor> {
    return this.http.post<Proveedor>(this.base, request);
  }

  actualizar(id: number, request: ProveedorRequest): Observable<Proveedor> {
    return this.http.put<Proveedor>(`${this.base}/${id}`, request);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
