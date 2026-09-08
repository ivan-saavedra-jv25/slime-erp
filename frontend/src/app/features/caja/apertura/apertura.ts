import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { Router } from '@angular/router';
import { CashCountItem } from '../core/models';
import { MOCK_USER, nombreSucursal, nombreUsuario } from '../core/seed/mock-data';
import { BusinessRuleError } from '../core/services/business-rule-error';
import { CajaFacade } from '../core/services/caja-facade.service';
import { emptyCountItems } from '../core/utils/money';
import { CashDenominationCounterComponent } from '../shared/components/cash-denomination-counter/cash-denomination-counter';
import { ClpPipe } from '../shared/pipes/clp.pipe';

/** Apertura de Caja: conteo inicial de efectivo y creación de la sesión (spec §2). */
@Component({
  selector: 'app-apertura',
  standalone: true,
  imports: [DatePipe, ClpPipe, MatButtonModule, MatCardModule, CashDenominationCounterComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './apertura.html',
  styleUrl: './apertura.css',
})
export class AperturaComponent {
  private readonly facade = inject(CajaFacade);
  private readonly router = inject(Router);

  readonly cajas = this.facade.cajas;
  readonly caja = this.facade.cajaActual;
  readonly ahora = new Date();

  readonly conteo = signal<CashCountItem[]>(emptyCountItems());
  readonly total = signal(0);
  readonly error = signal<string | null>(null);

  readonly responsable = computed(() => {
    const c = this.caja();
    return c ? nombreUsuario(c.responsableId) : nombreUsuario(MOCK_USER.id);
  });

  readonly sucursal = computed(() => {
    const c = this.caja();
    return c ? nombreSucursal(c.sucursalId) : '—';
  });

  /** Se permite abrir con $0 (caja sin fondo fijo), pero debe existir una caja. */
  readonly puedeAbrir = computed(() => this.caja() !== null);

  seleccionarCaja(id: string): void {
    this.facade.seleccionarCaja(id);
    this.error.set(null);
  }

  abrir(): void {
    const caja = this.caja();
    if (!caja) {
      this.error.set('Selecciona una caja antes de abrir.');
      return;
    }
    try {
      this.facade.abrirCaja(caja.id, this.conteo());
      this.router.navigate(['/caja/resumen']);
    } catch (e) {
      this.error.set(e instanceof BusinessRuleError ? e.message : 'No se pudo abrir la caja.');
    }
  }
}
