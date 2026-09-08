import { CurrencyPipe } from '@angular/common';
import { inject, LOCALE_ID, Pipe, PipeTransform } from '@angular/core';

/** Formatea montos en pesos chilenos sin decimales: `1234567` → `$1.234.567`. */
@Pipe({ name: 'clp', standalone: true })
export class ClpPipe implements PipeTransform {
  private readonly locale = inject(LOCALE_ID);
  private readonly currency = new CurrencyPipe(this.locale);

  transform(value: number | null | undefined): string {
    if (value === null || value === undefined || !Number.isFinite(value)) return '—';
    return this.currency.transform(value, 'CLP', 'symbol-narrow', '1.0-0') ?? '—';
  }
}
