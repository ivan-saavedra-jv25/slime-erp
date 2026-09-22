import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { CategoriaGasto, CuentaPorPagar, EstadoCuentaPorPagar, MedioPago, Proveedor, TransaccionPagoCompra } from '../../core/models/models';
import { CuentaPorPagarService } from '../../core/services/cuenta-por-pagar.service';
import { TransaccionPagoCompraRequest, TransaccionPagoCompraService } from '../../core/services/transaccion-pago-compra.service';
import { ProveedorService } from '../../core/services/proveedor.service';
import { CategoriaGastoService } from '../../core/services/categoria-gasto.service';
import { AuthService } from '../../core/services/auth.service';
import { MonedaPipe } from '../../core/pipes/moneda.pipe';
import { cerrarCargando, mostrarCargando } from '../../core/utils/swal-loading';
import { BANCOS_CHILE } from '../../core/constants/bancos-chile';

const OTRO_BANCO = '__OTRO__';

const ETIQUETAS: Record<EstadoCuentaPorPagar, string> = {
  DEUDA: 'En deuda',
  PARCIAL: 'Parcial',
  PAGADO: 'Pagado',
  ANULADO: 'Anulado',
};

function hoy(): string {
  const d = new Date();
  const mes = String(d.getMonth() + 1).padStart(2, '0');
  const dia = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${mes}-${dia}`;
}

function pagoVacio(): TransaccionPagoCompraRequest {
  return { monto: 0, medioPago: 'EFECTIVO', fecha: hoy() };
}

@Component({
  selector: 'app-cuenta-por-pagar-detalle',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, MatButtonModule, MatIconModule, MatCardModule, MonedaPipe],
  templateUrl: './cuenta-por-pagar-detalle.component.html',
  styleUrl: './cuenta-por-pagar-detalle.component.scss',
})
export class CuentaPorPagarDetalleComponent implements OnInit {
  cuenta: CuentaPorPagar | null = null;
  proveedor: Proveedor | null = null;
  categoria: CategoriaGasto | null = null;
  pagos: TransaccionPagoCompra[] = [];
  cargando = true;

  formularioPagoAbierto = false;
  pagoForm: TransaccionPagoCompraRequest = pagoVacio();
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
  readonly bancosChile = BANCOS_CHILE;
  readonly OTRO_BANCO = OTRO_BANCO;

  bancoOrigenSeleccion = '';
  bancoDestinoSeleccion = '';
  chequeBancoSeleccion = '';

  constructor(
    private route: ActivatedRoute,
    private cuentaPorPagarService: CuentaPorPagarService,
    private transaccionPagoCompraService: TransaccionPagoCompraService,
    private proveedorService: ProveedorService,
    private categoriaGastoService: CategoriaGastoService,
    public auth: AuthService
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.cargar(id);
  }

  private cargar(id: number): void {
    this.cargando = true;
    this.cuentaPorPagarService.obtener(id).subscribe({
      next: (cuenta) => {
        this.cuenta = cuenta;
        if (cuenta.proveedorId !== null) {
          this.proveedorService.listar().subscribe((data) => {
            this.proveedor = data.find((p) => p.id === cuenta.proveedorId) ?? null;
          });
        }
        if (cuenta.categoriaGastoId !== null) {
          this.categoriaGastoService.listar().subscribe((data) => {
            this.categoria = data.find((c) => c.id === cuenta.categoriaGastoId) ?? null;
          });
        }
        this.cargarPagos(id);
      },
      error: () => (this.cargando = false),
    });
  }

  private cargarPagos(id: number): void {
    this.transaccionPagoCompraService.listarPorCuenta(id).subscribe({
      next: (data) => {
        this.pagos = data;
        this.cargando = false;
      },
      error: () => (this.cargando = false),
    });
  }

  get esOrigenCompra(): boolean {
    return !!this.cuenta && this.cuenta.compraId !== null;
  }

  get puedeRegistrarPago(): boolean {
    return !!this.cuenta && (this.cuenta.estado === 'DEUDA' || this.cuenta.estado === 'PARCIAL');
  }

  toggleFormularioPago(): void {
    this.formularioPagoAbierto = !this.formularioPagoAbierto;
    this.pagoForm = pagoVacio();
    this.bancoOrigenSeleccion = '';
    this.bancoDestinoSeleccion = '';
    this.chequeBancoSeleccion = '';
    this.errorPago = '';
  }

  seleccionarMedio(medio: MedioPago): void {
    this.pagoForm.medioPago = medio;
  }

  // Los <select> de banco usan una selección aparte del valor final: al
  // elegir "Otro" se limpia el campo para que el usuario lo escriba a mano.
  seleccionarBanco(
    campo: 'transferenciaBancoOrigen' | 'transferenciaBancoDestino' | 'chequeBanco',
    valor: string
  ): void {
    this.pagoForm[campo] = valor === OTRO_BANCO ? '' : valor;
  }

  get montoInvalido(): boolean {
    return !this.cuenta || this.pagoForm.monto <= 0 || this.pagoForm.monto > this.cuenta.saldoPendiente;
  }

  get fechaPagoInvalida(): boolean {
    return !this.pagoForm.fecha;
  }

  confirmarPago(): void {
    if (!this.cuenta || this.montoInvalido || this.fechaPagoInvalida) return;
    this.guardandoPago = true;
    this.errorPago = '';
    mostrarCargando('Registrando pago');

    const request = {
      ...this.pagoForm,
      fecha: this.pagoForm.fecha ? `${this.pagoForm.fecha}T12:00:00` : undefined,
    };
    this.transaccionPagoCompraService.registrarPago(this.cuenta.id, request).subscribe({
      next: () => {
        cerrarCargando();
        this.guardandoPago = false;
        this.formularioPagoAbierto = false;
        this.pagoForm = pagoVacio();
        this.bancoOrigenSeleccion = '';
        this.bancoDestinoSeleccion = '';
        this.chequeBancoSeleccion = '';
        this.cargar(this.cuenta!.id);
      },
      error: (err) => {
        cerrarCargando();
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
    this.cuentaPorPagarService.anular(this.cuenta.id, this.motivoAnulacionCuenta).subscribe({
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
    this.transaccionPagoCompraService.anular(pagoId, this.motivoAnulacionPago).subscribe({
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
