import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { EstadoTransaccion, MedioPago, TransaccionPago } from '../models/models';

export interface TransaccionPagoRequest {
  monto: number;
  medioPago: MedioPago;
  observaciones?: string;
  transferenciaBancoOrigen?: string;
  transferenciaBancoDestino?: string;
  transferenciaNumeroOperacion?: string;
  transferenciaFecha?: string;
  tarjetaEntidad?: string;
  tarjetaTipo?: string;
  tarjetaNumeroOperacion?: string;
  tarjetaFecha?: string;
  chequeBanco?: string;
  chequeNumero?: string;
  chequeFechaEmision?: string;
  chequeFechaPago?: string;
}

export interface FiltrosHistorialPago {
  clienteId?: number;
  estado?: EstadoTransaccion;
  medioPago?: MedioPago;
  fechaDesde?: string;
  fechaHasta?: string;
}

@Injectable({ providedIn: 'root' })
export class TransaccionPagoService {
  private readonly base = `${environment.apiUrl}/tesoreria`;

  constructor(private http: HttpClient) {}

  registrarPago(cuentaId: number, request: TransaccionPagoRequest): Observable<TransaccionPago> {
    return this.http.post<TransaccionPago>(`${this.base}/cuentas/${cuentaId}/pagos`, request);
  }

  listarPorCuenta(cuentaId: number): Observable<TransaccionPago[]> {
    return this.http.get<TransaccionPago[]>(`${this.base}/cuentas/${cuentaId}/pagos`);
  }

  buscar(filtros: FiltrosHistorialPago): Observable<TransaccionPago[]> {
    const params: Record<string, string> = {};
    if (filtros.clienteId) params['clienteId'] = String(filtros.clienteId);
    if (filtros.estado) params['estado'] = filtros.estado;
    if (filtros.medioPago) params['medioPago'] = filtros.medioPago;
    if (filtros.fechaDesde) params['fechaDesde'] = filtros.fechaDesde;
    if (filtros.fechaHasta) params['fechaHasta'] = filtros.fechaHasta;
    return this.http.get<TransaccionPago[]>(`${this.base}/pagos`, { params });
  }

  anular(id: number, motivo: string): Observable<TransaccionPago> {
    return this.http.post<TransaccionPago>(`${this.base}/pagos/${id}/anular`, { motivo });
  }
}
