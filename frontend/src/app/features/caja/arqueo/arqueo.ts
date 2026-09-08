import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { RouterLink } from '@angular/router';
import { CashCountItem } from '../core/models';
import { BusinessRuleError } from '../core/services/business-rule-error';
import { CajaFacade } from '../core/services/caja-facade.service';
import { countTotal, emptyCountItems } from '../core/utils/money';
import { CashDenominationCounterComponent } from '../shared/components/cash-denomination-counter/cash-denomination-counter';
import { DiferenciaPanelComponent } from '../shared/components/diferencia-panel/diferencia-panel';
import { ClpPipe } from '../shared/pipes/clp.pipe';

/**
 * Arqueo: conteo físico del dinero existente y comparación con el saldo esperado (spec §8).
 * Reutiliza el mismo componente de conteo que la apertura.
 */
@Component({
  selector: 'app-arqueo',
  standalone: true,
  imports: [
    RouterLink,
    DatePipe,
    ClpPipe,
    MatButtonModule,
    MatCardModule,
    CashDenominationCounterComponent,
    DiferenciaPanelComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './arqueo.html',
  styleUrl: './arqueo.css',
})
export class ArqueoComponent {
  private readonly facade = inject(CajaFacade);

  readonly saldoEsperado = this.facade.saldoEsperado;
  readonly sesion = this.facade.sesionActiva;
  readonly conteoGuardado = this.facade.conteoArqueo;

  /** Conteo en edición; se inicializa con el arqueo previo si ya se hizo uno. */
  readonly conteo = signal<CashCountItem[]>(this.facade.conteoArqueo()?.items ?? emptyCountItems());
  readonly error = signal<string | null>(null);
  readonly guardado = signal(false);

  readonly contado = computed(() => countTotal(this.conteo()));

  registrar(): void {
    this.error.set(null);
    try {
      this.facade.registrarArqueo(this.conteo());
      this.guardado.set(true);
    } catch (e) {
      this.error.set(e instanceof BusinessRuleError ? e.message : 'No se pudo registrar el arqueo.');
    }
  }
}
