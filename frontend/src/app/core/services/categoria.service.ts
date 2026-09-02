import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Categoria } from '../models/models';

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
