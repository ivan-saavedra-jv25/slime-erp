import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Gasto, PaginaResponse } from '../models/models';

export interface GastoRequest {
  categoriaGastoId: number;
  monto: number;
  descripcion: string;
  fecha: string;
}

export interface FiltrosGasto {
  categoriaGastoId?: number | null;
  fechaDesde?: string;
  fechaHasta?: string;
  q?: string;
  pagina?: number;
  tamano?: number;
}

@Injectable({ providedIn: 'root' })
export class GastoService {
  private readonly base = `${environment.apiUrl}/gastos`;

  constructor(private http: HttpClient) {}

  buscar(filtros: FiltrosGasto): Observable<PaginaResponse<Gasto>> {
    const params: Record<string, string> = {
      pagina: String(filtros.pagina ?? 0),
      tamano: String(filtros.tamano ?? 10),
    };
    if (filtros.categoriaGastoId) params['categoriaGastoId'] = String(filtros.categoriaGastoId);
    if (filtros.fechaDesde) params['fechaDesde'] = filtros.fechaDesde;
    if (filtros.fechaHasta) params['fechaHasta'] = filtros.fechaHasta;
    if (filtros.q) params['q'] = filtros.q;
    return this.http.get<PaginaResponse<Gasto>>(this.base, { params });
  }

  crear(request: GastoRequest): Observable<Gasto> {
    return this.http.post<Gasto>(this.base, request);
  }

  actualizar(id: number, request: GastoRequest): Observable<Gasto> {
    return this.http.put<Gasto>(`${this.base}/${id}`, request);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
