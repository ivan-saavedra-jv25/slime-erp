import { januaryOf, monthKeysFrom } from './month';
import {
  CashflowState,
  Category,
  MonthKey,
  Nature,
  Override,
  RecurringItem,
  Variability,
} from './models';
import { CATEGORIA_VENTAS_ID, MONTHS_PER_YEAR } from './seed';
import { SyncedIncome } from './synced-income';

export interface Line {
  /** Id del `RecurringItem` u `OneOffItem` que origina la línea; para `source: 'synced'`, el id sintético `pago-<id>`. */
  id: string;
  source: 'recurring' | 'oneoff' | 'synced';
  description: string;
  categoryId: string;
  amount: number;
  /** `true` si un `Override` cambió el monto de la plantilla en este mes. */
  overridden: boolean;
  nature: Nature;
  variability: Variability;
  /** Parte de `amount` que es interés; el resto amortiza capital. */
  interestAmount: number | null;
  /** Solo en líneas `source: 'synced'`: la cuenta por cobrar de origen, para enlazar a Tesorería. */
  cuentaPorCobrarId?: number;
}

export interface MonthProjection {
  month: MonthKey;
  openingBalance: number;
  incomes: Line[];
  expenses: Line[];
  incomeByCategory: Map<string, number>;
  expenseByCategory: Map<string, number>;
  totalIncome: number;
  totalExpense: number;
  /** `totalIncome - totalExpense`. */
  net: number;
  /** `openingBalance + net`. Arrastra al mes siguiente. */
  closingBalance: number;

  // --- Clasificación ---
  operationalIncome: number;
  nonOperationalIncome: number;
  /** Préstamos recibidos y otros ingresos por financiamiento. */
  financingIncome: number;
  operationalExpense: number;
  nonOperationalExpense: number;
  /** Servicio de deuda: cuotas de préstamos. */
  financingExpense: number;
  /** Parte de las cuotas que amortiza capital. */
  debtPrincipal: number;
  /** Parte de las cuotas que es interés. */
  debtInterest: number;
  fixedExpense: number;
  /** Sólo los gastos fijos que se repiten mes a mes: la base del colchón. */
  fixedRecurringExpense: number;
  variableExpense: number;
  /** Caja que genera el giro: ingresos operacionales menos gastos operacionales. */
  operationalNet: number;

  // --- Provisiones ---
  /** Reserva de impuestos del mes, sobre el resultado operacional positivo. */
  taxProvision: number;
  /** Reserva de impuestos acumulada desde enero del año base. */
  accumulatedTaxProvision: number;
  /** Colchón objetivo: N meses de gastos fijos. Es una meta, no una deuda. */
  contingencyTarget: number;
  /**
   * Saldo final menos los impuestos provisionados, que sí son plata ajena. El
   * colchón no se descuenta: se compara contra este monto.
   */
  availableBalance: number;
  /** Cuánto falta para constituir el colchón. Cero o negativo = ya está cubierto. */
  contingencyGap: number;
}

/** El recurrente aplica en `month` si está dentro de su vigencia. */
export function isActiveIn(item: RecurringItem, month: MonthKey): boolean {
  if (month < item.fromMonth) return false;
  if (item.toMonth !== null && month > item.toMonth) return false;
  return true;
}

export function findOverride(
  overrides: readonly Override[],
  recurringId: string,
  month: MonthKey,
): Override | undefined {
  return overrides.find((o) => o.recurringId === recurringId && o.month === month);
}

function sumByCategory(lines: readonly Line[]): Map<string, number> {
  const totals = new Map<string, number>();
  for (const line of lines) {
    totals.set(line.categoryId, (totals.get(line.categoryId) ?? 0) + line.amount);
  }
  return totals;
}

function sumWhere(lines: readonly Line[], predicate: (line: Line) => boolean): number {
  return lines.reduce((total, line) => (predicate(line) ? total + line.amount : total), 0);
}

/** Categoría por defecto para ítems cuya categoría fue borrada del estado. */
const ORPHAN_CATEGORY: Pick<Category, 'nature' | 'variability'> = {
  nature: 'operational',
  variability: 'variable',
};

function classify(state: CashflowState): Map<string, Pick<Category, 'nature' | 'variability'>> {
  return new Map(
    state.categories.map((c) => [c.id, { nature: c.nature, variability: c.variability }]),
  );
}

