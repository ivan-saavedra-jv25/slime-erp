import { CashCount, CashCountItem, CashDifferenceStatus, CashMovement, CashMovementType } from '../models';

/**
 * Denominaciones de billetes y monedas en pesos chilenos, de mayor a menor (spec §2).
 * Los montos son SIEMPRE enteros: CLP no usa decimales (spec §16.11-12).
 */
export const DENOMINATIONS: readonly number[] = [20000, 10000, 5000, 2000, 1000, 500, 100, 50, 10];

/**
 * Normaliza una cantidad ingresada por el usuario: entero no negativo.
 * Protege contra negativos, decimales, NaN y strings vacíos (spec §16.6).
 */
export function normalizeQuantity(value: unknown): number {
  const parsed = typeof value === 'number' ? value : Number(value);
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return 0;
  }
  return Math.trunc(parsed);
}

/**
 * Normaliza un monto en pesos: entero no negativo, sin decimales (spec §16.11-12).
 */
export function normalizeAmount(value: unknown): number {
  return normalizeQuantity(value);
}

/** Conteo vacío con todas las denominaciones en cantidad 0. */
export function emptyCountItems(): CashCountItem[] {
  return DENOMINATIONS.map((denomination) => ({ denomination, quantity: 0, subtotal: 0 }));
}

/** Recalcula el subtotal de una línea: denominación × cantidad (spec §2). */
export function buildCountItem(denomination: number, quantity: unknown): CashCountItem {
  const qty = normalizeQuantity(quantity);
  return { denomination, quantity: qty, subtotal: denomination * qty };
}

/** Total de efectivo del conteo: Σ(denominación × cantidad) (spec §14). */
export function countTotal(items: readonly CashCountItem[]): number {
  return items.reduce((sum, item) => sum + item.denomination * normalizeQuantity(item.quantity), 0);
}

/** Empaqueta un conteo con su total ya calculado. El usuario nunca escribe el total (spec §2). */
export function buildCashCount(items: readonly CashCountItem[], fecha: Date = new Date()): CashCount {
  const normalized = items.map((item) => buildCountItem(item.denomination, item.quantity));
  return { items: normalized, total: countTotal(normalized), fecha };
}

/** Suma de todas las entradas de una lista de movimientos. */
export function totalEntradas(movimientos: readonly CashMovement[]): number {
  return movimientos
    .filter((m) => m.tipo === CashMovementType.ENTRADA)
    .reduce((sum, m) => sum + m.monto, 0);
}

/** Suma de todas las salidas de una lista de movimientos. */
export function totalSalidas(movimientos: readonly CashMovement[]): number {
  return movimientos
    .filter((m) => m.tipo === CashMovementType.SALIDA)
    .reduce((sum, m) => sum + m.monto, 0);
}

/**
 * Saldo esperado = saldo inicial + entradas − salidas (spec §4).
 * Siempre derivado, nunca un campo mutable que pueda desincronizarse.
 */
export function expectedBalance(saldoInicial: number, movimientos: readonly CashMovement[]): number {
  return saldoInicial + totalEntradas(movimientos) - totalSalidas(movimientos);
}

/**
 * Diferencia de caja = efectivo contado − saldo esperado (spec §8).
 * Positivo = sobrante, negativo = faltante, 0 = caja cuadrada. No se redondea.
 */
export function difference(saldoEsperado: number, saldoContado: number): number {
  return saldoContado - saldoEsperado;
}

/** Clasifica la diferencia para mostrarla en pantalla (spec §8). */
export function differenceStatus(diferencia: number): CashDifferenceStatus {
  if (diferencia === 0) return CashDifferenceStatus.CUADRADA;
  return diferencia > 0 ? CashDifferenceStatus.SOBRANTE : CashDifferenceStatus.FALTANTE;
}

/** Formatea un entero CLP como "$156.630". Sin decimales (spec §16.12). */
export function formatCLP(amount: number): string {
  const negative = amount < 0;
  const digits = Math.abs(Math.trunc(amount)).toLocaleString('es-CL');
  return `${negative ? '-' : ''}$${digits}`;
}
