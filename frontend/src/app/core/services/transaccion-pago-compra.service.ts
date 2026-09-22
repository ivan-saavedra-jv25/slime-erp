import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { EstadoTransaccion, MedioPago, PaginaResponse, TransaccionPagoCompra } from '../models/models';

export interface TransaccionPagoCompraRequest {
  monto: number;
  medioPago: MedioPago;
  observaciones?: string;
  fecha?: string;
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

export interface FiltrosHistorialPagoCompra {
  estado?: EstadoTransaccion;
  medioPago?: MedioPago;
  fechaDesde?: string;
  fechaHasta?: string;
  pagina?: number;
  tamano?: number;
}

@Injectable({ providedIn: 'root' })
export class TransaccionPagoCompraService {
  private readonly base = `${environment.apiUrl}/tesoreria`;

  constructor(private http: HttpClient) {}

  registrarPago(cuentaId: number, request: TransaccionPagoCompraRequest): Observable<TransaccionPagoCompra> {
    return this.http.post<TransaccionPagoCompra>(`${this.base}/cuentas-por-pagar/${cuentaId}/pagos`, request);
  }

  listarPorCuenta(cuentaId: number): Observable<TransaccionPagoCompra[]> {
    return this.http.get<TransaccionPagoCompra[]>(`${this.base}/cuentas-por-pagar/${cuentaId}/pagos`);
  }

  buscar(filtros: FiltrosHistorialPagoCompra): Observable<PaginaResponse<TransaccionPagoCompra>> {
    const params: Record<string, string> = {
      pagina: String(filtros.pagina ?? 0),
      tamano: String(filtros.tamano ?? 10),
    };
    if (filtros.estado) params['estado'] = filtros.estado;
    if (filtros.medioPago) params['medioPago'] = filtros.medioPago;
    if (filtros.fechaDesde) params['fechaDesde'] = filtros.fechaDesde;
    if (filtros.fechaHasta) params['fechaHasta'] = filtros.fechaHasta;
    return this.http.get<PaginaResponse<TransaccionPagoCompra>>(`${this.base}/pagos-compra`, { params });
  }

  anular(id: number, motivo: string): Observable<TransaccionPagoCompra> {
    return this.http.post<TransaccionPagoCompra>(`${this.base}/pagos-compra/${id}/anular`, { motivo });
  }
}
