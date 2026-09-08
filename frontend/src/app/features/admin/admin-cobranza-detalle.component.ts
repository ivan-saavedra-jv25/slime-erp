import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { CobranzaService } from '../../core/services/cobranza.service';
import { EmpresaService } from '../../core/services/empresa.service';
import { CobranzaEmpresa, CobranzaPago, EstadoCobranza, MedioPago, PagoCobranzaRequest } from '../../core/models/models';
import { MonedaPipe } from '../../core/pipes/moneda.pipe';

const ETIQUETAS: Record<EstadoCobranza, string> = {
  DEUDA: 'En deuda',
  PARCIAL: 'Parcial',
  PAGADO: 'Pagado',
  ANULADO: 'Anulado',
};

function pagoVacio(): PagoCobranzaRequest {
  return { monto: 0, medioPago: 'EFECTIVO' };
}

@Component({
  selector: 'app-admin-cobranza-detalle',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, MatButtonModule, MatIconModule, MatCardModule, MonedaPipe],
  templateUrl: './admin-cobranza-detalle.component.html',
  styleUrl: './admin-cobranza-detalle.component.scss',
})
export class AdminCobranzaDetalleComponent implements OnInit {
  cargo: CobranzaEmpresa | null = null;
  nombreEmpresa = '';
  pagos: CobranzaPago[] = [];
  cargando = true;
  error = '';

  formularioPagoAbierto = false;
  pagoForm: PagoCobranzaRequest = pagoVacio();
  guardandoPago = false;
  errorPago = '';

  anulandoCargo = false;
  motivoAnulacionCargo = '';
  errorAnulacionCargo = '';

  pagoAnulandoId: number | null = null;
  motivoAnulacionPago = '';
  errorAnulacionPago = '';

  readonly etiquetaEstado = ETIQUETAS;
  readonly mediosPago: { value: MedioPago; label: string }[] = [
    { value: 'EFECTIVO', label: 'Efectivo' },
    { value: 'TRANSFERENCIA', label: 'Transferencia' },
    { value: 'TARJETA', label: 'Tarjeta' },
    { value: 'CHEQUE', label: 'Cheque' },
  ];

  constructor(
    private route: ActivatedRoute,
    private cobranzaService: CobranzaService,
    private empresaService: EmpresaService
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.cargar(id);
  }

  private cargar(id: number): void {
    this.cargando = true;
    this.error = '';
    this.cobranzaService.obtener(id).subscribe({
      next: (cargo) => {
        this.cargo = cargo;
        this.empresaService.listar().subscribe((empresas) => {
          this.nombreEmpresa = empresas.find((e) => e.id === cargo.tenantId)?.nombre ?? `Empresa #${cargo.tenantId}`;
        });
        this.cargarPagos(id);
      },
      error: (err) => {
        this.cargando = false;
        this.error = err?.error?.error ?? 'No se pudo cargar el cargo.';
      },
    });
  }

  private cargarPagos(id: number): void {
    this.cobranzaService.listarPagos(id).subscribe({
      next: (pagos) => {
        this.pagos = pagos;
        this.cargando = false;
      },
      error: () => (this.cargando = false),
    });
  }

  get puedeRegistrarPago(): boolean {
    return !!this.cargo && (this.cargo.estado === 'DEUDA' || this.cargo.estado === 'PARCIAL');
  }

  toggleFormularioPago(): void {
    this.formularioPagoAbierto = !this.formularioPagoAbierto;
    this.pagoForm = pagoVacio();
    this.errorPago = '';
  }

  seleccionarMedio(medio: MedioPago): void {
    this.pagoForm.medioPago = medio;
  }

  get montoInvalido(): boolean {
    return !this.cargo || this.pagoForm.monto <= 0 || this.pagoForm.monto > this.cargo.saldoPendiente;
  }

  confirmarPago(): void {
    if (!this.cargo || this.montoInvalido) return;
    this.guardandoPago = true;
    this.errorPago = '';

    this.cobranzaService.registrarPago(this.cargo.id, this.pagoForm).subscribe({
      next: () => {
        this.guardandoPago = false;
        this.formularioPagoAbierto = false;
        this.pagoForm = pagoVacio();
        this.cargar(this.cargo!.id);
      },
      error: (err) => {
        this.errorPago = err?.error?.error ?? 'OcurriÃ³ un error al registrar el pago.';
        this.guardandoPago = false;
      },
    });
  }

  toggleAnularCargo(): void {
    this.anulandoCargo = !this.anulandoCargo;
    this.motivoAnulacionCargo = '';
    this.errorAnulacionCargo = '';
  }

  confirmarAnulacionCargo(): void {
    if (!this.cargo || !this.motivoAnulacionCargo.trim()) return;
    this.cobranzaService.anular(this.cargo.id, this.motivoAnulacionCargo).subscribe({
      next: () => {
        this.anulandoCargo = false;
        this.cargar(this.cargo!.id);
      },
      error: (err) => {
        this.errorAnulacionCargo = err?.error?.error ?? 'OcurriÃ³ un error al anular el cargo.';
      },
    });
  }

  toggleAnularPago(pagoId: number): void {
    this.pagoAnulandoId = this.pagoAnulandoId === pagoId ? null : pagoId;
    this.motivoAnulacionPago = '';
    this.errorAnulacionPago = '';
  }

  confirmarAnulacionPago(pagoId: number): void {
    if (!this.motivoAnulacionPago.trim()) return;
    this.cobranzaService.anularPago(pagoId, this.motivoAnulacionPago).subscribe({
      next: () => {
        this.pagoAnulandoId = null;
        this.cargar(this.cargo!.id);
      },
      error: (err) => {
        this.errorAnulacionPago = err?.error?.error ?? 'OcurriÃ³ un error al anular el pago.';
      },
    });
  }
}