export function projectMonth(
  state: CashflowState,
  month: MonthKey,
  openingBalance: number,
  accumulatedTaxProvision = 0,
  syncedIncomes: readonly SyncedIncome[] = [],
): MonthProjection {
  const categories = classify(state);
  const incomes: Line[] = [];
  const expenses: Line[] = [];

  const push = (
    id: string,
    source: Line['source'],
    kind: 'income' | 'expense',
    categoryId: string,
    description: string,
    amount: number,
    overridden: boolean,
    interestAmount: number | null,
    cuentaPorCobrarId?: number,
  ) => {
    const category = categories.get(categoryId) ?? ORPHAN_CATEGORY;
    const line: Line = {
      id,
      source,
      description,
      categoryId,
      amount,
      overridden,
      nature: category.nature,
      variability: category.variability,
      interestAmount,
      cuentaPorCobrarId,
    };
    (kind === 'income' ? incomes : expenses).push(line);
  };

  for (const item of state.recurring) {
    if (!isActiveIn(item, month)) continue;
    const override = findOverride(state.overrides, item.id, month);
    // Un override con `amount: null` omite el ítem ese mes.
    if (override && override.amount === null) continue;
    const amount = override ? override.amount! : item.amount;
    // Si el override cambia la cuota, el interés de la plantilla ya no calza.
    const interest =
      item.interestAmount === null ? null : Math.min(item.interestAmount, Math.abs(amount));
    push(
      item.id,
      'recurring',
      item.kind,
      item.categoryId,
      item.description,
      amount,
      override !== undefined,
      interest,
    );
  }

  for (const item of state.oneOff) {
    if (item.month !== month) continue;
    push(
      item.id,
      'oneoff',
      item.kind,
      item.categoryId,
      item.description,
      item.amount,
      false,
      item.interestAmount,
    );
  }

  for (const income of syncedIncomes) {
    if (income.month !== month) continue;
    push(
      income.id,
      'synced',
      'income',
      CATEGORIA_VENTAS_ID,
      income.description,
      income.amount,
      false,
      null,
      income.cuentaPorCobrarId,
    );
  }

  const totalIncome = incomes.reduce((sum, l) => sum + l.amount, 0);
  const totalExpense = expenses.reduce((sum, l) => sum + l.amount, 0);
  const net = totalIncome - totalExpense;

  const operationalIncome = sumWhere(incomes, (l) => l.nature === 'operational');
  const operationalExpense = sumWhere(expenses, (l) => l.nature === 'operational');
  const operationalNet = operationalIncome - operationalExpense;

  const financingLines = expenses.filter((l) => l.nature === 'financing');
  const debtInterest = financingLines.reduce((sum, l) => sum + (l.interestAmount ?? 0), 0);
  const financingExpense = financingLines.reduce((sum, l) => sum + l.amount, 0);

  const fixedExpense = sumWhere(expenses, (l) => l.variability === 'fixed');
  // Un pago puntual en una categoría fija (un aguinaldo) no es compromiso mensual.
  const fixedRecurringExpense = sumWhere(
    expenses,
    (l) => l.variability === 'fixed' && l.source === 'recurring',
  );
  const { taxRatePercent, contingencyMonths } = state.settings.provisions;
  // Sólo se provisiona sobre resultado operacional positivo: sin utilidad no hay impuesto.
  const taxProvision = Math.max(0, operationalNet) * (taxRatePercent / 100);
  const totalTaxProvision = accumulatedTaxProvision + taxProvision;
  const contingencyTarget = fixedRecurringExpense * contingencyMonths;
  const closingBalance = openingBalance + net;
  const availableBalance = closingBalance - totalTaxProvision;

  return {
    month,
    openingBalance,
    incomes,
    expenses,
    incomeByCategory: sumByCategory(incomes),
    expenseByCategory: sumByCategory(expenses),
    totalIncome,
    totalExpense,
    net,
    closingBalance,

    operationalIncome,
    nonOperationalIncome: sumWhere(incomes, (l) => l.nature === 'non_operational'),
    financingIncome: sumWhere(incomes, (l) => l.nature === 'financing'),
    operationalExpense,
    nonOperationalExpense: sumWhere(expenses, (l) => l.nature === 'non_operational'),
    financingExpense,
    debtPrincipal: financingExpense - debtInterest,
    debtInterest,
    fixedExpense,
    fixedRecurringExpense,
    variableExpense: sumWhere(expenses, (l) => l.variability === 'variable'),
    operationalNet,

    taxProvision,
    accumulatedTaxProvision: totalTaxProvision,
    contingencyTarget,
    availableBalance,
    contingencyGap: contingencyTarget - availableBalance,
  };
}

export function projectMonths(
  state: CashflowState,
  months: readonly MonthKey[],
  openingBalance: number,
  syncedIncomes: readonly SyncedIncome[] = [],
): MonthProjection[] {
  const result: MonthProjection[] = [];
  let balance = openingBalance;
  let taxProvision = 0;
  for (const month of months) {
    const projection = projectMonth(state, month, balance, taxProvision, syncedIncomes);
    result.push(projection);
    balance = projection.closingBalance;
    taxProvision = projection.accumulatedTaxProvision;
  }
  return result;
}

/**
 * Proyecta desde enero del año base hasta diciembre de `throughYear`. Los años
 * se encadenan igual que los meses: enero de un año parte con el cierre de
 * diciembre del anterior.
 */
export function projectThrough(
  state: CashflowState,
  throughYear: number,
  syncedIncomes: readonly SyncedIncome[] = [],
): MonthProjection[] {
  const { baseYear, openingBalance } = state.settings;
  const years = Math.max(1, throughYear - baseYear + 1);
  const months = monthKeysFrom(januaryOf(baseYear), years * MONTHS_PER_YEAR);
  return projectMonths(state, months, openingBalance, syncedIncomes);
}

/** Los 12 meses de `year`, con el saldo ya encadenado desde el año base. */
export function projectYear(
  state: CashflowState,
  year: number,
  syncedIncomes: readonly SyncedIncome[] = [],
): MonthProjection[] {
  return projectThrough(state, year, syncedIncomes).slice(-MONTHS_PER_YEAR);
}
