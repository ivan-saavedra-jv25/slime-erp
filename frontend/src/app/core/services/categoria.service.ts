import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Categoria, PaginaResponse } from '../models/models';

export interface CategoriaRequest {
  nombre: string;
}

@Injectable({ providedIn: 'root' })
export class CategoriaService {
  private readonly base = `${environment.apiUrl}/categorias`;

  constructor(private http: HttpClient) {}

  listar(): Observable<Categoria[]> {
    return this.http.get<Categoria[]>(this.base);
  }

  listarPagina(q: string, pagina: number, tamano: number): Observable<PaginaResponse<Categoria>> {
    const params: Record<string, string> = { pagina: String(pagina), tamano: String(tamano) };
    if (q) params['q'] = q;
    return this.http.get<PaginaResponse<Categoria>>(`${this.base}/pagina`, { params });
  }

  crear(request: CategoriaRequest): Observable<Categoria> {
    return this.http.post<Categoria>(this.base, request);
  }

  actualizar(id: number, request: CategoriaRequest): Observable<Categoria> {
    return this.http.put<Categoria>(`${this.base}/${id}`, request);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
