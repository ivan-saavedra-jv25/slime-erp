import { MonthKey } from './models';

const MONTH_PATTERN = /^\d{4}-(0[1-9]|1[0-2])$/;

const MONTH_LABELS = [
  'Ene',
  'Feb',
  'Mar',
  'Abr',
  'May',
  'Jun',
  'Jul',
  'Ago',
  'Sep',
  'Oct',
  'Nov',
  'Dic',
];

const MONTH_LABELS_LONG = [
  'Enero',
  'Febrero',
  'Marzo',
  'Abril',
  'Mayo',
  'Junio',
  'Julio',
  'Agosto',
  'Septiembre',
  'Octubre',
  'Noviembre',
  'Diciembre',
];

export function isValidMonthKey(value: unknown): value is MonthKey {
  return typeof value === 'string' && MONTH_PATTERN.test(value);
}

/** Descompone `"2026-03"` en `{ year: 2026, month: 3 }` (mes 1-12). */
export function parseMonth(key: MonthKey): { year: number; month: number } {
  if (!isValidMonthKey(key)) {
    throw new Error(`MonthKey inválido: ${key}`);
  }
  return { year: Number(key.slice(0, 4)), month: Number(key.slice(5, 7)) };
}

export function toMonthKey(year: number, month: number): MonthKey {
  // Normaliza meses fuera de 1-12 arrastrando el año.
  const zeroBased = month - 1;
  const normalizedYear = year + Math.floor(zeroBased / 12);
  const normalizedMonth = (((zeroBased % 12) + 12) % 12) + 1;
  return `${String(normalizedYear).padStart(4, '0')}-${String(normalizedMonth).padStart(2, '0')}`;
}

export function addMonths(key: MonthKey, delta: number): MonthKey {
  const { year, month } = parseMonth(key);
  return toMonthKey(year, month + delta);
}

/** Diferencia en meses: `monthDiff('2026-03', '2026-01') === 2`. */
export function monthDiff(a: MonthKey, b: MonthKey): number {
  const x = parseMonth(a);
  const y = parseMonth(b);
  return (x.year - y.year) * 12 + (x.month - y.month);
}

export function monthKeysFrom(start: MonthKey, count: number): MonthKey[] {
  return Array.from({ length: Math.max(0, count) }, (_, i) => addMonths(start, i));
}

/** `"2026-03"` → `"Mar 2026"`. */
export function formatMonthLabel(key: MonthKey): string {
  const { year, month } = parseMonth(key);
  return `${MONTH_LABELS[month - 1]} ${year}`;
}

/** `"2026-03"` → `"Marzo 2026"`. */
export function formatMonthLabelLong(key: MonthKey): string {
  const { year, month } = parseMonth(key);
  return `${MONTH_LABELS_LONG[month - 1]} ${year}`;
}

export function currentMonthKey(now: Date = new Date()): MonthKey {
  return toMonthKey(now.getFullYear(), now.getMonth() + 1);
}

export function currentYear(now: Date = new Date()): number {
  return now.getFullYear();
}

/** Enero del año dado: el mes en que parte todo flujo. */
export function januaryOf(year: number): MonthKey {
  return toMonthKey(year, 1);
}

export function yearOf(key: MonthKey): number {
  return parseMonth(key).year;
}

/** Convierte a `Date` en el primer día del mes (para el datepicker). */
export function monthKeyToDate(key: MonthKey): Date {
  const { year, month } = parseMonth(key);
  return new Date(year, month - 1, 1);
}

export function dateToMonthKey(date: Date): MonthKey {
  return toMonthKey(date.getFullYear(), date.getMonth() + 1);
}
