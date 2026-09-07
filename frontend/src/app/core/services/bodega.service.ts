import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Bodega, PaginaResponse, TipoBodega } from '../models/models';

export interface BodegaRequest {
  nombre: string;
  tipo: TipoBodega;
}

@Injectable({ providedIn: 'root' })
export class BodegaService {
  private readonly base = `${environment.apiUrl}/bodegas`;

  constructor(private http: HttpClient) {}

  listar(): Observable<Bodega[]> {
    return this.http.get<Bodega[]>(this.base);
  }

  listarPagina(q: string, pagina: number, tamano: number): Observable<PaginaResponse<Bodega>> {
    const params: Record<string, string> = { pagina: String(pagina), tamano: String(tamano) };
    if (q) params['q'] = q;
    return this.http.get<PaginaResponse<Bodega>>(`${this.base}/pagina`, { params });
  }

  crear(request: BodegaRequest): Observable<Bodega> {
    return this.http.post<Bodega>(this.base, request);
  }

  actualizar(id: number, request: BodegaRequest): Observable<Bodega> {
    return this.http.put<Bodega>(`${this.base}/${id}`, request);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  marcarPrincipal(id: number): Observable<Bodega> {
    return this.http.put<Bodega>(`${this.base}/${id}/principal`, {});
  }
}
