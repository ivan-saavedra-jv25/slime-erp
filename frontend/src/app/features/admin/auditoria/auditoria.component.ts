import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { AuditoriaService } from '../../../core/services/auditoria.service';
import { EmpresaAdminService } from '../../../core/services/empresa-admin.service';
import { AuditLogResponse, Empresa } from '../../../core/models/models';

const MODULOS: ReadonlyArray<{ valor: string; etiqueta: string }> = [
  { valor: 'empresas', etiqueta: 'Empresas' },
  { valor: 'usuarios', etiqueta: 'Usuarios' },
  { valor: 'suscripciones', etiqueta: 'Suscripciones' },
  { valor: 'planes', etiqueta: 'Planes' },
  { valor: 'pagos', etiqueta: 'Pagos' },
  { valor: 'cobranza', etiqueta: 'Cobranza' },
  { valor: 'soporte', etiqueta: 'Soporte' },
  { valor: 'auditoria', etiqueta: 'Auditoría' },
  { valor: 'configuracion', etiqueta: 'Configuración' },
  { valor: 'seguridad', etiqueta: 'Seguridad' },
  { valor: 'dte', etiqueta: 'DTE' },
  { valor: 'sii', etiqueta: 'SII' },
];

@Component({
  selector: 'app-auditoria',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
  ],
  templateUrl: './auditoria.component.html',
  styleUrl: './auditoria.component.scss',
})
export class AuditoriaComponent implements OnInit {
  registros: AuditLogResponse[] = [];
  cargando = true;
  error = '';
  totalElements = 0;
  totalPages = 0;
  pagina = 0;
  expandidoId: number | null = null;

  filtroModulo = '';
  filtroAction = '';
  filtroEmpresaId: number | null = null;
  filtroDesde = '';
  filtroHasta = '';

  columnas = ['fecha', 'admin', 'empresa', 'modulo', 'accion', 'entidad', 'detalle'];

  readonly modulos = MODULOS;
  empresas: Empresa[] = [];

  private readonly auditoriaService = inject(AuditoriaService);
  private readonly empresaService = inject(EmpresaAdminService);

  ngOnInit(): void {
    this.cargar();
    this.empresaService.listar({ page: 0, limit: 100 }).subscribe({
      next: (pagina) => (this.empresas = pagina.content),
    });
  }

  cargar(): void {
    this.cargando = true;
    this.error = '';
    this.auditoriaService
      .listar({
        page: this.pagina,
        limit: 20,
        modulo: this.filtroModulo || undefined,
        action: this.filtroAction || undefined,
        companyId: this.filtroEmpresaId ?? undefined,
        desde: this.fechaDesdeBackend(),
        hasta: this.fechaHastaBackend(),
      })
      .subscribe({
        next: (resultado) => {
          this.registros = resultado.content;
          this.totalElements = resultado.totalElements;
          this.totalPages = resultado.totalPages;
          this.cargando = false;
        },
        error: (err: unknown) => {
          this.cargando = false;
          this.error =
            (err as { error?: { error?: string } })?.error?.error ??
            'Ocurrió un error al cargar la auditoría.';
        },
      });
  }

  aplicarFiltros(): void {
    this.pagina = 0;
    this.cargar();
  }

  limpiarFiltros(): void {
    this.filtroModulo = '';
    this.filtroAction = '';
    this.filtroEmpresaId = null;
    this.filtroDesde = '';
    this.filtroHasta = '';
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

  toggleDetalle(id: number): void {
    this.expandidoId = this.expandidoId === id ? null : id;
  }

  etiquetaModulo(modulo: string): string {
    return MODULOS.find((m) => m.valor === modulo)?.etiqueta ?? modulo;
  }

  formatoValor(valor: string | null): string {
    if (!valor) return '—';
    try {
      return JSON.stringify(JSON.parse(valor), null, 2);
    } catch {
      return valor;
    }
  }

  hayCambios(registro: AuditLogResponse): boolean {
    return registro.oldValue !== null || registro.newValue !== null;
  }

  private fechaDesdeBackend(): string | undefined {
    return this.filtroDesde ? `${this.filtroDesde}T00:00:00` : undefined;
  }

  private fechaHastaBackend(): string | undefined {
    return this.filtroHasta ? `${this.filtroHasta}T23:59:59` : undefined;
  }
}