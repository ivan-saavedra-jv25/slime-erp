import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PaginaResponse, Subcategoria } from '../models/models';

export interface SubcategoriaRequest {
  categoriaId: number;
  nombre: string;
}

@Injectable({ providedIn: 'root' })
export class SubcategoriaService {
  private readonly base = `${environment.apiUrl}/subcategorias`;

  constructor(private http: HttpClient) {}

  listar(categoriaId?: number): Observable<Subcategoria[]> {
    return this.http.get<Subcategoria[]>(this.base, {
      params: categoriaId != null ? { categoriaId } : {},
    });
  }

  listarPagina(
    categoriaId: number,
    q: string,
    pagina: number,
    tamano: number
  ): Observable<PaginaResponse<Subcategoria>> {
    const params: Record<string, string> = {
      categoriaId: String(categoriaId),
      pagina: String(pagina),
      tamano: String(tamano),
    };
    if (q) params['q'] = q;
    return this.http.get<PaginaResponse<Subcategoria>>(`${this.base}/pagina`, { params });
  }

  crear(request: SubcategoriaRequest): Observable<Subcategoria> {
    return this.http.post<Subcategoria>(this.base, request);
  }

  actualizar(id: number, request: SubcategoriaRequest): Observable<Subcategoria> {
    return this.http.put<Subcategoria>(`${this.base}/${id}`, request);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
