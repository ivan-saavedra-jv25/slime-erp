import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { InventarioConsultaItem, PaginaResponse, TipoBusquedaInventario } from '../models/models';

export interface InventarioFiltro {
  bodegaId: number | null;
  familiaId: number | null;
  subfamiliaId: number | null;
  verDeshabilitados: boolean;
  tipoBusqueda: TipoBusquedaInventario;
  busqueda: string;
}

@Injectable({ providedIn: 'root' })
export class InventarioService {
  private readonly base = `${environment.apiUrl}/inventario`;

  constructor(private http: HttpClient) {}

  listar(
    filtro: InventarioFiltro,
    sort: string,
    dir: 'asc' | 'desc',
    pagina: number,
    tamano: number
  ): Observable<PaginaResponse<InventarioConsultaItem>> {
    const params = this.parametrosFiltro(filtro)
      .set('sort', sort)
      .set('dir', dir)
      .set('pagina', pagina)
      .set('tamano', tamano);
    return this.http.get<PaginaResponse<InventarioConsultaItem>>(this.base, { params });
  }

  exportarCsv(filtro: InventarioFiltro): Observable<Blob> {
    return this.http.get(`${this.base}/exportar.csv`, {
      params: this.parametrosFiltro(filtro),
      responseType: 'blob',
    });
  }

  exportarXlsx(filtro: InventarioFiltro): Observable<Blob> {
    return this.http.get(`${this.base}/exportar.xlsx`, {
      params: this.parametrosFiltro(filtro),
      responseType: 'blob',
    });
  }

  private parametrosFiltro(filtro: InventarioFiltro): HttpParams {
    let params = new HttpParams()
      .set('verDeshabilitados', filtro.verDeshabilitados)
      .set('tipoBusqueda', filtro.tipoBusqueda);
    if (filtro.bodegaId != null) params = params.set('bodegaId', filtro.bodegaId);
    if (filtro.familiaId != null) params = params.set('familiaId', filtro.familiaId);
    if (filtro.subfamiliaId != null) params = params.set('subfamiliaId', filtro.subfamiliaId);
    if (filtro.busqueda) params = params.set('busqueda', filtro.busqueda);
    return params;
  }
}
