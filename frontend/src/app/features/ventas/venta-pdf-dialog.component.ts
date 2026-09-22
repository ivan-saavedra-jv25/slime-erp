import { Component, Inject } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';

// El visor sirve para cualquier documento en PDF del ERP: quien lo abre define
// el título (una venta pasa su ventaId y el título se arma solo).
export interface VentaPdfDialogData {
  ventaId?: number;
  titulo?: string;
  url: string;
}

@Component({
  selector: 'app-venta-pdf-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ titulo }}</h2>
    <mat-dialog-content class="pdf-dialog-content">
      <iframe [src]="urlSegura" class="pdf-dialog-frame"></iframe>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-flat-button color="primary" mat-dialog-close>Cerrar</button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .pdf-dialog-content {
        width: 100%;
        height: 80vh;
        padding: 0;
      }

      .pdf-dialog-frame {
        width: 100%;
        height: 100%;
        border: none;
      }
    `,
  ],
})
export class VentaPdfDialogComponent {
  urlSegura: SafeResourceUrl;
  titulo: string;

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: VentaPdfDialogData,
    public dialogRef: MatDialogRef<VentaPdfDialogComponent>,
    sanitizer: DomSanitizer
  ) {
    this.urlSegura = sanitizer.bypassSecurityTrustResourceUrl(`${data.url}#zoom=page-width`);
    this.titulo = data.titulo ?? `Comprobante — Venta #${data.ventaId}`;
  }
}
