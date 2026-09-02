import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PaginaResponse, Producto } from '../models/models';

export interface ProductoRequest {
  sku?: string | null;
  nombre: string;
  descripcion?: string;
  categoriaId?: number | null;
  subcategoriaId?: number | null;
  precioVenta: number;
  precioCompra?: number;
  stockMinimo?: number;
}

@Injectable({ providedIn: 'root' })
export class ProductoService {
  private readonly base = `${environment.apiUrl}/productos`;

  constructor(private http: HttpClient) {}

  listar(): Observable<Producto[]> {
    return this.http.get<Producto[]>(this.base);
  }

  listarPagina(q: string, pagina: number, tamano: number): Observable<PaginaResponse<Producto>> {
    const params: Record<string, string> = { pagina: String(pagina), tamano: String(tamano) };
    if (q) params['q'] = q;
    return this.http.get<PaginaResponse<Producto>>(`${this.base}/pagina`, { params });
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
