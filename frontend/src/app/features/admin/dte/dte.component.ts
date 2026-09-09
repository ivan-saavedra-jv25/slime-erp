import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { DteService } from '../../../core/services/dte.service';
import { EmpresaAdminService } from '../../../core/services/empresa-admin.service';
import { DteDashboard, DocumentoDte, Empresa } from '../../../core/models/models';

export const ETIQUETAS_TIPO_DTE: Record<string, string> = {
  BOLETA: 'Boleta',
  FACTURA: 'Factura',
  VOUCHER: 'Voucher',
};

export const CLASES_ESTADO_DTE: Record<string, string> = {
  EMITIDA: 'tag--success',
  ANULADA: 'tag--error',
};

@Component({
  selector: 'app-dte',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
  ],
  templateUrl: './dte.component.html',
  styleUrl: './dte.component.scss',
})
export class DteComponent implements OnInit {
  dtes: DocumentoDte[] = [];
  resumen: DteDashboard | null = null;
  cargando = true;
  error = '';
  totalElements = 0;
  totalPages = 0;
  pagina = 0;

  empresas: Empresa[] = [];

  filtroEmpresaId: number | null = null;
  filtroTipo: string = '';
  filtroEstado: string = '';
  filtroDesde = '';
  filtroHasta = '';
  filtroFolio: number | null = null;
  filtroRut = '';

  columnas = ['fecha', 'empresa', 'tipo', 'folio', 'receptor', 'monto', 'estado', 'acciones'];

  private readonly dteService = inject(DteService);
  private readonly empresaService = inject(EmpresaAdminService);
  private readonly router = inject(Router);

  ngOnInit(): void {
    this.cargar();
    this.cargarResumen();
    this.empresaService.listar({ page: 0, limit: 100 }).subscribe({
      next: (pagina) => (this.empresas = pagina.content),
    });
  }

  cargar(): void {
    this.cargando = true;
    this.error = '';
    this.dteService
      .listar({
        page: this.pagina,
        limit: 20,
        empresaId: this.filtroEmpresaId ?? undefined,
        tipoDte: this.filtroTipo || undefined,
        estado: this.filtroEstado || undefined,
        fechaDesde: this.filtroDesde || undefined,
        fechaHasta: this.filtroHasta || undefined,
        folio: this.filtroFolio ?? undefined,
        rutReceptor: this.filtroRut || undefined,
      })
      .subscribe({
        next: (resultado) => {
          this.dtes = resultado.content;
          this.totalElements = resultado.totalElements;
          this.totalPages = resultado.totalPages;
          this.cargando = false;
        },
        error: (err: unknown) => {
          this.cargando = false;
          this.error =
            (err as { error?: { error?: string } })?.error?.error ??
            'Ocurrió un error al cargar los documentos.';
        },
      });
  }

  cargarResumen(): void {
    this.dteService
      .dashboard({
        empresaId: this.filtroEmpresaId ?? undefined,
        tipoDte: this.filtroTipo || undefined,
        fechaDesde: this.filtroDesde || undefined,
        fechaHasta: this.filtroHasta || undefined,
      })
      .subscribe({
        next: (resumen) => (this.resumen = resumen),
        error: () => (this.resumen = null),
      });
  }

  aplicarFiltros(): void {
    this.pagina = 0;
    this.cargar();
    this.cargarResumen();
  }

  limpiarFiltros(): void {
    this.filtroEmpresaId = null;
    this.filtroTipo = '';
    this.filtroEstado = '';
    this.filtroDesde = '';
    this.filtroHasta = '';
    this.filtroFolio = null;
    this.filtroRut = '';
    this.aplicarFiltros();
  }

  paginaAnterior(): void {
    if (this.pagina > 0) {
      this.pagina--;
      this.cargar();
    }
  }

  paginaSiguiente(): void {
    if (this.pagina < this.totalPages - 1) {
      this.pagina++;
      this.cargar();
    }
  }

  etiquetaTipo(tipo: string): string {
    return ETIQUETAS_TIPO_DTE[tipo] ?? tipo;
  }

  claseEstado(estado: string): string {
    return CLASES_ESTADO_DTE[estado] ?? 'tag--muted';
  }

  formatearFecha(fecha: string): string {
    return new Date(fecha).toLocaleDateString('es-CL');
  }

  verEmpresa(d: DocumentoDte): void {
    this.router.navigate(['/admin/empresas', d.empresaId]);
  }
}