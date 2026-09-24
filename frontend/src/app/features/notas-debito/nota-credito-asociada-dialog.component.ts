import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { NotaCredito } from '../../core/models/models';
import { NotaCreditoService } from '../../core/services/nota-credito.service';
import { MonedaPipe } from '../../core/pipes/moneda.pipe';
import { VentaPdfDialogComponent } from '../ventas/venta-pdf-dialog.component';

export interface NotaCreditoAsociadaDialogData {
  notaCreditoId: number;
  numero: string;
  tipo: string;
  folio: number | null;
  fecha: string | null;
  montoTotal: number | null;
  montoDisponible: number | null;
  razon: string | null;
  clienteNombre?: string | null;
}

// Detalle de la nota de crédito que la nota de débito revierte, con acceso a su
// PDF. Recibe los datos ya cargados por el componente padre (documentoAsociado)
// y completa con `obtener(id)` la información que este no trae.
@Component({
  selector: 'app-nota-credito-asociada-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule, MatIconModule, MonedaPipe],
  template: `
    <h2 mat-dialog-title>{{ data.numero }}</h2>

    <mat-dialog-content class="nc-dialog">
      @if (cargando) {
        <p class="empty-state">Cargando nota de crédito...</p>
      }
      @if (error) {
        <p class="page-error">{{ error }}</p>
      }

      @if (nc) {
        <dl class="doc-info">
          <div>
            <dt>Número</dt>
            <dd>{{ nc.numero }}</dd>
          </div>
          <div>
            <dt>Fecha</dt>
            <dd>{{ nc.fecha | date: 'dd-MM-yyyy' }}</dd>
          </div>
          <div>
            <dt>Cliente</dt>
            <dd>{{ nc.clienteNombre || data.clienteNombre || '—' }}</dd>
          </div>
          <div>
            <dt>Total</dt>
            <dd>{{ nc.montoTotal | moneda }}</dd>
          </div>
          <div>
            <dt>Disponible para revertir</dt>
            <dd>{{ data.montoDisponible != null ? (data.montoDisponible | moneda) : '—' }}</dd>
          </div>
          <div>
            <dt>Documento original</dt>
            <dd>{{ data.tipo }} N.º {{ data.folio }}</dd>
          </div>
          @if (data.razon) {
            <div class="span-2">
              <dt>Razón de la reversión</dt>
              <dd>{{ data.razon }}</dd>
            </div>
          }
        </dl>

        <div class="table-scroll">
          <table class="doc-table">
            <thead>
              <tr>
                <th>Código</th>
                <th>Descripción</th>
                <th class="right">Cantidad</th>
                <th class="right">Precio unit.</th>
                <th class="right">Subtotal</th>
              </tr>
            </thead>
            <tbody>
              @for (l of nc.lineas; track l.id) {
                <tr>
                  <td>{{ l.codigo || '—' }}</td>
                  <td>{{ l.descripcion }}</td>
                  <td class="right">{{ l.cantidad }}</td>
                  <td class="right">{{ l.precioUnitario | moneda }}</td>
                  <td class="right">{{ l.subtotal | moneda }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>

        <div class="doc-totales">
          <div class="doc-totales__row">
            <span>Neto</span>
            <span>{{ nc.montoNeto | moneda }}</span>
          </div>
          @if (!nc.exenta && nc.montoIva > 0) {
            <div class="doc-totales__row">
              <span>IVA</span>
              <span>{{ nc.montoIva | moneda }}</span>
            </div>
          }
          <div class="doc-totales__row doc-totales__row--total">
            <span>Total</span>
            <span>{{ nc.montoTotal | moneda }}</span>
          </div>
        </div>
      }
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button type="button" mat-button mat-dialog-close>Cerrar</button>
      <button type="button" mat-flat-button color="primary" [disabled]="!nc || generandoPdf" (click)="verPdf()">
        <mat-icon>picture_as_pdf</mat-icon>
        {{ generandoPdf ? 'Abriendo...' : 'Ver PDF' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .nc-dialog {
        min-width: min(680px, 80vw);
      }

      .doc-info {
        display: grid;
        grid-template-columns: repeat(2, 1fr);
        gap: var(--space-3);
        margin: 0 0 var(--space-4);
      }

      .doc-info .span-2 {
        grid-column: span 2;
      }

      .doc-info dt {
        font: var(--font-body-sm);
        color: var(--text-muted);
      }

      .doc-info dd {
        margin: 0;
        font: var(--font-body);
        font-weight: 600;
        color: var(--text-title);
      }

      .table-scroll {
        overflow-x: auto;
      }

      .doc-table {
        width: 100%;
        border-collapse: collapse;
      }

      .doc-table th,
      .doc-table td {
        text-align: left;
        padding: var(--space-2) var(--space-3);
        border-bottom: var(--border-width-default) solid var(--border-default);
        font: var(--font-body-sm);
      }

      .doc-table th {
        color: var(--text-muted);
        font-weight: 600;
      }

      .doc-table .right {
        text-align: right;
      }

      .doc-totales {
        display: flex;
        flex-direction: column;
        align-items: flex-end;
        gap: var(--space-2);
        margin-top: var(--space-4);
      }

      .doc-totales__row {
        display: flex;
        justify-content: space-between;
        min-width: 220px;
        font: var(--font-body-sm);
        color: var(--text-body);
      }

      .doc-totales__row--total {
        font: var(--font-h3);
        font-weight: 700;
        color: var(--text-title);
        border-top: var(--border-width-default) solid var(--border-default);
        padding-top: var(--space-2);
      }

      @media (max-width: 560px) {
        .doc-info {
          grid-template-columns: 1fr;
        }

        .doc-info .span-2 {
          grid-column: span 1;
        }
      }
    `,
  ],
})
export class NotaCreditoAsociadaDialogComponent {
  nc: NotaCredito | null = null;
  cargando = true;
  generandoPdf = false;
  error = '';

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: NotaCreditoAsociadaDialogData,
    public dialogRef: MatDialogRef<NotaCreditoAsociadaDialogComponent>,
    private notaCreditoService: NotaCreditoService,
    private dialog: MatDialog
  ) {
    this.notaCreditoService.obtener(data.notaCreditoId).subscribe({
      next: (nc) => {
        this.nc = nc;
        this.cargando = false;
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'No se pudo cargar la nota de crédito asociada.';
        this.cargando = false;
      },
    });
  }

  get titulo(): string {
    return this.data.numero;
  }

  verPdf(): void {
    if (!this.nc || this.generandoPdf) return;
    this.generandoPdf = true;
    this.notaCreditoService.obtenerPdf(this.data.notaCreditoId).subscribe({
      next: (blob) => {
        this.generandoPdf = false;
        const url = URL.createObjectURL(blob);
        const pdfRef = this.dialog.open(VentaPdfDialogComponent, {
          data: { titulo: `Nota de Crédito ${this.data.numero}`, url },
          width: '90vw',
          maxWidth: '1200px',
        });
        pdfRef.afterClosed().subscribe(() => URL.revokeObjectURL(url));
      },
      error: () => {
        this.error = 'No se pudo cargar el PDF de la nota de crédito.';
        this.generandoPdf = false;
      },
    });
  }
}