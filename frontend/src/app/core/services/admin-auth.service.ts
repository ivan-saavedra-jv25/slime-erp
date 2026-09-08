import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AdminSesion } from '../models/models';

const STORAGE_KEY = 'saavia_admin_session';

@Injectable({ providedIn: 'root' })
export class AdminAuthService {
  session = signal<AdminSesion | null>(this.leerSesionGuardada());

  constructor(private http: HttpClient) {}

  login(email: string, password: string): Observable<AdminSesion> {
    return this.http
      .post<AdminSesion>(`${environment.adminApiUrl}/admin/auth/login`, { email, password })
      .pipe(
        tap((res) => {
          localStorage.setItem(STORAGE_KEY, JSON.stringify(res));
          this.session.set(res);
        })
      );
  }

  cerrarSesion(): Observable<void> {
    const token = this.token;
    localStorage.removeItem(STORAGE_KEY);
    this.session.set(null);
    if (!token) {
      return of(void 0);
    }
    return this.http.post<void>(`${environment.adminApiUrl}/admin/auth/logout`, null);
  }

  get token(): string | null {
    return this.session()?.token ?? null;
  }

  get estaAutenticado(): boolean {
    return this.session() !== null;
  }

  get adminRol(): string | undefined {
    return this.session()?.adminRol;
  }

  tienePermiso(permiso: string): boolean {
    return this.session()?.permisos.includes(permiso) ?? false;
  }

  private leerSesionGuardada(): AdminSesion | null {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw) as AdminSesion;
    } catch {
      localStorage.removeItem(STORAGE_KEY);
      return null;
    }
  }
}