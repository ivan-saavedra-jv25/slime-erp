import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { CashSession, CashSessionStatus } from '../core/models';
import { nombreUsuario } from '../core/seed/mock-data';
import { CajaFacade } from '../core/services/caja-facade.service';
import { CashAuditService } from '../core/services/cash-audit.service';
import { CashDenominationCounterComponent } from '../shared/components/cash-denomination-counter/cash-denomination-counter';
import { DiferenciaPanelComponent } from '../shared/components/diferencia-panel/diferencia-panel';
import { MovimientosTablaComponent } from '../shared/components/movimientos-tabla/movimientos-tabla';
import { ClpPipe } from '../shared/pipes/clp.pipe';

/** Historial de sesiones y su detalle completo (spec §11). */
@Component({
  selector: 'app-historial',
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
  templateUrl: './historial.html',
  styleUrl: './historial.css',
})
export class HistorialComponent {
  private readonly facade = inject(CajaFacade);
  private readonly auditService = inject(CashAuditService);

  readonly sesiones = this.facade.historial;
  readonly Estado = CashSessionStatus;

  readonly seleccionadaId = signal<string | null>(null);

  readonly seleccionada = computed<CashSession | null>(() => {
    const id = this.seleccionadaId();
    return id ? (this.sesiones().find((s) => s.id === id) ?? null) : null;
  });

  readonly movimientosDetalle = computed(() => {
    const s = this.seleccionada();
    return s ? this.facade.movimientosDe(s.id) : [];
  });

  readonly auditoriaDetalle = computed(() => {
    const s = this.seleccionada();
    return s ? this.auditService.bySession(s.id) : [];
  });

  toggleDetalle(id: string): void {
    this.seleccionadaId.update((actual) => (actual === id ? null : id));
  }

  nombreUsuario = nombreUsuario;
  cajaNombre = (id: string) => this.facade.cajaNombre(id);
}
