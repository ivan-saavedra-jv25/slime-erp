import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CobranzaEmpresa, CobranzaPago, EstadoCobranza, PagoCobranzaRequest, ResumenCobranza } from '../models/models';

@Injectable({ providedIn: 'root' })
export class CobranzaService {
  private readonly base = `${environment.adminApiUrl}/admin/cobranza`;

  constructor(private http: HttpClient) {}

  listar(empresaId?: number, estado?: EstadoCobranza): Observable<CobranzaEmpresa[]> {
    const params: Record<string, string> = {};
    if (empresaId) params['empresaId'] = String(empresaId);
    if (estado) params['estado'] = estado;
    return this.http.get<CobranzaEmpresa[]>(this.base, { params });
  }

  resumen(): Observable<ResumenCobranza> {
    return this.http.get<ResumenCobranza>(`${this.base}/resumen`);
  }

  obtener(id: number): Observable<CobranzaEmpresa> {
    return this.http.get<CobranzaEmpresa>(`${this.base}/${id}`);
  }

  emitir(request: {
    tenantId: number;
    concepto: string;
    periodo: string;
    montoTotal: number;
    fechaVencimiento?: string | null;
    observaciones?: string | null;
  }): Observable<CobranzaEmpresa> {
    return this.http.post<CobranzaEmpresa>(this.base, request);
  }

  listarPagos(cobranzaId: number): Observable<CobranzaPago[]> {
    return this.http.get<CobranzaPago[]>(`${this.base}/${cobranzaId}/pagos`);
  }

  registrarPago(cobranzaId: number, request: PagoCobranzaRequest): Observable<CobranzaPago> {
    return this.http.post<CobranzaPago>(`${this.base}/${cobranzaId}/pagos`, request);
  }

  anularPago(pagoId: number, motivo: string): Observable<CobranzaPago> {
    return this.http.post<CobranzaPago>(`${this.base}/pagos/${pagoId}/anular`, { motivo });
  }

  anular(cobranzaId: number, motivo: string): Observable<CobranzaEmpresa> {
    return this.http.post<CobranzaEmpresa>(`${this.base}/${cobranzaId}/anular`, { motivo });
  }
}