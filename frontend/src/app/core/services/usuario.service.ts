import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Permiso, PermisosUsuario, Usuario, UsuarioBasico, Rol } from '../models/models';

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

  // Listado liviano (id/nombre) para selectores de "responsable" en otras pantallas —
  // no requiere el permiso USUARIOS_VER que sí exige listar().
  listarBasico(): Observable<UsuarioBasico[]> {
    return this.http.get<UsuarioBasico[]>(`${this.base}/basico`);
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

  obtenerPermisos(id: number): Observable<PermisosUsuario> {
    return this.http.get<PermisosUsuario>(`${this.base}/${id}/permisos`);
  }

  guardarPermisosExtra(id: number, permisos: Permiso[]): Observable<void> {
    return this.http.put<void>(`${this.base}/${id}/permisos-extra`, { permisos });
  }
}
