/** Mes en formato `YYYY-MM`. El orden lexicográfico coincide con el cronológico. */
export type MonthKey = string;

export type ItemKind = 'income' | 'expense';

/**
 * Origen del movimiento. Separa la caja que genera el giro de la que entra o
 * sale por financiamiento, que es lo que distingue un negocio sano de uno que
 * se sostiene con deuda.
 */
export type Nature = 'operational' | 'non_operational' | 'financing';

/** Sólo aplica a gastos: un gasto fijo se paga aunque no haya ventas. */
export type Variability = 'fixed' | 'variable';

export interface Provisions {
  /** % del resultado operacional positivo que se reserva para impuestos. */
  taxRatePercent: number;
  /** Meses de gastos fijos que se quieren mantener como colchón. */
  contingencyMonths: number;
}

export interface Settings {
  /** Primer año del flujo. Enero de este año parte con `openingBalance`. */
  baseYear: number;
  /** Monto con el que se parte en enero del año base. */
  openingBalance: number;
  provisions: Provisions;
}

export interface Category {
  id: string;
  name: string;
  kind: ItemKind;
  nature: Nature;
  /** Sólo se usa cuando `kind` es `expense`. */
  variability: Variability;
}

/** Ítem que se repite todos los meses dentro de su vigencia (arriendo, sueldos). */
export interface RecurringItem {
  id: string;
  kind: ItemKind;
  categoryId: string;
  description: string;
  amount: number;
  fromMonth: MonthKey;
  /** `null` = sin fecha de término. */
  toMonth: MonthKey | null;
  /**
   * Parte de `amount` que es interés, en cuotas de préstamos. El resto amortiza
   * capital. `null` cuando el ítem no es servicio de deuda.
   */
  interestAmount: number | null;
}

/** Movimiento de un mes puntual. */
export interface OneOffItem {
  id: string;
  kind: ItemKind;
  categoryId: string;
  description: string;
  amount: number;
  month: MonthKey;
  interestAmount: number | null;
}

/** Ajuste del monto de un recurrente en un mes específico. */
export interface Override {
  recurringId: string;
  month: MonthKey;
  /** `null` = el recurrente se omite ese mes. */
  amount: number | null;
}

export interface CashflowState {
  version: number;
  settings: Settings;
  categories: Category[];
  recurring: RecurringItem[];
  oneOff: OneOffItem[];
  overrides: Override[];
}

export const STATE_VERSION = 3;
