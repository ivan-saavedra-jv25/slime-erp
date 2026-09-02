import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { Cliente, CuentaPorCobrar, EstadoCuentaPorCobrar, MedioPago, TransaccionPago } from '../../core/models/models';
import { CuentaPorCobrarService } from '../../core/services/cuenta-por-cobrar.service';
import { TransaccionPagoRequest, TransaccionPagoService } from '../../core/services/transaccion-pago.service';
import { ClienteService } from '../../core/services/cliente.service';
import { AuthService } from '../../core/services/auth.service';

const ETIQUETAS: Record<EstadoCuentaPorCobrar, string> = {
  DEUDA: 'En deuda',
  PARCIAL: 'Parcial',
  PAGADO: 'Pagado',
  ANULADO: 'Anulado',
};

function pagoVacio(): TransaccionPagoRequest {
  return { monto: 0, medioPago: 'EFECTIVO' };
}

@Component({
  selector: 'app-tesoreria-cuenta-detalle',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, MatButtonModule, MatIconModule, MatCardModule],
  templateUrl: './tesoreria-cuenta-detalle.component.html',
  styleUrl: './tesoreria-cuenta-detalle.component.scss',
})
export class TesoreriaCuentaDetalleComponent implements OnInit {
  cuenta: CuentaPorCobrar | null = null;
  cliente: Cliente | null = null;
  pagos: TransaccionPago[] = [];
  cargando = true;

  formularioPagoAbierto = false;
  pagoForm: TransaccionPagoRequest = pagoVacio();
  guardandoPago = false;
  errorPago = '';

  anulandoCuenta = false;
  motivoAnulacionCuenta = '';
  errorAnulacionCuenta = '';

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
    private cuentaPorCobrarService: CuentaPorCobrarService,
    private transaccionPagoService: TransaccionPagoService,
    private clienteService: ClienteService,
    public auth: AuthService
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.cargar(id);
  }

  private cargar(id: number): void {
    this.cargando = true;
    this.cuentaPorCobrarService.obtener(id).subscribe({
      next: (cuenta) => {
        this.cuenta = cuenta;
        this.clienteService.listar().subscribe((clientes) => {
          this.cliente = clientes.find((c) => c.id === cuenta.clienteId) ?? null;
        });
        this.cargarPagos(id);
      },
      error: () => (this.cargando = false),
    });
  }

  private cargarPagos(id: number): void {
    this.transaccionPagoService.listarPorCuenta(id).subscribe({
      next: (data) => {
        this.pagos = data;
        this.cargando = false;
      },
      error: () => (this.cargando = false),
    });
  }

  get puedeRegistrarPago(): boolean {
    return !!this.cuenta && (this.cuenta.estado === 'DEUDA' || this.cuenta.estado === 'PARCIAL');
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
    return !this.cuenta || this.pagoForm.monto <= 0 || this.pagoForm.monto > this.cuenta.saldoPendiente;
  }

  confirmarPago(): void {
    if (!this.cuenta || this.montoInvalido) return;
    this.guardandoPago = true;
    this.errorPago = '';

    this.transaccionPagoService.registrarPago(this.cuenta.id, this.pagoForm).subscribe({
      next: () => {
        this.guardandoPago = false;
        this.formularioPagoAbierto = false;
        this.pagoForm = pagoVacio();
        this.cargar(this.cuenta!.id);
      },
      error: (err) => {
        this.errorPago = err?.error?.error ?? 'Ocurrió un error al registrar el pago.';
        this.guardandoPago = false;
      },
    });
  }

  toggleAnularCuenta(): void {
    this.anulandoCuenta = !this.anulandoCuenta;
    this.motivoAnulacionCuenta = '';
    this.errorAnulacionCuenta = '';
  }

  confirmarAnulacionCuenta(): void {
    if (!this.cuenta || !this.motivoAnulacionCuenta.trim()) return;
    this.cuentaPorCobrarService.anular(this.cuenta.id, this.motivoAnulacionCuenta).subscribe({
      next: () => {
        this.anulandoCuenta = false;
        this.cargar(this.cuenta!.id);
      },
      error: (err) => {
        this.errorAnulacionCuenta = err?.error?.error ?? 'Ocurrió un error al anular la cuenta.';
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
    this.transaccionPagoService.anular(pagoId, this.motivoAnulacionPago).subscribe({
      next: () => {
        this.pagoAnulandoId = null;
        this.cargar(this.cuenta!.id);
      },
      error: (err) => {
        this.errorAnulacionPago = err?.error?.error ?? 'Ocurrió un error al anular el pago.';
      },
    });
  }
}
