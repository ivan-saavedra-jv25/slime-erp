import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LoginResponse, Permiso } from '../models/models';

const STORAGE_KEY = 'slime_erp_session';

@Injectable({ providedIn: 'root' })
export class AuthService {
  session = signal<LoginResponse | null>(this.leerSesionGuardada());

  constructor(private http: HttpClient) {}

  login(email: string, password: string): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${environment.apiUrl}/auth/login`, { email, password })
      .pipe(
        tap((res) => {
          localStorage.setItem(STORAGE_KEY, JSON.stringify(res));
          this.session.set(res);
        })
      );
  }

  logout(): void {
    localStorage.removeItem(STORAGE_KEY);
    this.session.set(null);
  }

  get token(): string | null {
    return this.session()?.token ?? null;
  }

  get estaAutenticado(): boolean {
    return this.session() !== null;
  }

  tienePermiso(permiso: Permiso): boolean {
    return this.session()?.permisos.includes(permiso) ?? false;
  }

  private leerSesionGuardada(): LoginResponse | null {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw) as LoginResponse;
    } catch {
      localStorage.removeItem(STORAGE_KEY);
      return null;
    }
  }
}
