import { newId } from './id';
import { januaryOf, toMonthKey } from './month';
import { CashflowState } from './models';
import { seedState } from './seed';

/**
 * Flujo de ejemplo de una empresa chica: ventas mensuales como ingreso, y
 * sueldos, arriendo y servicios como gastos fijos. Incluye un par de
 * movimientos puntuales y un ajuste de un solo mes para mostrar cómo se usan.
 */
export function sampleState(baseYear: number): CashflowState {
  const state = seedState(baseYear);
  const enero = januaryOf(baseYear);
  const month = (m: number) => toMonthKey(baseYear, m);

  state.settings.openingBalance = 3_000_000;
  // Impuesto de primera categoría y dos meses de gastos fijos como colchón.
  state.settings.provisions = { taxRatePercent: 27, contingencyMonths: 2 };

  state.recurring = [
    {
      id: newId('rec'),
      kind: 'income',
      categoryId: 'cat-ventas',
      description: 'Ventas mensuales',
      amount: 8_000_000,
      fromMonth: enero,
      toMonth: null,
      interestAmount: null,
    },
    {
      id: newId('rec'),
      kind: 'expense',
      categoryId: 'cat-sueldos',
      description: 'Sueldos y cotizaciones',
      amount: 2_600_000,
      fromMonth: enero,
      toMonth: null,
      interestAmount: null,
    },
    {
      id: newId('rec'),
      kind: 'expense',
      categoryId: 'cat-arriendo',
      description: 'Arriendo oficina',
      amount: 450_000,
      fromMonth: enero,
      toMonth: null,
      interestAmount: null,
    },
    {
      id: newId('rec'),
      kind: 'expense',
      categoryId: 'cat-servicios',
      description: 'Luz, agua e internet',
      amount: 220_000,
      fromMonth: enero,
      toMonth: null,
      interestAmount: null,
    },
    {
      id: newId('rec'),
      kind: 'expense',
      categoryId: 'cat-deuda',
      description: 'Cuota crédito capital de trabajo',
      amount: 620_000,
      fromMonth: enero,
      // Tres años de cuotas: el servicio de deuda no es un gasto de operación.
      toMonth: toMonthKey(baseYear + 2, 12),
      interestAmount: 180_000,
    },
  ];

  const ventasId = state.recurring[0].id;

  state.oneOff = [
    {
      id: newId('one'),
      kind: 'expense',
      categoryId: 'cat-otros-gastos',
      description: 'Patente municipal',
      amount: 380_000,
      month: month(1),
      interestAmount: null,
    },
    {
      id: newId('one'),
      kind: 'expense',
      categoryId: 'cat-otros-gastos',
      description: 'Reparación de maquinaria',
      amount: 4_500_000,
      month: month(4),
      interestAmount: null,
    },
    {
      id: newId('one'),
      kind: 'expense',
      categoryId: 'cat-sueldos',
      description: 'Indemnizaciones por reestructuración',
      amount: 6_000_000,
      month: month(5),
      interestAmount: null,
    },
    {
      id: newId('one'),
      kind: 'income',
      categoryId: 'cat-otros-ingresos',
      description: 'Venta de equipo en desuso',
      amount: 1_200_000,
      month: month(7),
      interestAmount: null,
    },
    {
      id: newId('one'),
      kind: 'expense',
      categoryId: 'cat-sueldos',
      description: 'Aguinaldo fiestas patrias',
      amount: 900_000,
      month: month(9),
      interestAmount: null,
    },
    {
      id: newId('one'),
      kind: 'expense',
      categoryId: 'cat-sueldos',
      description: 'Aguinaldo navidad',
      amount: 900_000,
      month: month(12),
      interestAmount: null,
    },
  ];

  // Caída de ventas de febrero a mayo. Junto con los dos sobregastos deja el
  // flujo en rojo en mayo: es el caso que hace visible el semáforo.
  state.overrides = [
    { recurringId: ventasId, month: month(2), amount: 5_800_000 },
    { recurringId: ventasId, month: month(3), amount: 3_400_000 },
    { recurringId: ventasId, month: month(4), amount: 2_900_000 },
    { recurringId: ventasId, month: month(5), amount: 3_600_000 },
  ];

  return state;
}
