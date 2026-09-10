import { CashflowState, Category, Provisions, STATE_VERSION } from './models';
import { currentYear } from './month';

/** El flujo cubre siempre un año calendario completo: enero a diciembre. */
export const MONTHS_PER_YEAR = 12;

export const DEFAULT_PROVISIONS: Provisions = { taxRatePercent: 0, contingencyMonths: 0 };

/** Categoría a la que caen los cobros sincronizados desde Tesorería (ver `core/projection.ts`). */
export const CATEGORIA_VENTAS_ID = 'cat-ventas';

export const DEFAULT_CATEGORIES: Category[] = [
  {
    id: CATEGORIA_VENTAS_ID,
    name: 'Ventas',
    kind: 'income',
    nature: 'operational',
    variability: 'variable',
  },
  {
    id: 'cat-otros-ingresos',
    name: 'Otros ingresos',
    kind: 'income',
    nature: 'non_operational',
    variability: 'variable',
  },
  {
    id: 'cat-financiamiento',
    name: 'Préstamos recibidos',
    kind: 'income',
    nature: 'financing',
    variability: 'variable',
  },
  {
    id: 'cat-arriendo',
    name: 'Arriendo',
    kind: 'expense',
    nature: 'operational',
    variability: 'fixed',
  },
  {
    id: 'cat-sueldos',
    name: 'Sueldos',
    kind: 'expense',
    nature: 'operational',
    variability: 'fixed',
  },
  {
    id: 'cat-servicios',
    name: 'Servicios',
    kind: 'expense',
    nature: 'operational',
    variability: 'variable',
  },
  {
    id: 'cat-deuda',
    name: 'Servicio de deuda',
    kind: 'expense',
    nature: 'financing',
    variability: 'fixed',
  },
  {
    id: 'cat-otros-gastos',
    name: 'Otros gastos',
    kind: 'expense',
    nature: 'operational',
    variability: 'variable',
  },
];

export function seedState(baseYear = currentYear()): CashflowState {
  return {
    version: STATE_VERSION,
    settings: { baseYear, openingBalance: 0, provisions: { ...DEFAULT_PROVISIONS } },
    categories: DEFAULT_CATEGORIES.map((c) => ({ ...c })),
    recurring: [],
    oneOff: [],
    overrides: [],
  };
}
