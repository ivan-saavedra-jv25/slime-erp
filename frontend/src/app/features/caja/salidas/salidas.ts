import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { CONCEPTOS_SALIDA, CashMovementType } from '../core/models';
import { CajaFacade } from '../core/services/caja-facade.service';
import { MovimientoFormComponent } from '../shared/components/movimiento-form/movimiento-form';
import { MovimientosTablaComponent } from '../shared/components/movimientos-tabla/movimientos-tabla';
import { ClpPipe } from '../shared/pipes/clp.pipe';

/** Salidas de dinero: disminuyen el saldo esperado y nunca lo dejan negativo (spec §6). */
@Component({
  selector: 'app-salidas',
  standalone: true,
  imports: [ClpPipe, MatCardModule, MovimientoFormComponent, MovimientosTablaComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './salidas.html',
  styleUrl: './salidas.css',
})
export class SalidasComponent {
  private readonly facade = inject(CajaFacade);

  readonly conceptos = CONCEPTOS_SALIDA;
  readonly tipo = CashMovementType.SALIDA;
  readonly saldoEsperado = this.facade.saldoEsperado;
  readonly totalSalidas = this.facade.totalSalidas;

  readonly salidas = computed(() =>
    this.facade.movimientosSesion().filter((m) => m.tipo === CashMovementType.SALIDA),
  );
}
