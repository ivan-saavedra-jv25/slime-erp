import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { Observable } from 'rxjs';
import {
  AccionNotaCredito,
  EstadoNotaCredito,
  NotaCredito,
  RecuperacionInventario,
  TipoCorreccion,
  TipoDocumentoVenta,
  TipoMovimientoNotaCredito,
  TipoMovimientoNotaDebito,
} from '../../core/models/models';
import { NotaCreditoService } from '../../core/services/nota-credito.service';
import { MovimientoService } from '../../core/services/movimiento.service';
import { AuthService } from '../../core/services/auth.service';
import { MonedaPipe } from '../../core/pipes/moneda.pipe';
import { VentaPdfDialogComponent } from '../ventas/venta-pdf-dialog.component';
import { MovimientoDetalleDialogComponent } from '../movimientos/movimiento-detalle-dialog.component';
import { DocumentoAsociadoDialogComponent } from './documento-asociado-dialog.component';
import { ConfirmActionDialog } from '../../core/components/confirm-action-dialog/confirm-action-dialog.component';
import {
  ETIQUETAS_ESTADO,
  ETIQUETAS_MOVIMIENTO,
  ETIQUETAS_RECUPERACION,
  ETIQUETAS_TIPO_CORRECCION,
  TAGS_ESTADO,
  TAGS_MOVIMIENTO,
  TAGS_RECUPERACION,
} from './estado-nota-credito';

const ETIQUETAS_ACCION: Record<AccionNotaCredito, string> = {
  CREADA: 'Nota de crédito creada',
  EDITADA: 'Nota de crédito editada',
  EMITIDA: 'Nota de crédito emitida',
  ANULADA: 'Nota de crédito anulada',
};

const ETIQUETAS_DOCUMENTO: Record<TipoDocumentoVenta, string> = {
  FACTURA: 'Factura',
  BOLETA: 'Boleta',
  VOUCHER: 'Voucher',
};

@Component({
  selector: 'app-nota-credito-detalle',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatDialogModule,
    MonedaPipe,
  ],
  templateUrl: './nota-credito-detalle.component.html',
  styleUrl: './nota-credito-detalle.component.scss',
})
export class NotaCreditoDetalleComponent implements OnInit {
  nota: NotaCredito | null = null;
  cargando = true;
  procesando = false;
  error = '';

  // Confirmación embebida de la emisión: no es reversible sin anular, así que
  // se explica el efecto antes de ejecutarla.
  confirmandoEmision = false;

