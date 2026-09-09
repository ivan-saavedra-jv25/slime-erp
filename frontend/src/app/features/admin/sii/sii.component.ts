import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { SiiService } from '../../../core/services/sii.service';
import { SiiEstado } from '../../../core/models/models';
import { DetalleSiiDialog } from './dialogs/detalle-sii-dialog.component';

export const ETIQUETAS_ESTADO_SII: Record<string, string> = {
  CONFIGURED: 'Configurado',
  NOT_CONFIGURED: 'No configurado',
  CERTIFICATE_EXPIRING: 'Certificado por vencer',
  CERTIFICATE_EXPIRED: 'Certificado vencido',
  CONNECTION_ERROR: 'Error de conexión',
};

export const CLASES_ESTADO_SII: Record<string, string> = {
  CONFIGURED: 'tag--success',
  NOT_CONFIGURED: 'tag--muted',
  CERTIFICATE_EXPIRING: 'tag--warning',
  CERTIFICATE_EXPIRED: 'tag--error',
  CONNECTION_ERROR: 'tag--error',
};

@Component({
  selector: 'app-sii',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
  ],
  templateUrl: './sii.component.html',
  styleUrl: './sii.component.scss',
})
export class SiiComponent implements OnInit {
  empresas: SiiEstado[] = [];
  cargando = true;
  error = '';
  totalElements = 0;
  totalPages = 0;
  pagina = 0;

  filtroEstado: string = '';

  columnas = ['empresa', 'estado', 'certificado', 'ultimaComunicacion', 'ultimoDte', 'acciones'];

  readonly estados = Object.keys(ETIQUETAS_ESTADO_SII);

  private readonly siiService = inject(SiiService);
  private readonly dialog = inject(MatDialog);

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cargando = true;
    this.error = '';
    this.siiService
      .listar({
        page: this.pagina,
        limit: 20,
        estado: this.filtroEstado || undefined,
      })
      .subscribe({
        next: (resultado) => {
          this.empresas = resultado.content;
          this.totalElements = resultado.totalElements;
          this.totalPages = resultado.totalPages;
          this.cargando = false;
        },
        error: (err: unknown) => {
          this.cargando = false;
          this.error =
            (err as { error?: { error?: string } })?.error?.error ??
            'Ocurrió un error al cargar el estado SII.';
        },
      });
  }

  aplicarFiltros(): void {
    this.pagina = 0;
    this.cargar();
  }

  limpiarFiltros(): void {
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

  etiquetaEstado(estado: string): string {
    return ETIQUETAS_ESTADO_SII[estado] ?? estado;
  }

  claseEstado(estado: string): string {
    return CLASES_ESTADO_SII[estado] ?? 'tag--muted';
  }

  formatearFecha(fecha: string): string {
    return fecha ? new Date(fecha).toLocaleDateString('es-CL') : '—';
  }

  verDetalle(e: SiiEstado): void {
    this.dialog.open(DetalleSiiDialog, { width: '480px', data: e.empresaId });
  }
}