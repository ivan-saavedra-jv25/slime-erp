import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { PagosService } from '../../../core/services/pagos.service';
import { EmpresaAdminService } from '../../../core/services/empresa-admin.service';
import { AdminAuthService } from '../../../core/services/admin-auth.service';
import { Empresa, PagoPlataforma } from '../../../core/models/models';
import {
  METODOS_PAGO,
  RegistrarPagoDialog,
} from './dialogs/registrar-pago-dialog.component';

export const ETIQUETAS_ESTADO_PAGO: Record<string, string> = {
  PAID: 'Pagado',
  CANCELLED: 'Cancelado',
};

export const CLASES_ESTADO_PAGO: Record<string, string> = {
  PAID: 'tag--success',
  CANCELLED: 'tag--error',
};

@Component({
  selector: 'app-pagos',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatDialogModule,
  ],
  templateUrl: './pagos.component.html',
  styleUrl: './pagos.component.scss',
})
export class PagosComponent implements OnInit {
  pagos: PagoPlataforma[] = [];
  cargando = true;
  error = '';
  totalElements = 0;
  totalPages = 0;
  pagina = 0;

  filtroEmpresaId: number | null = null;
  filtroEstado: string = '';

  columnas = ['fecha', 'empresa', 'monto', 'metodo', 'estado', 'referencia', 'admin'];

  empresas: Empresa[] = [];

  private readonly pagosService = inject(PagosService);
  private readonly empresaService = inject(EmpresaAdminService);
  private readonly dialog = inject(MatDialog);
  readonly auth = inject(AdminAuthService);

  ngOnInit(): void {
    this.cargar();
    this.empresaService.listar({ page: 0, limit: 100 }).subscribe({
      next: (pagina) => (this.empresas = pagina.content),
    });
  }

  cargar(): void {
    this.cargando = true;
    this.error = '';
    const estado = this.filtroEstado === '' ? undefined : this.filtroEstado;
    this.pagosService
      .listar({
        page: this.pagina,
        limit: 20,
        empresaId: this.filtroEmpresaId ?? undefined,
        estado: estado as never,
      })
      .subscribe({
        next: (resultado) => {
          this.pagos = resultado.content;
          this.totalElements = resultado.totalElements;
          this.totalPages = resultado.totalPages;
          this.cargando = false;
        },
        error: (err: unknown) => {
          this.cargando = false;
          this.error =
            (err as { error?: { error?: string } })?.error?.error ??
            'Ocurrió un error al cargar los pagos.';
        },
      });
  }

  aplicarFiltros(): void {
    this.pagina = 0;
    this.cargar();
  }

  limpiarFiltros(): void {
    this.filtroEmpresaId = null;
    this.filtroEstado = '';
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

  puedeRegistrar(): boolean {
    return this.auth.tienePermiso('PAGOS_EDITAR');
  }

  etiquetaEstado(estado: string): string {
    return ETIQUETAS_ESTADO_PAGO[estado] ?? estado;
  }

  claseEstado(estado: string): string {
    return CLASES_ESTADO_PAGO[estado] ?? 'tag--muted';
  }

  etiquetaMetodo(metodo: string): string {
    return METODOS_PAGO.find((m) => m.valor === metodo)?.etiqueta ?? metodo;
  }

  registrarPago(): void {
    this.error = '';
    this.dialog
      .open(RegistrarPagoDialog, { width: '520px' })
      .afterClosed()
      .subscribe((request?: unknown) => {
        if (!request) return;
        this.pagosService.registrar(request as never).subscribe({
          next: () => this.cargar(),
          error: (err: unknown) => {
            this.error =
              (err as { error?: { error?: string } })?.error?.error ??
              'Ocurrió un error al registrar el pago.';
          },
        });
      });
  }
}