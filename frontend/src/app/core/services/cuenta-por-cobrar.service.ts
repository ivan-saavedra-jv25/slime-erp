import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CuentaPorCobrar, EstadoCuentaPorCobrar, ResumenTesoreria } from '../models/models';

@Injectable({ providedIn: 'root' })
export class CuentaPorCobrarService {
  private readonly base = `${environment.apiUrl}/tesoreria/cuentas`;

  constructor(private http: HttpClient) {}

  listar(clienteId?: number, estado?: EstadoCuentaPorCobrar): Observable<CuentaPorCobrar[]> {
    const params: Record<string, string> = {};
    if (clienteId) params['clienteId'] = String(clienteId);
    if (estado) params['estado'] = estado;
    return this.http.get<CuentaPorCobrar[]>(this.base, { params });
  }

  resumen(): Observable<ResumenTesoreria> {
    return this.http.get<ResumenTesoreria>(`${this.base}/resumen`);
  }

  obtener(id: number): Observable<CuentaPorCobrar> {
    return this.http.get<CuentaPorCobrar>(`${this.base}/${id}`);
  }

  obtenerPorVenta(ventaId: number): Observable<CuentaPorCobrar> {
    return this.http.get<CuentaPorCobrar>(`${this.base}/venta/${ventaId}`);
  }

  anular(id: number, motivo: string): Observable<CuentaPorCobrar> {
    return this.http.post<CuentaPorCobrar>(`${this.base}/${id}/anular`, { motivo });
  }
}
