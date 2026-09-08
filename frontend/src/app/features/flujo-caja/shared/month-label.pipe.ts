import { Pipe, PipeTransform } from '@angular/core';
import { formatMonthLabel, formatMonthLabelLong } from '../core/month';
import { MonthKey } from '../core/models';

/** `"2026-03"` → `"Mar 2026"`, o `"Marzo 2026"` con el argumento `'long'`. */
@Pipe({ name: 'monthLabel', standalone: true })
export class MonthLabelPipe implements PipeTransform {
  transform(value: MonthKey | null | undefined, format: 'short' | 'long' = 'short'): string {
    if (!value) return '';
    return format === 'long' ? formatMonthLabelLong(value) : formatMonthLabel(value);
  }
}
