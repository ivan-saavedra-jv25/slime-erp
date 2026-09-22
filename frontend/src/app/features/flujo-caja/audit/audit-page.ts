import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuditReport, AuditStatus, buildAuditReport } from '../core/audit';
import { FlujoCajaService } from '../core/flujo-caja.service';
import { currentYear } from '../core/month';
import { ClpPipe } from '../shared/clp.pipe';

const STATUS_META: Record<AuditStatus, { icon: string; label: string }> = {
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
    MatProgressBarModule,
    MatTooltipModule,
    ClpPipe,
  ],
  templateUrl: './audit-page.html',
  styleUrl: './audit-page.css',
})
export class AuditPage {
  private readonly service = inject(FlujoCajaService);

  readonly year = signal(currentYear());
  readonly report = signal<AuditReport | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  constructor() {
    this.load(this.year());
  }

  readonly isEmpty = signal(true);

  readonly counts = {
    pass: () => this.report()?.checks.filter((c) => c.status === 'pass').length ?? 0,
    warn: () => this.report()?.checks.filter((c) => c.status === 'warn').length ?? 0,
    fail: () => this.report()?.checks.filter((c) => c.status === 'fail').length ?? 0,
  };

  meta(status: AuditStatus) {
    return STATUS_META[status];
  }

  stepYear(delta: number): void {
    this.load(this.year() + delta);
  }

  private load(anio: number): void {
    this.year.set(anio);
    this.loading.set(true);
    this.error.set(null);
    this.service
      .resumenAnio(anio)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (resumen) => {
          this.report.set(buildAuditReport(resumen.anio, resumen.meses));
          this.isEmpty.set(!resumen.meses.some((m) => m.ingresos > 0 || m.compras > 0 || m.gastos > 0));
        },
        error: () =>
          this.error.set('No se pudo cargar la auditoría del año. Intenta de nuevo más tarde.'),
      });
  }
}