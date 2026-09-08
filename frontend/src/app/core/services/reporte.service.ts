import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LibroVentasResponse } from '../models/models';

@Injectable({ providedIn: 'root' })
export class ReporteService {
  private readonly base = `${environment.apiUrl}/reportes`;

  constructor(private http: HttpClient) {}

  libroVentas(
    desde: string,
    hasta: string,
    tipoDocumento?: string | null,
    q?: string | null,
    pagina = 0,
    tamano = 10
  ): Observable<LibroVentasResponse> {
    const params = this.parametrosPeriodo(desde, hasta, tipoDocumento, q)
      .set('pagina', pagina)
      .set('tamano', tamano);
    return this.http.get<LibroVentasResponse>(`${this.base}/libro-ventas`, { params });
  }

  libroVentasExcel(desde: string, hasta: string, tipoDocumento?: string | null, q?: string | null): Observable<Blob> {
    return this.http.get(`${this.base}/libro-ventas/excel`, {
      params: this.parametrosPeriodo(desde, hasta, tipoDocumento, q),
      responseType: 'blob',
    });
  }

  private parametrosPeriodo(desde: string, hasta: string, tipoDocumento?: string | null, q?: string | null): HttpParams {
    let params = new HttpParams().set('desde', desde).set('hasta', hasta);
    if (tipoDocumento) {
      params = params.set('tipoDocumento', tipoDocumento);
    }
    if (q) {
      params = params.set('q', q);
    }
    return params;
  }
}
