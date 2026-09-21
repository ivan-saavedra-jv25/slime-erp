import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CuentaPorPagar, EstadoCuentaPorPagar, ResumenCuentasPorPagar } from '../models/models';

@Injectable({ providedIn: 'root' })
export class CuentaPorPagarService {
  private readonly base = `${environment.apiUrl}/tesoreria/cuentas-por-pagar`;

  constructor(private http: HttpClient) {}

  listar(proveedorId?: number, categoriaGastoId?: number, estado?: EstadoCuentaPorPagar): Observable<CuentaPorPagar[]> {
    const params: Record<string, string> = {};
    if (proveedorId) params['proveedorId'] = String(proveedorId);
    if (categoriaGastoId) params['categoriaGastoId'] = String(categoriaGastoId);
    if (estado) params['estado'] = estado;
    return this.http.get<CuentaPorPagar[]>(this.base, { params });
  }

  resumen(): Observable<ResumenCuentasPorPagar> {
    return this.http.get<ResumenCuentasPorPagar>(`${this.base}/resumen`);
  }

  obtener(id: number): Observable<CuentaPorPagar> {
    return this.http.get<CuentaPorPagar>(`${this.base}/${id}`);
  }

  anular(id: number, motivo: string): Observable<CuentaPorPagar> {
    return this.http.post<CuentaPorPagar>(`${this.base}/${id}/anular`, { motivo });
  }
}
