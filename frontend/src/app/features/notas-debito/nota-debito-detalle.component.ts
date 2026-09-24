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
  AccionNotaDebito,
  EslabonCadenaDocumento,
  EstadoNotaDebito,
  ImpactoInventario,
  NotaDebito,
  TipoDocumentoVenta,
  TipoMovimientoNotaCredito,
  TipoMovimientoNotaDebito,
  TipoReversion,
} from '../../core/models/models';
import { NotaDebitoService } from '../../core/services/nota-debito.service';
import { MovimientoService } from '../../core/services/movimiento.service';
import { AuthService } from '../../core/services/auth.service';
import { MonedaPipe } from '../../core/pipes/moneda.pipe';
import { VentaPdfDialogComponent } from '../ventas/venta-pdf-dialog.component';
import { MovimientoDetalleDialogComponent } from '../movimientos/movimiento-detalle-dialog.component';
import { ConfirmActionDialog } from '../../core/components/confirm-action-dialog/confirm-action-dialog.component';
import { NotaCreditoAsociadaDialogComponent } from './nota-credito-asociada-dialog.component';
import { DocumentoAsociadoDialogComponent } from '../notas-credito/documento-asociado-dialog.component';
import {
  ETIQUETAS_ESTADO,
  ETIQUETAS_IMPACTO,
  ETIQUETAS_MOVIMIENTO,
  ETIQUETAS_TIPO_REVERSION,
  TAGS_ESTADO,
  TAGS_IMPACTO,
  TAGS_MOVIMIENTO,
} from './estado-nota-debito';

const ETIQUETAS_ACCION: Record<AccionNotaDebito, string> = {
  CREADA: 'Nota de débito creada',
  EDITADA: 'Nota de débito editada',
  EMITIDA: 'Nota de débito emitida',
  ANULADA: 'Nota de débito anulada',
};

const ETIQUETAS_DOCUMENTO: Record<TipoDocumentoVenta, string> = {
  FACTURA: 'Factura',
  BOLETA: 'Boleta',
  VOUCHER: 'Voucher',
};

const ETIQUETAS_ESLABON: Record<EslabonCadenaDocumento['tipo'], string> = {
  COTIZACION: 'Cotización',
  NOTA_VENTA: 'Nota de Venta',
  VENTA: 'Venta',
  NOTA_CREDITO: 'Nota de Crédito',
};

@Component({
  selector: 'app-nota-debito-detalle',
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
  templateUrl: './nota-debito-detalle.component.html',
  styleUrl: './nota-debito-detalle.component.scss',
})
export class NotaDebitoDetalleComponent implements OnInit {
  nota: NotaDebito | null = null;
  cargando = true;
  procesando = false;
  error = '';

  // Cadena de documentos asociados (Cotización -> Nota de Venta -> Venta ->
  // Nota de Crédito), de arriba hacia abajo. La nota de débito y el inventario
  // se pintan con su propia información después del último eslabón.
  cadena: EslabonCadenaDocumento[] = [];

  // Confirmación embebida de la emisión: no es reversible sin anular, así que
  // se explica el efecto antes de ejecutarla.
  confirmandoEmision = false;

  constructor(
    private notaDebitoService: NotaDebitoService,
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
    return this.esBorrador && this.auth.tienePermiso('NOTAS_DEBITO_EDITAR');
  }

  get puedeEmitir(): boolean {
    return this.esBorrador && this.auth.tienePermiso('NOTAS_DEBITO_EDITAR');
  }

  get puedeAnular(): boolean {
    return this.esEmitida && this.auth.tienePermiso('NOTAS_DEBITO_EDITAR');
  }

  get muestraDetalle(): boolean {
    return !!this.nota && this.nota.tipoReversion !== 'REVIERTE_TEXTO';
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
    this.ejecutar(this.notaDebitoService.emitir(this.nota.id));
  }

