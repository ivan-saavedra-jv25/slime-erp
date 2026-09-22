import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { Observable } from 'rxjs';
import { AccionNotaVenta, EstadoNotaVenta, NotaVenta } from '../../core/models/models';
import { EntregaRequest, NotaVentaService } from '../../core/services/nota-venta.service';
import { AuthService } from '../../core/services/auth.service';
import { MonedaPipe } from '../../core/pipes/moneda.pipe';
import { VentaPdfDialogComponent } from '../ventas/venta-pdf-dialog.component';
import { ETIQUETAS_ESTADO, ETIQUETAS_ORIGEN, TAGS_ESTADO } from './estado-nota-venta';

const ETIQUETAS_ACCION: Record<AccionNotaVenta, string> = {
  CREADA: 'Nota de venta creada',
  EDITADA: 'Nota de venta editada',
  CONFIRMADA: 'Nota de venta confirmada',
  PREPARADA: 'Enviada a preparación',
  ENTREGA_REGISTRADA: 'Entrega parcial registrada',
  ENTREGADA: 'Entrega completada',
  FACTURADA: 'Facturada',
  CANCELADA: 'Nota de venta cancelada',
  DUPLICADA: 'Nota de venta duplicada',
};

interface LineaEntregaForm {
  productoId: number;
  descripcion: string;
  solicitada: number;
  yaEntregada: number;
  cantidad: number;
}

@Component({
  selector: 'app-nota-venta-detalle',
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
  templateUrl: './nota-venta-detalle.component.html',
  styleUrl: './nota-venta-detalle.component.scss',
})
export class NotaVentaDetalleComponent implements OnInit {
  nota: NotaVenta | null = null;
  cargando = true;
  procesando = false;
  error = '';

  // Formulario embebido para cancelar: null cuando está cerrado.
  accionConMotivo = false;
  motivo = '';

  // Formulario embebido para registrar un avance de entrega.
  entregaAbierta = false;
  lineasEntrega: LineaEntregaForm[] = [];
  entregaObservacion = '';
  entregaError: string | null = null;

  constructor(
    private notaVentaService: NotaVentaService,
    private route: ActivatedRoute,
    private router: Router,
    private dialog: MatDialog,
    public auth: AuthService
  ) {}

  ngOnInit(): void {
    // Se escucha el parámetro en vez de leer el snapshot una sola vez: al
    // duplicar se navega de /notas-venta/1 a /notas-venta/2 y Angular reutiliza
    // esta misma instancia del componente, así que ngOnInit no vuelve a correr.
    this.route.paramMap.subscribe((params) => this.cargar(Number(params.get('id'))));
  }

  private cargar(id: number): void {
    this.cargando = true;
    this.error = '';
    this.cerrarMotivo();
    this.cerrarEntrega();
    this.notaVentaService.obtener(id).subscribe({
      next: (nota) => {
        this.nota = nota;
        this.cargando = false;
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'No se pudo cargar la nota de venta.';
        this.cargando = false;
      },
    });
  }

  get puedeEditar(): boolean {
    return this.auth.tienePermiso('NOTAS_VENTA_EDITAR');
  }

  esEstado(...estados: EstadoNotaVenta[]): boolean {
    return !!this.nota && estados.includes(this.nota.estado);
  }

  etiquetaEstado(estado: EstadoNotaVenta): string {
    return ETIQUETAS_ESTADO[estado];
  }

  tagEstado(estado: EstadoNotaVenta): string {
    return TAGS_ESTADO[estado];
  }

  etiquetaAccion(accion: AccionNotaVenta): string {
    return ETIQUETAS_ACCION[accion];
  }

  etiquetaOrigen(): string {
    return this.nota ? ETIQUETAS_ORIGEN[this.nota.origen] : '';
  }

  // El backend guarda el tipo como texto libre para no tener que tocar la
  // entidad cada vez que aparezca un módulo nuevo aguas abajo; acá solo se
  // formatea lo que llegue.
  etiquetaTipoDocumento(tipo: string): string {
    const etiquetas: Record<string, string> = {
      NOTA_VENTA: 'Nota de Venta',
      GUIA_DESPACHO: 'Guía de Despacho',
      FACTURA: 'Factura',
      PAGO: 'Pago',
    };
    return etiquetas[tipo] ?? tipo;
  }

  get pendientesDeEntrega(): boolean {
    if (!this.nota) return false;
    return this.nota.lineas.some((l) => l.cantidadEntregada < l.cantidad);
  }

