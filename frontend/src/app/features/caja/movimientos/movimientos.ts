import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { CONCEPTOS_ENTRADA, CONCEPTOS_SALIDA, CashMovementType } from '../core/models';
import { CajaFacade } from '../core/services/caja-facade.service';
import { CashMovementService } from '../core/services/cash-movement.service';
import { MovimientosTablaComponent } from '../shared/components/movimientos-tabla/movimientos-tabla';
import { ClpPipe } from '../shared/pipes/clp.pipe';

/** Movimientos de Caja: listado filtrable de la sesión actual (spec §7). */
@Component({
  selector: 'app-movimientos',
  standalone: true,
  imports: [MatButtonModule, MatCardModule, MovimientosTablaComponent, ClpPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './movimientos.html',
  styleUrl: './movimientos.css',
})
export class MovimientosComponent {
  private readonly facade = inject(CajaFacade);
  private readonly movementService = inject(CashMovementService);

  readonly sesion = this.facade.sesionActiva;
  readonly saldoEsperado = this.facade.saldoEsperado;

  readonly tipos = Object.values(CashMovementType);
  readonly conceptos = [...new Set([...CONCEPTOS_ENTRADA, ...CONCEPTOS_SALIDA])].sort();

  readonly filtroTipo = signal<CashMovementType | ''>('');
  readonly filtroConcepto = signal('');
  readonly filtroDesde = signal('');
  readonly filtroHasta = signal('');

  readonly filtrados = computed(() =>
    this.movementService.filter(this.facade.movimientosSesion(), {
      tipo: this.filtroTipo() || null,
      concepto: this.filtroConcepto() || null,
      desde: this.filtroDesde() ? new Date(this.filtroDesde() + 'T00:00:00') : null,
      hasta: this.filtroHasta() ? new Date(this.filtroHasta() + 'T00:00:00') : null,
    }),
  );

  readonly hayFiltros = computed(
    () => !!(this.filtroTipo() || this.filtroConcepto() || this.filtroDesde() || this.filtroHasta()),
  );

  limpiarFiltros(): void {
    this.filtroTipo.set('');
    this.filtroConcepto.set('');
    this.filtroDesde.set('');
    this.filtroHasta.set('');
  }
}
