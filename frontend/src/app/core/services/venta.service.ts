import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { TipoDocumentoVenta, Venta, VentaItem } from '../models/models';

export interface VentaRequest {
  clienteId: number;
  formaPagoId: number;
  bodegaId: number | null;
  tipoDocumento: TipoDocumentoVenta;
  exento: boolean;
  observacion?: string;
  descuento?: number;
  items: VentaItem[];
}

@Injectable({ providedIn: 'root' })
export class VentaService {
  private readonly base = `${environment.apiUrl}/ventas`;

  constructor(private http: HttpClient) {}

  listar(): Observable<Venta[]> {
    return this.http.get<Venta[]>(this.base);
  }

  obtener(id: number): Observable<Venta> {
    return this.http.get<Venta>(`${this.base}/${id}`);
  }

  crear(request: VentaRequest): Observable<Venta> {
    return this.http.post<Venta>(this.base, request);
  }
}