  confirmar(): void {
    this.ejecutar(this.notaVentaService.confirmar(this.nota!.id));
  }

  preparar(): void {
    this.ejecutar(this.notaVentaService.preparar(this.nota!.id));
  }

  abrirMotivo(): void {
    this.accionConMotivo = true;
    this.motivo = '';
  }

  cerrarMotivo(): void {
    this.accionConMotivo = false;
    this.motivo = '';
  }

  confirmarMotivo(): void {
    const motivo = this.motivo.trim() || null;
    this.ejecutar(this.notaVentaService.cancelar(this.nota!.id, motivo), () => this.cerrarMotivo());
  }

  abrirEntrega(): void {
    if (!this.nota) return;
    this.cerrarMotivo();
    this.entregaAbierta = true;
    this.entregaError = null;
    this.entregaObservacion = '';
    this.lineasEntrega = this.nota.lineas
      .filter((l) => l.cantidadEntregada < l.cantidad)
      .map((l) => ({
        productoId: l.productoId,
        descripcion: l.descripcion,
        solicitada: l.cantidad,
        yaEntregada: l.cantidadEntregada,
        cantidad: 0,
      }));
  }

  cerrarEntrega(): void {
    this.entregaAbierta = false;
    this.lineasEntrega = [];
    this.entregaObservacion = '';
    this.entregaError = null;
  }

  restanteEntrega(linea: LineaEntregaForm): number {
    return Math.max(linea.solicitada - linea.yaEntregada, 0);
  }

  confirmarEntrega(): void {
    if (this.procesando || !this.lineasEntrega.length) return;

    let error: string | null = null;
    for (const linea of this.lineasEntrega) {
      if (linea.cantidad < 0) {
        error = 'Las cantidades entregadas no pueden ser negativas.';
        break;
      }
      if (linea.cantidad > this.restanteEntrega(linea)) {
        error = `La entrega supera lo solicitado de "${linea.descripcion}".`;
        break;
      }
    }
    if (!this.lineasEntrega.some((l) => l.cantidad > 0)) {
      error = 'Debes indicar al menos una cantidad a entregar.';
    }
    if (error) {
      this.entregaError = error;
      return;
    }
    this.entregaError = null;

    const request: EntregaRequest = {
      observacion: this.entregaObservacion.trim() || undefined,
      lineas: this.lineasEntrega
        .filter((l) => l.cantidad > 0)
        .map((l) => ({ productoId: l.productoId, cantidad: l.cantidad })),
    };
    this.ejecutar(this.notaVentaService.registrarEntrega(this.nota!.id, request), () => this.cerrarEntrega());
  }

  duplicar(): void {
    if (this.procesando) return;
    this.procesando = true;
    this.notaVentaService.duplicar(this.nota!.id).subscribe({
      next: (copia) => {
        this.procesando = false;
        this.router.navigate(['/notas-venta', copia.id]);
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'No se pudo duplicar la nota de venta.';
        this.procesando = false;
      },
    });
  }

  eliminar(): void {
    if (this.procesando) return;
    this.procesando = true;
    this.notaVentaService.eliminar(this.nota!.id).subscribe({
      next: () => {
        this.procesando = false;
        this.router.navigate(['/notas-venta']);
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'No se pudo eliminar la nota de venta.';
        this.procesando = false;
      },
    });
  }

  verPdf(): void {
    if (this.procesando || !this.nota) return;
    this.procesando = true;
    const numero = this.nota.numero;
    this.notaVentaService.obtenerPdf(this.nota.id).subscribe({
      next: (blob) => {
        this.procesando = false;
        const url = URL.createObjectURL(blob);
        const dialogRef = this.dialog.open(VentaPdfDialogComponent, {
          data: { titulo: `Nota de Venta ${numero}`, url },
          width: '90vw',
          maxWidth: '1200px',
        });
        dialogRef.afterClosed().subscribe(() => URL.revokeObjectURL(url));
      },
      error: () => {
        this.error = 'No se pudo cargar el PDF de la nota de venta.';
        this.procesando = false;
      },
    });
  }

  private ejecutar(peticion: Observable<NotaVenta>, alTerminar?: () => void): void {
    if (this.procesando) return;
    this.procesando = true;
    this.error = '';
    peticion.subscribe({
      next: (nota) => {
        this.nota = nota;
        this.procesando = false;
        alTerminar?.();
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'No se pudo actualizar la nota de venta.';
        this.procesando = false;
      },
    });
  }
}