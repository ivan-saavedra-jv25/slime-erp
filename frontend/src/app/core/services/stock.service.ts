import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { InventarioItem, PaginaResponse, StockPorBodega } from '../models/models';

export interface AjusteStockRequest {
  productoId: number;
  bodegaId: number;
  cantidad: number;
}

@Injectable({ providedIn: 'root' })
export class StockService {
  private readonly base = `${environment.apiUrl}/stock`;

  constructor(private http: HttpClient) {}

  porProducto(productoId: number): Observable<StockPorBodega[]> {
    return this.http.get<StockPorBodega[]>(this.base, { params: { productoId } });
  }

  inventarioPorBodega(bodegaId: number): Observable<InventarioItem[]> {
    return this.http.get<InventarioItem[]>(`${this.base}/inventario`, { params: { bodegaId } });
  }

  inventarioPorBodegaPagina(
    bodegaId: number,
    q: string,
    pagina: number,
    tamano: number
  ): Observable<PaginaResponse<InventarioItem>> {
    const params: Record<string, string> = {
      bodegaId: String(bodegaId),
      pagina: String(pagina),
      tamano: String(tamano),
    };
    if (q) params['q'] = q;
    return this.http.get<PaginaResponse<InventarioItem>>(`${this.base}/inventario/pagina`, { params });
  }

  ajustar(request: AjusteStockRequest): Observable<StockPorBodega[]> {
    return this.http.post<StockPorBodega[]>(`${this.base}/ajuste`, request);
  }
}
