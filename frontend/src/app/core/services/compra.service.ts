import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Compra, CompraItem } from '../models/models';

export interface CompraRequest {
  proveedorId: number;
  bodegaId: number | null;
  observacion?: string;
  items: CompraItem[];
}

@Injectable({ providedIn: 'root' })
export class CompraService {
  private readonly base = `${environment.apiUrl}/compras`;

  constructor(private http: HttpClient) {}

  listar(): Observable<Compra[]> {
    return this.http.get<Compra[]>(this.base);
  }

  obtener(id: number): Observable<Compra> {
    return this.http.get<Compra>(`${this.base}/${id}`);
  }

  crear(request: CompraRequest): Observable<Compra> {
    return this.http.post<Compra>(this.base, request);
  }
}
