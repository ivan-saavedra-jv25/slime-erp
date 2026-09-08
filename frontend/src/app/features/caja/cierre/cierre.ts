import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { Router } from '@angular/router';
import { CashCountItem, CashDifferenceStatus } from '../core/models';
import { nombreUsuario } from '../core/seed/mock-data';
import { BusinessRuleError } from '../core/services/business-rule-error';
import { CajaFacade } from '../core/services/caja-facade.service';
import { countTotal, differenceStatus, emptyCountItems } from '../core/utils/money';
import { CashDenominationCounterComponent } from '../shared/components/cash-denomination-counter/cash-denomination-counter';
import { DiferenciaPanelComponent } from '../shared/components/diferencia-panel/diferencia-panel';
import { MovimientosTablaComponent } from '../shared/components/movimientos-tabla/movimientos-tabla';
import { ClpPipe } from '../shared/pipes/clp.pipe';

/**
 * Cierre de Caja (spec §9):
 * revisión de movimientos → conteo final → comparación → confirmación → caja cerrada.
 */
@Component({
  selector: 'app-cierre',
  standalone: true,
  imports: [
    DatePipe,
    ClpPipe,
    MatButtonModule,
    MatCardModule,
    CashDenominationCounterComponent,
    DiferenciaPanelComponent,
    MovimientosTablaComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './cierre.html',
  styleUrl: './cierre.css',
})
export class CierreComponent {
  private readonly facade = inject(CajaFacade);
  private readonly router = inject(Router);

  readonly sesion = this.facade.sesionActiva;
  readonly caja = this.facade.cajaActual;
  readonly movimientos = this.facade.movimientosSesion;
  readonly saldoEsperado = this.facade.saldoEsperado;
  readonly totalEntradas = this.facade.totalEntradas;
  readonly totalSalidas = this.facade.totalSalidas;

  /** Arranca con el conteo del arqueo si ya se hizo: no se cuenta dos veces (spec §9). */
  readonly conteo = signal<CashCountItem[]>(this.facade.conteoArqueo()?.items ?? emptyCountItems());
  readonly vieneDeArqueo = this.facade.conteoArqueo() !== null;

  readonly observacionCierre = signal('');
  readonly error = signal<string | null>(null);
  readonly confirmando = signal(false);

  readonly Estado = CashDifferenceStatus;
  readonly contado = computed(() => countTotal(this.conteo()));
  readonly diferencia = computed(() => this.contado() - this.saldoEsperado());
  readonly estadoDiferencia = computed(() => differenceStatus(this.diferencia()));
  readonly hayDiferencia = computed(() => this.diferencia() !== 0);
  readonly magnitud = computed(() => Math.abs(this.diferencia()));

  /** Solo se exige haber contado; la diferencia no bloquea el cierre (spec §10). */
  readonly puedeCerrar = computed(() => this.conteo().some((item) => item.quantity > 0) || this.contado() === 0);

  readonly responsable = computed(() => {
    const s = this.sesion();
    return s ? nombreUsuario(s.responsableId) : '—';
  });

  pedirConfirmacion(): void {
    this.error.set(null);
    this.confirmando.set(true);
  }

  cancelarConfirmacion(): void {
    this.confirmando.set(false);
  }

  cerrar(): void {
    this.error.set(null);
    try {
      this.facade.cerrarCaja({
        items: this.conteo(),
        observacionCierre: this.observacionCierre(),
      });
      this.router.navigate(['/caja/historial']);
    } catch (e) {
      this.confirmando.set(false);
      this.error.set(e instanceof BusinessRuleError ? e.message : 'No se pudo cerrar la caja.');
    }
  }
}
