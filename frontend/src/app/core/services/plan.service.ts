import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Plan, PlanRequest } from '../models/models';

export interface CambiarEstadoPlanRequest {
  estado: 'ACTIVE' | 'INACTIVE';
}

@Injectable({ providedIn: 'root' })
export class PlanService {
  private readonly base = `${environment.adminApiUrl}/admin/planes`;

  constructor(private http: HttpClient) {}

  listar(): Observable<Plan[]> {
    return this.http.get<Plan[]>(this.base);
  }

  crear(request: PlanRequest): Observable<Plan> {
    return this.http.post<Plan>(this.base, request);
  }

  actualizar(id: number, request: PlanRequest): Observable<Plan> {
    return this.http.put<Plan>(`${this.base}/${id}`, request);
  }

  cambiarEstado(id: number, estado: 'ACTIVE' | 'INACTIVE'): Observable<Plan> {
    return this.http.patch<Plan>(`${this.base}/${id}/estado`, { estado });
  }
}