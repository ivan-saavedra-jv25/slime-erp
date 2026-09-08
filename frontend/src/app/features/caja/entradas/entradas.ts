import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { CONCEPTOS_ENTRADA, CashMovementType } from '../core/models';
import { CajaFacade } from '../core/services/caja-facade.service';
import { MovimientoFormComponent } from '../shared/components/movimiento-form/movimiento-form';
import { MovimientosTablaComponent } from '../shared/components/movimientos-tabla/movimientos-tabla';
import { ClpPipe } from '../shared/pipes/clp.pipe';

/** Entradas de dinero: aumentan el saldo esperado (spec §5). */
@Component({
  selector: 'app-entradas',
  standalone: true,
  imports: [ClpPipe, MatCardModule, MovimientoFormComponent, MovimientosTablaComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './entradas.html',
  styleUrl: './entradas.css',
})
export class EntradasComponent {
  private readonly facade = inject(CajaFacade);

  readonly conceptos = CONCEPTOS_ENTRADA;
  readonly tipo = CashMovementType.ENTRADA;
  readonly saldoEsperado = this.facade.saldoEsperado;
  readonly totalEntradas = this.facade.totalEntradas;

  readonly entradas = computed(() =>
    this.facade.movimientosSesion().filter((m) => m.tipo === CashMovementType.ENTRADA),
  );
}
