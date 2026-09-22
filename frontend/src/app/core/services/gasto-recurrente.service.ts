import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { FrecuenciaGastoRecurrente, GastoRecurrente } from '../models/models';

export interface GastoRecurrenteRequest {
  categoriaGastoId: number;
  monto: number;
  descripcion: string;
  frecuencia: FrecuenciaGastoRecurrente;
  diaMes: number | null;
  fechaInicio: string;
  fechaFin: string | null;
}

@Injectable({ providedIn: 'root' })
export class GastoRecurrenteService {
  private readonly base = `${environment.apiUrl}/gastos/recurrentes`;

  constructor(private http: HttpClient) {}

  listar(): Observable<GastoRecurrente[]> {
    return this.http.get<GastoRecurrente[]>(this.base);
  }

  crear(request: GastoRecurrenteRequest): Observable<GastoRecurrente> {
    return this.http.post<GastoRecurrente>(this.base, request);
  }

  actualizar(id: number, request: GastoRecurrenteRequest): Observable<GastoRecurrente> {
    return this.http.put<GastoRecurrente>(`${this.base}/${id}`, request);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
