import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { InventarioItem, StockPorBodega } from '../models/models';

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

  ajustar(request: AjusteStockRequest): Observable<StockPorBodega[]> {
    return this.http.post<StockPorBodega[]>(`${this.base}/ajuste`, request);
  }
}