  constructor(
    private notaCreditoService: NotaCreditoService,
    private route: ActivatedRoute,
    private router: Router,
    private movimientoService: MovimientoService,
    private dialog: MatDialog,
    public auth: AuthService
  ) {}

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => this.cargar(Number(params.get('id'))));
  }

  // --- Estado ---------------------------------------------------------------

  get esBorrador(): boolean {
    return this.nota?.estado === 'BORRADOR';
  }

  get esEmitida(): boolean {
    return this.nota?.estado === 'EMITIDA';
  }

  get puedeEditar(): boolean {
    return this.esBorrador && this.auth.tienePermiso('NOTAS_CREDITO_EDITAR');
  }

  get puedeEmitir(): boolean {
    return this.esBorrador && this.auth.tienePermiso('NOTAS_CREDITO_EDITAR');
  }

  get puedeAnular(): boolean {
    return this.esEmitida && this.auth.tienePermiso('NOTAS_CREDITO_EDITAR');
  }

  get muestraDetalle(): boolean {
    return !!this.nota && this.nota.tipoCorreccion !== 'CORRIGE_TEXTO';
  }

  // --- Acciones -------------------------------------------------------------

  abrirConfirmacionEmision(): void {
    this.confirmandoEmision = true;
  }

  cancelarEmision(): void {
    this.confirmandoEmision = false;
  }

  emitir(): void {
    if (!this.nota) return;
    this.confirmandoEmision = false;
    this.ejecutar(this.notaCreditoService.emitir(this.nota.id));
  }

  anular(): void {
    if (!this.nota) return;
    const dialogRef = this.dialog.open(ConfirmActionDialog, {
      data: {
        titulo: 'Anular nota de crédito',
        entidad: this.nota.numero,
        accion: 'anular',
      },
      width: '480px',
    });
    dialogRef.afterClosed().subscribe((motivo?: string) => {
      // Sin motivo el usuario cerró el diálogo: no se hace nada.
      if (!motivo || !this.nota) return;
      this.ejecutar(this.notaCreditoService.anular(this.nota.id, motivo));
    });
  }

  eliminar(): void {
    if (!this.nota) return;
    const dialogRef = this.dialog.open(ConfirmActionDialog, {
      data: {
        titulo: 'Eliminar borrador',
        entidad: this.nota.numero,
        accion: 'eliminar',
      },
      width: '480px',
    });
    dialogRef.afterClosed().subscribe((motivo?: string) => {
      if (!motivo || !this.nota) return;
      this.procesando = true;
      this.notaCreditoService.eliminar(this.nota.id).subscribe({
        next: () => {
          this.procesando = false;
          this.router.navigate(['/notas-credito']);
        },
        error: (err) => {
          this.error = err?.error?.error ?? 'No se pudo eliminar el borrador.';
          this.procesando = false;
        },
      });
    });
  }

  verPdf(): void {
    if (!this.nota || this.procesando) return;
    this.procesando = true;
    const numero = this.nota.numero;
    this.notaCreditoService.obtenerPdf(this.nota.id).subscribe({
      next: (blob) => {
        this.procesando = false;
        const url = URL.createObjectURL(blob);
        const dialogRef = this.dialog.open(VentaPdfDialogComponent, {
          data: { titulo: `Nota de Crédito ${numero}`, url },
          width: '90vw',
          maxWidth: '1200px',
        });
        dialogRef.afterClosed().subscribe(() => URL.revokeObjectURL(url));
      },
      error: () => {
        this.error = 'No se pudo cargar el PDF de la nota de crédito.';
        this.procesando = false;
      },
    });
  }

  // Abre el detalle del documento corregido, con acceso a su PDF.
  verDocumentoAsociado(): void {
    if (!this.nota) return;
    this.dialog.open(DocumentoAsociadoDialogComponent, {
      data: {
        ventaId: this.nota.documentoAsociado.ventaId,
        tipo: this.nota.documentoAsociado.tipo,
        folio: this.nota.documentoAsociado.folio,
        clienteNombre: this.nota.clienteNombre,
        razon: this.nota.documentoAsociado.razon,
      },
      width: '760px',
      maxWidth: '95vw',
    });
  }

  // Abre el movimiento de inventario en la pantalla del módulo de Inventario,
  // que ya trae el detalle por producto y las exportaciones a PDF y Excel.
  verMovimiento(headerId: number | null): void {
    if (!headerId || this.procesando) return;
    this.procesando = true;
    this.movimientoService.detalle(headerId).subscribe({
      next: (movimiento) => {
        this.procesando = false;
        this.dialog.open(MovimientoDetalleDialogComponent, {
          data: movimiento,
          width: '680px',
          maxWidth: '95vw',
        });
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'No se pudo cargar el movimiento de inventario.';
        this.procesando = false;
      },
    });
  }

  // El nodo "Inventario" de la cadena abre el movimiento de la recuperación;
  // las filas de la tabla abren cada una el suyo.
  get headerMovimientos(): number | null {
    const conHeader = this.nota?.movimientosInventario.find((m) => m.headerId !== null);
    return conHeader?.headerId ?? null;
  }


  // --- Etiquetas ------------------------------------------------------------

  etiquetaEstado(estado: EstadoNotaCredito): string {
    return ETIQUETAS_ESTADO[estado];
  }

  tagEstado(estado: EstadoNotaCredito): string {
    return TAGS_ESTADO[estado];
  }

  etiquetaTipo(tipo: TipoCorreccion): string {
    return ETIQUETAS_TIPO_CORRECCION[tipo];
  }

  etiquetaDocumento(tipo: TipoDocumentoVenta): string {
    return ETIQUETAS_DOCUMENTO[tipo];
  }

  etiquetaAccion(accion: AccionNotaCredito): string {
    return ETIQUETAS_ACCION[accion];
  }

  etiquetaRecuperacion(recuperacion: RecuperacionInventario): string {
    return ETIQUETAS_RECUPERACION[recuperacion];
  }

  tagRecuperacion(recuperacion: RecuperacionInventario): string {
    return TAGS_RECUPERACION[recuperacion];
  }

  etiquetaMovimiento(tipo: TipoMovimientoNotaCredito | TipoMovimientoNotaDebito): string {
    return ETIQUETAS_MOVIMIENTO[tipo as TipoMovimientoNotaCredito];
  }

  tagMovimiento(tipo: TipoMovimientoNotaCredito | TipoMovimientoNotaDebito): string {
    return TAGS_MOVIMIENTO[tipo as TipoMovimientoNotaCredito];
  }

  // --- Carga ----------------------------------------------------------------

  private cargar(id: number): void {
    this.cargando = true;
    this.error = '';
    this.confirmandoEmision = false;
    this.notaCreditoService.obtener(id).subscribe({
      next: (nota) => {
        this.nota = nota;
        this.cargando = false;
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'No se pudo cargar la nota de crédito.';
        this.cargando = false;
      },
    });
  }

  // Las acciones de transición devuelven la nota completa: basta reasignarla
  // para que la pantalla se reconfigure sola.
  private ejecutar(peticion: Observable<NotaCredito>): void {
    if (this.procesando) return;
    this.procesando = true;
    this.error = '';
    peticion.subscribe({
      next: (nota) => {
        this.nota = nota;
        this.procesando = false;
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'No se pudo completar la acción.';
        this.procesando = false;
      },
    });
  }
}
