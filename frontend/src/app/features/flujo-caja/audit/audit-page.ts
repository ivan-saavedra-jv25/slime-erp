import { Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { buildAudit, CheckStatus } from '../core/audit';
import { CashflowStore } from '../core/cashflow.store';
import { ClpPipe } from '../shared/clp.pipe';
import { MonthLabelPipe } from '../shared/month-label.pipe';

const STATUS_META: Record<CheckStatus, { icon: string; label: string }> = {
  pass: { icon: 'check_circle', label: 'Cumple' },
  warn: { icon: 'warning', label: 'Atención' },
  fail: { icon: 'error', label: 'No cumple' },
  info: { icon: 'info', label: 'Informativo' },
};

@Component({
  selector: 'app-audit-page',
  standalone: true,
  imports: [
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatTooltipModule,
    ClpPipe,
    MonthLabelPipe,
  ],
  templateUrl: './audit-page.html',
  styleUrl: './audit-page.css',
})
export class AuditPage {
  private readonly store = inject(CashflowStore);

  readonly year = this.store.year;
  readonly report = computed(() =>
    buildAudit(this.store.state(), this.store.projection(), this.store.year()),
  );

  readonly isEmpty = computed(() => {
    const state = this.store.state();
    return state.recurring.length === 0 && state.oneOff.length === 0;
  });

  meta(status: CheckStatus) {
    return STATUS_META[status];
  }

  stepYear(delta: number): void {
    this.store.stepYear(delta);
  }

  readonly canGoToPreviousYear = this.store.canGoToPreviousYear;
}
