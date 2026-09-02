import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Subcategoria } from '../models/models';

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
