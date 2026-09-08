import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LibroVentasResponse } from '../models/models';

@Injectable({ providedIn: 'root' })
export class ReporteService {
  private readonly base = `${environment.apiUrl}/reportes`;

  constructor(private http: HttpClient) {}

  libroVentas(desde: string, hasta: string): Observable<LibroVentasResponse> {
    return this.http.get<LibroVentasResponse>(`${this.base}/libro-ventas`, { params: { desde, hasta } });
  }

  libroVentasExcel(desde: string, hasta: string): Observable<Blob> {
    return this.http.get(`${this.base}/libro-ventas/excel`, {
      params: { desde, hasta },
      responseType: 'blob',
    });
  }
}
