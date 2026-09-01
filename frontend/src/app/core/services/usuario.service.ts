import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Usuario, Rol } from '../models/models';

export interface UsuarioRequest {
  nombre: string;
  rut: string;
  email: string;
  password?: string;
  rol: Rol;
  activo?: boolean;
}

@Injectable({ providedIn: 'root' })
export class UsuarioService {
  private readonly base = `${environment.apiUrl}/usuarios`;

  constructor(private http: HttpClient) {}

  listar(): Observable<Usuario[]> {
    return this.http.get<Usuario[]>(this.base);
  }

  crear(request: UsuarioRequest): Observable<Usuario> {
    return this.http.post<Usuario>(this.base, request);
  }

  actualizar(id: number, request: UsuarioRequest): Observable<Usuario> {
    return this.http.put<Usuario>(`${this.base}/${id}`, request);
  }

  desactivar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
