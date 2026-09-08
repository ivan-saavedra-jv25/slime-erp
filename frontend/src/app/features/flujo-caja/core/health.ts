import { MonthProjection } from './projection';

export type MonthHealth = 'ok' | 'warning' | 'critical';

export interface MonthStatus {
  health: MonthHealth;
  /** Qué está pasando, en orden de gravedad. */
  reasons: string[];
}

/**
 * Semáforo de un mes. Rojo es plata que no alcanza; amarillo es una señal que
 * todavía se puede corregir. El detalle va en `reasons` para no depender sólo
 * del color.
 */
export function monthStatus(month: MonthProjection): MonthStatus {
  const reasons: string[] = [];
  let health: MonthHealth = 'ok';

  if (month.closingBalance < 0) {
    health = 'critical';
    reasons.push('Cierra con saldo negativo');
  }

  if (month.availableBalance < 0 && month.closingBalance >= 0) {
    health = 'critical';
    reasons.push('El saldo no alcanza a cubrir los impuestos provisionados');
  }

  if (month.net < 0) {
    if (health === 'ok') health = 'warning';
    reasons.push('Se gasta más de lo que entra');
  }

  if (
    health !== 'critical' &&
    month.contingencyTarget > 0 &&
    month.availableBalance < month.contingencyTarget
  ) {
    if (health === 'ok') health = 'warning';
    reasons.push('El disponible queda bajo el colchón de imprevistos');
  }

  return { health, reasons };
}

export interface YearAlerts {
  critical: number;
  warning: number;
  /** Total de meses con algo que mirar. */
  total: number;
}

export function yearAlerts(months: readonly MonthProjection[]): YearAlerts {
  let critical = 0;
  let warning = 0;
  for (const month of months) {
    const { health } = monthStatus(month);
    if (health === 'critical') critical++;
    else if (health === 'warning') warning++;
  }
  return { critical, warning, total: critical + warning };
}
