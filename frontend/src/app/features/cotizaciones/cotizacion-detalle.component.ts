import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { Observable } from 'rxjs';
import { AccionCotizacion, Cotizacion, EstadoCotizacion } from '../../core/models/models';
import { CotizacionService } from '../../core/services/cotizacion.service';
import { NotaVentaService } from '../../core/services/nota-venta.service';
import { AuthService } from '../../core/services/auth.service';
import { MonedaPipe } from '../../core/pipes/moneda.pipe';
import { VentaPdfDialogComponent } from '../ventas/venta-pdf-dialog.component';
import { ETIQUETAS_ESTADO, TAGS_ESTADO } from './estado-cotizacion';

const ETIQUETAS_ACCION: Record<AccionCotizacion, string> = {
  CREADA: 'Cotización creada',
  EDITADA: 'Cotización editada',
  ENVIADA: 'Cotización enviada',
  ACEPTADA: 'Cotización aceptada',
  RECHAZADA: 'Cotización rechazada',
  CANCELADA: 'Cotización cancelada',
  VENCIDA: 'Cotización vencida',
  DUPLICADA: 'Cotización duplicada',
};

@Component({
  selector: 'app-cotizacion-detalle',
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
  templateUrl: './cotizacion-detalle.component.html',
  styleUrl: './cotizacion-detalle.component.scss',
})
export class CotizacionDetalleComponent implements OnInit {
  cotizacion: Cotizacion | null = null;
  cargando = true;
  procesando = false;
  error = '';

  // Formulario embebido para rechazar/cancelar: null cuando está cerrado.
  accionConMotivo: 'rechazar' | 'cancelar' | null = null;
  motivo = '';

  constructor(
    private cotizacionService: CotizacionService,
    private notaVentaService: NotaVentaService,
    private route: ActivatedRoute,
    private router: Router,
    private dialog: MatDialog,
    public auth: AuthService
  ) {}

  ngOnInit(): void {
    // Se escucha el parámetro en vez de leer el snapshot una sola vez: al
    // duplicar se navega de /cotizaciones/1 a /cotizaciones/2 y Angular reutiliza
    // esta misma instancia del componente, así que ngOnInit no vuelve a correr.
    this.route.paramMap.subscribe((params) => this.cargar(Number(params.get('id'))));
  }

  private cargar(id: number): void {
    this.cargando = true;
    this.error = '';
    this.accionConMotivo = null;
    this.cotizacionService.obtener(id).subscribe({
      next: (cotizacion) => {
        this.cotizacion = cotizacion;
        this.cargando = false;
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'No se pudo cargar la cotización.';
        this.cargando = false;
      },
    });
  }

  get puedeEditar(): boolean {
    return this.auth.tienePermiso('COTIZACIONES_EDITAR');
  }

  esEstado(...estados: EstadoCotizacion[]): boolean {
    return !!this.cotizacion && estados.includes(this.cotizacion.estado);
  }

  etiquetaEstado(estado: EstadoCotizacion): string {
    return ETIQUETAS_ESTADO[estado];
  }

  tagEstado(estado: EstadoCotizacion): string {
    return TAGS_ESTADO[estado];
  }

  etiquetaAccion(accion: AccionCotizacion): string {
    return ETIQUETAS_ACCION[accion];
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

  enviar(): void {
    this.ejecutar(this.cotizacionService.enviar(this.cotizacion!.id));
  }

  aceptar(): void {
    this.ejecutar(this.cotizacionService.aceptar(this.cotizacion!.id));
  }

  abrirMotivo(accion: 'rechazar' | 'cancelar'): void {
    this.accionConMotivo = accion;
    this.motivo = '';
  }

  cerrarMotivo(): void {
    this.accionConMotivo = null;
    this.motivo = '';
  }

  confirmarMotivo(): void {
    const motivo = this.motivo.trim() || null;
    const peticion =
      this.accionConMotivo === 'rechazar'
        ? this.cotizacionService.rechazar(this.cotizacion!.id, motivo)
        : this.cotizacionService.cancelar(this.cotizacion!.id, motivo);
    this.ejecutar(peticion, () => this.cerrarMotivo());
  }

  duplicar(): void {
    if (this.procesando) return;
    this.procesando = true;
    this.cotizacionService.duplicar(this.cotizacion!.id).subscribe({
      next: (copia) => {
        this.procesando = false;
        this.router.navigate(['/cotizaciones', copia.id]);
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'No se pudo duplicar la cotización.';
        this.procesando = false;
      },
    });
  }

  crearNotaVenta(): void {
    if (this.procesando) return;
    this.procesando = true;
    this.error = '';
    this.notaVentaService.crearDesdeCotizacion(this.cotizacion!.id).subscribe({
      next: (nota) => {
        this.procesando = false;
        this.router.navigate(['/notas-venta', nota.id]);
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'No se pudo crear la nota de venta.';
        this.procesando = false;
      },
    });
  }

  eliminar(): void {
    if (this.procesando) return;
    this.procesando = true;
    this.cotizacionService.eliminar(this.cotizacion!.id).subscribe({
      next: () => {
        this.procesando = false;
        this.router.navigate(['/cotizaciones']);
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'No se pudo eliminar la cotización.';
        this.procesando = false;
      },
    });
  }

  verPdf(): void {
    if (this.procesando || !this.cotizacion) return;
    this.procesando = true;
    const numero = this.cotizacion.numero;
    this.cotizacionService.obtenerPdf(this.cotizacion.id).subscribe({
      next: (blob) => {
        this.procesando = false;
        const url = URL.createObjectURL(blob);
        const dialogRef = this.dialog.open(VentaPdfDialogComponent, {
          data: { titulo: `Cotización ${numero}`, url },
          width: '90vw',
          maxWidth: '1200px',
        });
        dialogRef.afterClosed().subscribe(() => URL.revokeObjectURL(url));
      },
      error: () => {
        this.error = 'No se pudo cargar el PDF de la cotización.';
        this.procesando = false;
      },
    });
  }

  private ejecutar(peticion: Observable<Cotizacion>, alTerminar?: () => void): void {
    if (this.procesando) return;
    this.procesando = true;
    this.error = '';
    peticion.subscribe({
      next: (cotizacion) => {
        this.cotizacion = cotizacion;
        this.procesando = false;
        alTerminar?.();
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'No se pudo actualizar la cotización.';
        this.procesando = false;
      },
    });
  }
}
