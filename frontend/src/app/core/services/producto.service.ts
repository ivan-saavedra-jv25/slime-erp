import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Producto } from '../models/models';

export interface ProductoRequest {
  sku?: string | null;
  nombre: string;
  descripcion?: string;
  precioVenta: number;
  precioCompra?: number;
  stock?: number;
  controlaStock: boolean;
}

@Injectable({ providedIn: 'root' })
export class ProductoService {
  private readonly base = `${environment.apiUrl}/productos`;

  constructor(private http: HttpClient) {}

  listar(): Observable<Producto[]> {
    return this.http.get<Producto[]>(this.base);
  }

  crear(request: ProductoRequest): Observable<Producto> {
    return this.http.post<Producto>(this.base, request);
  }

  actualizar(id: number, request: ProductoRequest): Observable<Producto> {
    return this.http.put<Producto>(`${this.base}/${id}`, request);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
