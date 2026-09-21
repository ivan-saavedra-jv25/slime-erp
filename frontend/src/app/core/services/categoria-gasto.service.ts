import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CategoriaGasto, PaginaResponse } from '../models/models';

export interface CategoriaGastoRequest {
  nombre: string;
}

@Injectable({ providedIn: 'root' })
export class CategoriaGastoService {
  private readonly base = `${environment.apiUrl}/gastos/categorias`;

  constructor(private http: HttpClient) {}

  listar(): Observable<CategoriaGasto[]> {
    return this.http.get<CategoriaGasto[]>(this.base);
  }

  listarPagina(q: string, pagina: number, tamano: number): Observable<PaginaResponse<CategoriaGasto>> {
    const params: Record<string, string> = { pagina: String(pagina), tamano: String(tamano) };
    if (q) params['q'] = q;
    return this.http.get<PaginaResponse<CategoriaGasto>>(`${this.base}/pagina`, { params });
  }

  crear(request: CategoriaGastoRequest): Observable<CategoriaGasto> {
    return this.http.post<CategoriaGasto>(this.base, request);
  }

  actualizar(id: number, request: CategoriaGastoRequest): Observable<CategoriaGasto> {
    return this.http.put<CategoriaGasto>(`${this.base}/${id}`, request);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