  anular(): void {
    if (!this.nota) return;
    const dialogRef = this.dialog.open(ConfirmActionDialog, {
      data: {
        titulo: 'Anular nota de débito',
        entidad: this.nota.numero,
        accion: 'anular',
      },
      width: '480px',
    });
    dialogRef.afterClosed().subscribe((motivo?: string) => {
      // Sin motivo el usuario cerró el diálogo: no se hace nada.
      if (!motivo || !this.nota) return;
      this.ejecutar(this.notaDebitoService.anular(this.nota.id, motivo));
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
      this.notaDebitoService.eliminar(this.nota.id).subscribe({
        next: () => {
          this.procesando = false;
          this.router.navigate(['/notas-debito']);
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
    this.notaDebitoService.obtenerPdf(this.nota.id).subscribe({
      next: (blob) => {
        this.procesando = false;
        const url = URL.createObjectURL(blob);
        const dialogRef = this.dialog.open(VentaPdfDialogComponent, {
          data: { titulo: `Nota de Débito ${numero}`, url },
          width: '90vw',
          maxWidth: '1200px',
        });
        dialogRef.afterClosed().subscribe(() => URL.revokeObjectURL(url));
      },
      error: () => {
        this.error = 'No se pudo cargar el PDF de la nota de débito.';
        this.procesando = false;
      },
    });
  }

  // Abre el detalle de la nota de crédito revertida, con acceso a su PDF.
  verNotaCreditoAsociada(): void {
    if (!this.nota) return;
    this.dialog.open(NotaCreditoAsociadaDialogComponent, {
      data: {
        notaCreditoId: this.nota.documentoAsociado.notaCreditoId,
        numero: this.nota.documentoAsociado.numero,
        tipo: this.nota.documentoAsociado.ncDocAsociadoTipo,
        folio: this.nota.documentoAsociado.ncFolio,
        fecha: this.nota.documentoAsociado.fecha,
        montoTotal: this.nota.documentoAsociado.montoTotal,
        montoDisponible: this.nota.documentoAsociado.montoDisponible,
        razon: this.nota.documentoAsociado.razon,
      },
      width: '760px',
      maxWidth: '95vw',
    });
  }

  // La Venta que dio origen a la nota de crédito: usa el snapshot de la ND
  // (tipo, folio, razón) y el nombre del cliente de la nota.
  verVentaAsociada(eslabon: EslabonCadenaDocumento): void {
    if (!this.nota) return;
    this.dialog.open(DocumentoAsociadoDialogComponent, {
      data: {
        ventaId: eslabon.documentoId,
        tipo: this.nota.documentoAsociado.ncDocAsociadoTipo,
        folio: this.nota.documentoAsociado.ncFolio,
        clienteNombre: this.nota.clienteNombre,
        razon: this.nota.documentoAsociado.razon,
      },
      width: '860px',
      maxWidth: '95vw',
    });
  }

  // Cada eslabón de la cadena abre su documento: la venta (diálogo con sus
  // líneas y PDF) y la nota de venta/cotización (su pantalla de detalle).
  verEslabon(eslabon: EslabonCadenaDocumento): void {
    if (this.procesando) return;
    switch (eslabon.tipo) {
      case 'COTIZACION':
        this.router.navigate(['/cotizaciones', eslabon.documentoId]);
        break;
      case 'NOTA_VENTA':
        this.router.navigate(['/notas-venta', eslabon.documentoId]);
        break;
      case 'VENTA':
        this.verVentaAsociada(eslabon);
        break;
      case 'NOTA_CREDITO':
        this.verNotaCreditoAsociada();
        break;
    }
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

  // El nodo "Inventario" de la cadena abre el movimiento de la reversión; las
  // filas de la tabla abren cada una el suyo.
  get headerMovimientos(): number | null {
    const conHeader = this.nota?.movimientosInventario.find((m) => m.headerId !== null);
    return conHeader?.headerId ?? null;
  }

  // --- Etiquetas ------------------------------------------------------------

  etiquetaEstado(estado: EstadoNotaDebito): string {
    return ETIQUETAS_ESTADO[estado];
  }

  tagEstado(estado: EstadoNotaDebito): string {
    return TAGS_ESTADO[estado];
  }

  etiquetaTipo(tipo: TipoReversion): string {
    return ETIQUETAS_TIPO_REVERSION[tipo];
  }

  etiquetaDocumento(tipo: TipoDocumentoVenta): string {
    return ETIQUETAS_DOCUMENTO[tipo];
  }

  etiquetaEslabon(tipo: EslabonCadenaDocumento['tipo']): string {
    return ETIQUETAS_ESLABON[tipo];
  }

  etiquetaAccion(accion: AccionNotaDebito): string {
    return ETIQUETAS_ACCION[accion];
  }

  etiquetaImpacto(impacto: ImpactoInventario): string {
    return ETIQUETAS_IMPACTO[impacto];
  }

  tagImpacto(impacto: ImpactoInventario): string {
    return TAGS_IMPACTO[impacto];
  }

  etiquetaMovimiento(tipo: TipoMovimientoNotaCredito | TipoMovimientoNotaDebito): string {
    return ETIQUETAS_MOVIMIENTO[tipo as TipoMovimientoNotaDebito];
  }

  tagMovimiento(tipo: TipoMovimientoNotaCredito | TipoMovimientoNotaDebito): string {
    return TAGS_MOVIMIENTO[tipo as TipoMovimientoNotaDebito];
  }

  // --- Carga ----------------------------------------------------------------

  private cargar(id: number): void {
    this.cargando = true;
    this.error = '';
    this.confirmandoEmision = false;
    this.notaDebitoService.obtener(id).subscribe({
      next: (nota) => {
        this.nota = nota;
        this.cargando = false;
        this.cargarCadena(id);
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'No se pudo cargar la nota de débito.';
        this.cargando = false;
      },
    });
  }

  // La traza hacia atrás se pide aparte: si falla no se rompe el detalle (el
  // nodo de la nota de crédito se sigue mostrando con el snapshot de la nota).
  private cargarCadena(id: number): void {
    this.notaDebitoService.cadenaDocumentos(id).subscribe({
      next: (cadena) => {
        this.cadena = cadena;
      },
      error: () => {
        this.cadena = [];
      },
    });
  }

  // Las acciones de transición devuelven la nota completa: basta reasignarla
  // para que la pantalla se reconfigure sola.
  private ejecutar(peticion: Observable<NotaDebito>): void {
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