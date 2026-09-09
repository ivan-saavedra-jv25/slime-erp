import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatDialogModule, MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { SiiService } from '../../../../core/services/sii.service';
import { SiiEstado } from '../../../../core/models/models';
import {
  ETIQUETAS_ESTADO_SII,
  CLASES_ESTADO_SII,
} from '../sii.component';

@Component({
  selector: 'app-detalle-sii-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule, MatIconModule],
  templateUrl: './detalle-sii-dialog.component.html',
  styleUrl: './detalle-sii-dialog.component.scss',
})
export class DetalleSiiDialog implements OnInit {
  private readonly empresaId = inject<number>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<DetalleSiiDialog>>(MatDialogRef);
  private readonly siiService = inject(SiiService);

  detalle: SiiEstado | null = null;
  cargando = true;
  error = '';

  ngOnInit(): void {
    this.siiService.detalle(this.empresaId).subscribe({
      next: (detalle) => {
        this.detalle = detalle;
        this.cargando = false;
      },
      error: (err: unknown) => {
        this.cargando = false;
        this.error =
          (err as { error?: { error?: string } })?.error?.error ??
          'Ocurrió un error al cargar el detalle SII.';
      },
    });
  }

  etiquetaEstado(estado: string): string {
    return ETIQUETAS_ESTADO_SII[estado] ?? estado;
  }

  claseEstado(estado: string): string {
    return CLASES_ESTADO_SII[estado] ?? 'tag--muted';
  }

  formatearFecha(fecha: string | null): string {
    return fecha ? new Date(fecha).toLocaleDateString('es-CL') : '—';
  }

  cerrar(): void {
    this.dialogRef.close();
  }
}