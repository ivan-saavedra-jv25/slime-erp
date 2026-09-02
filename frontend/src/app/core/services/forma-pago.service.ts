import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CategoriaFormaPago, FormaPago } from '../models/models';

export interface FormaPagoRequest {
  nombre: string;
  categoria: CategoriaFormaPago;
}

@Injectable({ providedIn: 'root' })
export class FormaPagoService {
  private readonly base = `${environment.apiUrl}/formas-pago`;

  constructor(private http: HttpClient) {}

  listar(): Observable<FormaPago[]> {
    return this.http.get<FormaPago[]>(this.base);
  }

  crear(request: FormaPagoRequest): Observable<FormaPago> {
    return this.http.post<FormaPago>(this.base, request);
  }

  actualizar(id: number, request: FormaPagoRequest): Observable<FormaPago> {
    return this.http.put<FormaPago>(`${this.base}/${id}`, request);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
