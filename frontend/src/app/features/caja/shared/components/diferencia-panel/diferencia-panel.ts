import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { CashDifferenceStatus } from '../../../core/models';
import { difference, differenceStatus } from '../../../core/utils/money';
import { ClpPipe } from '../../pipes/clp.pipe';

/**
 * Comparación entre saldo esperado y efectivo contado (spec §8, §10).
 * Muestra caja cuadrada, sobrante o faltante. No redondea ni oculta la diferencia.
 */
@Component({
  selector: 'app-diferencia-panel',
  standalone: true,
  imports: [ClpPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './diferencia-panel.html',
  styleUrl: './diferencia-panel.css',
})
export class DiferenciaPanelComponent {
  readonly saldoEsperado = input.required<number>();
  readonly saldoContado = input.required<number>();

  readonly Estado = CashDifferenceStatus;
  readonly diferencia = computed(() => difference(this.saldoEsperado(), this.saldoContado()));
  readonly estado = computed(() => differenceStatus(this.diferencia()));

  /** Magnitud absoluta, para leer "Faltante: $1.000" sin el signo. */
  readonly magnitud = computed(() => Math.abs(this.diferencia()));

  readonly titulo = computed(() => {
    switch (this.estado()) {
      case CashDifferenceStatus.CUADRADA:
        return 'Caja cuadrada';
      case CashDifferenceStatus.SOBRANTE:
        return 'Sobrante';
      default:
        return 'Faltante';
    }
  });
}
