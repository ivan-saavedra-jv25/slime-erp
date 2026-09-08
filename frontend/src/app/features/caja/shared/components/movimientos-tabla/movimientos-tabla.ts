import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { CashMovement, CashMovementType } from '../../../core/models';
import { nombreUsuario } from '../../../core/seed/mock-data';
import { totalEntradas, totalSalidas } from '../../../core/utils/money';
import { ClpPipe } from '../../pipes/clp.pipe';

/** Tabla de movimientos reutilizada en Movimientos, Entradas, Salidas y detalle histórico (spec §7). */
@Component({
  selector: 'app-movimientos-tabla',
  standalone: true,
  imports: [DatePipe, ClpPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './movimientos-tabla.html',
  styleUrl: './movimientos-tabla.css',
})
export class MovimientosTablaComponent {
  readonly movimientos = input.required<readonly CashMovement[]>();
  readonly mostrarTotales = input(false);
  readonly saldoInicial = input<number | null>(null);
  readonly emptyMessage = input('No hay movimientos registrados.');

  readonly Tipo = CashMovementType;
  readonly totalEntradas = computed(() => totalEntradas(this.movimientos()));
  readonly totalSalidas = computed(() => totalSalidas(this.movimientos()));

  readonly saldoEsperado = computed(() => {
    const inicial = this.saldoInicial();
    if (inicial === null) return null;
    return inicial + this.totalEntradas() - this.totalSalidas();
  });

  nombreUsuario = nombreUsuario;
}
