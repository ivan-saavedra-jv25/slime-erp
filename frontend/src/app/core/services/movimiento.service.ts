import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { MovimientoHistorial, MovimientoItem, TipoMovimiento } from '../models/models';

export interface MovimientoHistorialFiltro {
  fechaDesde: string | null;
  fechaHasta: string | null;
  usuarioId: number | null;
  bodegaId: number | null;
}

export interface MovimientoRequest {
  tipo: TipoMovimiento;
  bodegaOrigenId: number | null;
  bodegaDestinoId: number | null;
  observacion?: string;
  items: MovimientoItem[];
  responsableId: number | null;
}

export interface ImportFilaError {
  numeroFila: number;
  mensaje: string;
}

export interface ImportItemResuelto {
  productoId: number;
  productoSku: string | null;
  productoNombre: string;
  cantidad: number;
}

export interface ImportResultado {
  totalFilas: number;
  items: ImportItemResuelto[];
  errores: ImportFilaError[];
}

@Injectable({ providedIn: 'root' })
export class MovimientoService {
  private readonly base = `${environment.apiUrl}/movimientos`;

  constructor(private http: HttpClient) {}

  crear(request: MovimientoRequest): Observable<{ id: number; mensaje: string }> {
    return this.http.post<{ id: number; mensaje: string }>(this.base, request);
  }

  historial(filtro?: MovimientoHistorialFiltro): Observable<MovimientoHistorial[]> {
    let params = new HttpParams();
    if (filtro?.fechaDesde) params = params.set('fechaDesde', filtro.fechaDesde);
    if (filtro?.fechaHasta) params = params.set('fechaHasta', filtro.fechaHasta);
    if (filtro?.usuarioId != null) params = params.set('usuarioId', filtro.usuarioId);
    if (filtro?.bodegaId != null) params = params.set('bodegaId', filtro.bodegaId);
    return this.http.get<MovimientoHistorial[]>(this.base, { params });
  }

  detalle(id: number): Observable<MovimientoHistorial> {
    return this.http.get<MovimientoHistorial>(`${this.base}/${id}`);
  }

  exportarXlsx(id: number): Observable<Blob> {
    return this.http.get(`${this.base}/${id}/exportar.xlsx`, { responseType: 'blob' });
  }

  exportarPdf(id: number): Observable<Blob> {
    return this.http.get(`${this.base}/${id}/exportar.pdf`, { responseType: 'blob' });
  }

  importarExcel(archivo: File): Observable<ImportResultado> {
    const formData = new FormData();
    formData.append('archivo', archivo);
    return this.http.post<ImportResultado>(`${this.base}/importar`, formData);
  }
}
