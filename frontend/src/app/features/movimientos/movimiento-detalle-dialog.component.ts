import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import Swal from 'sweetalert2';
import { MovimientoHistorial } from '../../core/models/models';
import { MovimientoService } from '../../core/services/movimiento.service';
import { mostrarCargando, cerrarCargando } from '../../core/utils/swal-loading';

function descargarBlob(blob: Blob, nombreArchivo: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = nombreArchivo;
  anchor.click();
  URL.revokeObjectURL(url);
}

@Component({
  selector: 'app-movimiento-detalle-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule, MatIconModule],
  template: `
    <h2 mat-dialog-title>Movimiento #{{ data.id }}</h2>
    <mat-dialog-content class="detalle-dialog-content">
      <dl class="detalle-info">
        <div>
          <dt>Fecha</dt>
          <dd>{{ data.fecha }}</dd>
        </div>
        <div>
          <dt>Tipo</dt>
          <dd>
            <span [class]="claseTag(data.tipo)">
              <mat-icon class="tag-icon">{{ iconoTipo(data.tipo) }}</mat-icon>
              {{ data.tipo }}
            </span>
          </dd>
        </div>
        <div>
          <dt>Bodega origen</dt>
          <dd>{{ data.bodegaOrigenNombre || '—' }}</dd>
        </div>
        <div>
          <dt>Bodega destino</dt>
          <dd>{{ data.bodegaDestinoNombre || '—' }}</dd>
        </div>
        <div>
          <dt>Responsable</dt>
          <dd>{{ data.usuarioNombre }}</dd>
        </div>
      </dl>
      @if (data.observacion) {
        <p class="detalle-obs"><strong>Observación:</strong> {{ data.observacion }}</p>
      }

      <table class="detalle-table">
        <thead>
          <tr>
            <th>SKU</th>
            <th>Producto</th>
            <th class="right">Cantidad</th>
          </tr>
        </thead>
        <tbody>
          @for (item of data.items; track item.productoId) {
            <tr>
              <td>{{ item.productoSku || '—' }}</td>
              <td>{{ item.productoNombre }}</td>
              <td class="right">{{ item.cantidad }}</td>
            </tr>
          }
        </tbody>
      </table>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button type="button" mat-button (click)="exportarXlsx()">
        <mat-icon>table_view</mat-icon>
        Excel
      </button>
      <button type="button" mat-button (click)="exportarPdf()">
        <mat-icon>picture_as_pdf</mat-icon>
        PDF
      </button>
      <button type="button" mat-flat-button color="primary" mat-dialog-close>Cerrar</button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .detalle-dialog-content {
        min-width: min(560px, 85vw);
      }

      .detalle-info {
        display: grid;
        grid-template-columns: repeat(2, 1fr);
        gap: var(--space-3) var(--space-4);
        margin: 0 0 var(--space-3);
      }

      .detalle-info dt {
        font: var(--font-legals);
        color: var(--text-muted);
      }

      .detalle-info dd {
        margin: 0;
        font: var(--font-body-sm);
      }

      .detalle-obs {
        font: var(--font-body-sm);
        margin: 0 0 var(--space-3);
      }

      .tag-icon {
        font-size: 16px;
        width: 16px;
        height: 16px;
        vertical-align: text-bottom;
        margin-right: 2px;
      }

      .detalle-table {
        width: 100%;
        border-collapse: collapse;
      }

      .detalle-table th,
      .detalle-table td {
        text-align: left;
        padding: var(--space-1) var(--space-2);
        border-bottom: var(--border-width-default) solid var(--border-default);
        font: var(--font-body-sm);
      }

      .detalle-table th {
        color: var(--text-muted);
        font-weight: 600;
      }

      .right {
        text-align: right;
      }
    `,
  ],
})
export class MovimientoDetalleDialogComponent {
  constructor(
    @Inject(MAT_DIALOG_DATA) public data: MovimientoHistorial,
    public dialogRef: MatDialogRef<MovimientoDetalleDialogComponent>,
    private movimientoService: MovimientoService
  ) {}

  claseTag(tipo: string): string {
    switch (tipo) {
      case 'ENTRADA':
      case 'ENTRADA_COMPRA':
        return 'tag tag--success';
      case 'SALIDA':
      case 'SALIDA_VENTA':
        return 'tag tag--error';
      case 'TRASLADO':
        return 'tag tag--info';
      case 'AJUSTE':
        return 'tag tag--warning';
      default:
        return 'tag';
    }
  }

  iconoTipo(tipo: string): string {
    switch (tipo) {
      case 'ENTRADA':
      case 'ENTRADA_COMPRA':
        return 'input';
      case 'SALIDA':
      case 'SALIDA_VENTA':
        return 'output';
      case 'TRASLADO':
        return 'swap_horiz';
      case 'AJUSTE':
        return 'tune';
      default:
        return 'help';
    }
  }

  exportarXlsx(): void {
    mostrarCargando('Generando Excel');
    this.movimientoService.exportarXlsx(this.data.id).subscribe({
      next: (blob) => {
        cerrarCargando();
        descargarBlob(blob, `movimiento-${this.data.id}.xlsx`);
      },
      error: () => {
        cerrarCargando();
        Swal.fire('No se pudo exportar el Excel', '', 'error');
      },
    });
  }

  exportarPdf(): void {
    mostrarCargando('Generando PDF');
    this.movimientoService.exportarPdf(this.data.id).subscribe({
      next: (blob) => {
        cerrarCargando();
        descargarBlob(blob, `movimiento-${this.data.id}.pdf`);
      },
      error: () => {
        cerrarCargando();
        Swal.fire('No se pudo exportar el PDF', '', 'error');
      },
    });
  }
}
