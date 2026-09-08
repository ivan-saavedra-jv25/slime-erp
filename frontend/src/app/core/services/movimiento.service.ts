import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { MovimientoHistorial, MovimientoItem, TipoMovimiento } from '../models/models';

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

  historial(): Observable<MovimientoHistorial[]> {
    return this.http.get<MovimientoHistorial[]>(this.base);
  }

  detalle(id: number): Observable<MovimientoHistorial> {
    return this.http.get<MovimientoHistorial>(`${this.base}/${id}`);
  }

  importarExcel(archivo: File): Observable<ImportResultado> {
    const formData = new FormData();
    formData.append('archivo', archivo);
    return this.http.post<ImportResultado>(`${this.base}/importar`, formData);
  }
}
