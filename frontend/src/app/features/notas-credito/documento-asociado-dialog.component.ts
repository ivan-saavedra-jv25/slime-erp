import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { forkJoin } from 'rxjs';
import { LineaDocumentoOriginal, TipoDocumentoVenta, Venta } from '../../core/models/models';
import { VentaService } from '../../core/services/venta.service';
import { NotaCreditoService } from '../../core/services/nota-credito.service';
import { MonedaPipe } from '../../core/pipes/moneda.pipe';
import { VentaPdfDialogComponent } from '../ventas/venta-pdf-dialog.component';

export interface DocumentoAsociadoDialogData {
  ventaId: number;
  tipo: TipoDocumentoVenta;
  folio: number | null;
  clienteNombre: string | null;
  razon: string | null;
}

const ETIQUETAS_DOCUMENTO: Record<TipoDocumentoVenta, string> = {
  FACTURA: 'Factura',
  BOLETA: 'Boleta',
  VOUCHER: 'Voucher',
};

// Detalle del documento que la nota de crédito corrige, con acceso a su PDF.
// Las líneas se piden al endpoint de la nota de crédito en vez de a /api/ventas
// porque aquel ya resuelve el nombre y el SKU del producto (venta_detalle solo
// guarda el id) y además informa cuánto queda disponible para recuperar.
@Component({
  selector: 'app-documento-asociado-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule, MatIconModule, MonedaPipe],
  template: `
    <h2 mat-dialog-title>{{ titulo }}</h2>

    <mat-dialog-content class="doc-dialog">
      @if (cargando) {
        <p class="empty-state">Cargando documento...</p>
      }
      @if (error) {
        <p class="page-error">{{ error }}</p>
      }

      @if (venta) {
        <dl class="doc-info">
          <div>
            <dt>Tipo</dt>
            <dd>{{ etiquetaTipo }}</dd>
          </div>
          <div>
            <dt>Folio</dt>
            <dd>{{ venta.folio }}</dd>
          </div>
          <div>
            <dt>Fecha</dt>
            <dd>{{ venta.fecha | date: 'dd-MM-yyyy' }}</dd>
          </div>
          <div>
            <dt>Cliente</dt>
            <dd>{{ data.clienteNombre || '—' }}</dd>
          </div>
          @if (data.razon) {
            <div class="span-2">
              <dt>Razón de la corrección</dt>
              <dd>{{ data.razon }}</dd>
            </div>
          }
          @if (venta.observacion) {
            <div class="span-2">
              <dt>Observación</dt>
              <dd>{{ venta.observacion }}</dd>
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
                <th class="right">Descuento</th>
                <th class="right">Subtotal</th>
                <th class="right">Disponible</th>
              </tr>
            </thead>
            <tbody>
              @for (l of lineas; track l.ventaDetalleId) {
                <tr>
                  <td>{{ l.codigo || '—' }}</td>
                  <td>{{ l.descripcion }}</td>
                  <td class="right">{{ l.cantidad }}</td>
                  <td class="right">{{ l.precioUnitario | moneda }}</td>
                  <td class="right">{{ l.descuento | moneda }}</td>
                  <td class="right">{{ l.subtotal | moneda }}</td>
                  <td class="right">{{ l.cantidadDisponible }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>

        <div class="doc-totales">
          <div class="doc-totales__row">
            <span>Neto</span>
            <span>{{ venta.montoNeto | moneda }}</span>
          </div>
          @if (!venta.exento && venta.montoIva > 0) {
            <div class="doc-totales__row">
              <span>IVA</span>
              <span>{{ venta.montoIva | moneda }}</span>
            </div>
          }
          <div class="doc-totales__row doc-totales__row--total">
            <span>Total</span>
            <span>{{ venta.montoTotal | moneda }}</span>
          </div>
        </div>
      }
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button type="button" mat-button mat-dialog-close>Cerrar</button>
      <button type="button" mat-flat-button color="primary" [disabled]="!venta || generandoPdf" (click)="verPdf()">
        <mat-icon>picture_as_pdf</mat-icon>
        {{ generandoPdf ? 'Abriendo...' : 'Ver PDF' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .doc-dialog {
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
export class DocumentoAsociadoDialogComponent {
  venta: Venta | null = null;
  lineas: LineaDocumentoOriginal[] = [];
  cargando = true;
  generandoPdf = false;
  error = '';

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: DocumentoAsociadoDialogData,
    public dialogRef: MatDialogRef<DocumentoAsociadoDialogComponent>,
    private ventaService: VentaService,
    private notaCreditoService: NotaCreditoService,
    private dialog: MatDialog
  ) {
    forkJoin({
      venta: this.ventaService.obtener(data.ventaId),
      lineas: this.notaCreditoService.lineasDocumento(data.ventaId),
    }).subscribe({
      next: ({ venta, lineas }) => {
        this.venta = venta;
        this.lineas = lineas;
        this.cargando = false;
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'No se pudo cargar el documento asociado.';
        this.cargando = false;
      },
    });
  }

  get etiquetaTipo(): string {
    return ETIQUETAS_DOCUMENTO[this.data.tipo];
  }

  get titulo(): string {
    return `${this.etiquetaTipo} N.º ${this.data.folio}`;
  }

  verPdf(): void {
    if (!this.venta || this.generandoPdf) return;
    this.generandoPdf = true;
    this.ventaService.obtenerPdf(this.data.ventaId).subscribe({
      next: (blob) => {
        this.generandoPdf = false;
        const url = URL.createObjectURL(blob);
        const pdfRef = this.dialog.open(VentaPdfDialogComponent, {
          data: { titulo: this.titulo, url },
          width: '90vw',
          maxWidth: '1200px',
        });
        pdfRef.afterClosed().subscribe(() => URL.revokeObjectURL(url));
      },
      error: () => {
        this.error = 'No se pudo cargar el PDF del documento.';
        this.generandoPdf = false;
      },
    });
  }
}
