import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { CashMovement, CashMovementType } from '../../../core/models';
import { MOCK_USER, nombreUsuario } from '../../../core/seed/mock-data';
import { BusinessRuleError } from '../../../core/services/business-rule-error';
import { CajaFacade } from '../../../core/services/caja-facade.service';
import { normalizeAmount } from '../../../core/utils/money';
import { ClpPipe } from '../../pipes/clp.pipe';

/**
 * Formulario de registro de movimiento, compartido por Entradas y Salidas (spec §5, §6).
 * Solo cambia el tipo y la lista de conceptos: la lógica es la misma.
 */
@Component({
  selector: 'app-movimiento-form',
  standalone: true,
  imports: [ClpPipe, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './movimiento-form.html',
  styleUrl: './movimiento-form.css',
})
export class MovimientoFormComponent {
  private readonly facade = inject(CajaFacade);

  readonly tipo = input.required<CashMovementType>();
  readonly conceptos = input.required<readonly string[]>();
  readonly registrado = output<CashMovement>();

  readonly concepto = signal('');
  readonly monto = signal('');
  readonly observacion = signal('');
  readonly fecha = signal(toDateTimeLocal(new Date()));
  readonly error = signal<string | null>(null);
  readonly exito = signal<string | null>(null);

  readonly esSalida = computed(() => this.tipo() === CashMovementType.SALIDA);
  readonly saldoDisponible = this.facade.saldoEsperado;
  readonly responsable = nombreUsuario(MOCK_USER.id);

  /** Monto normalizado a entero, para previsualizar el saldo resultante. */
  readonly montoNumero = computed(() => normalizeAmount(this.monto()));

  readonly saldoResultante = computed(() =>
    this.esSalida()
      ? this.saldoDisponible() - this.montoNumero()
      : this.saldoDisponible() + this.montoNumero(),
  );

  /** La salida no puede dejar el saldo esperado en negativo (spec §6). */
  readonly excedeSaldo = computed(
    () => this.esSalida() && this.montoNumero() > this.saldoDisponible(),
  );

  readonly puedeRegistrar = computed(
    () => this.concepto().trim().length > 0 && this.montoNumero() > 0 && !this.excedeSaldo(),
  );

  registrar(): void {
    this.error.set(null);
    this.exito.set(null);
    try {
      const data = {
        concepto: this.concepto(),
        monto: this.monto(),
        observacion: this.observacion(),
        fecha: new Date(this.fecha()),
      };
      const movimiento = this.esSalida()
        ? this.facade.registrarSalida(data)
        : this.facade.registrarEntrada(data);

      this.exito.set(`Movimiento registrado por ${formatShort(movimiento.monto)}.`);
      this.registrado.emit(movimiento);
      this.limpiar();
    } catch (e) {
      this.error.set(
        e instanceof BusinessRuleError ? e.message : 'No se pudo registrar el movimiento.',
      );
    }
  }

  limpiar(): void {
    this.concepto.set('');
    this.monto.set('');
    this.observacion.set('');
    this.fecha.set(toDateTimeLocal(new Date()));
  }
}

/** Formatea para el input datetime-local, que exige hora local sin zona. */
function toDateTimeLocal(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function formatShort(monto: number): string {
  return `$${monto.toLocaleString('es-CL')}`;
}
