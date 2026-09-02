import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DashboardResponse, PuntoVenta } from '../models/models';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly base = `${environment.apiUrl}/dashboard`;

  constructor(private http: HttpClient) {}

  resumen(): Observable<DashboardResponse> {
    return this.http.get<DashboardResponse>(this.base);
  }

  ventasEvolucion(rango: string): Observable<PuntoVenta[]> {
    return this.http.get<PuntoVenta[]>(`${this.base}/ventas-evolucion`, { params: { rango } });
  }
}
