import { formatMonthLabel } from './month';
import { CashflowState, MonthKey } from './models';
import { MonthProjection } from './projection';

export type CheckStatus = 'pass' | 'warn' | 'fail' | 'info';

export interface AuditCheck {
  id: string;
  title: string;
  status: CheckStatus;
  /** Qué se está midiendo. */
  criterion: string;
  /** Hallazgo con el dato numérico, ya redactado salvo los montos. */
  finding: string;
  /** Montos a mostrar junto al hallazgo. */
  figures: { label: string; amount: number; month?: MonthKey }[];
  /** Qué hacer con el hallazgo. */
  advice: string;
}

export interface AuditReport {
  year: number;
  checks: AuditCheck[];
  passed: number;
  failed: number;
  warned: number;
}

function statusOf(months: MonthProjection[]): { negatives: MonthProjection[] } {
  return { negatives: months.filter((m) => m.closingBalance < 0) };
}

function minBy(months: MonthProjection[], value: (m: MonthProjection) => number): MonthProjection {
  return months.reduce((worst, m) => (value(m) < value(worst) ? m : worst));
}

function sum(months: MonthProjection[], value: (m: MonthProjection) => number): number {
  return months.reduce((total, m) => total + value(m), 0);
}

function share(part: number, total: number): number {
  return total === 0 ? 0 : (part / total) * 100;
}

function percent(value: number): string {
  return `${value.toFixed(1).replace('.', ',')}%`;
}

/**
 * Cuota mensual constante que el flujo aguanta sin que el disponible caiga bajo
 * cero en ningún mes. Se evalúa contra el acumulado, no sólo contra el mes.
 */
function debtCapacity(months: MonthProjection[]): number {
  const perMonth = months.map((m, i) => m.availableBalance / (i + 1));
  return Math.max(0, Math.min(...perMonth));
}

function liquidityCheck(months: MonthProjection[]): AuditCheck {
  const { negatives } = statusOf(months);
  const worst = minBy(months, (m) => m.closingBalance);
  return {
    id: 'liquidez',
    title: 'Saldo final positivo en todos los periodos',
    criterion: 'Ningún mes puede cerrar bajo cero.',
    status: negatives.length === 0 ? 'pass' : 'fail',
    finding:
      negatives.length === 0
        ? 'Los doce meses cierran en positivo.'
        : `${negatives.length} mes(es) cierran en negativo. El peor es ${formatMonthLabel(worst.month)}.`,
    figures: [
      { label: 'Saldo más bajo del año', amount: worst.closingBalance, month: worst.month },
    ],
    advice:
      negatives.length === 0
        ? 'Sin déficit de caja proyectado con los datos actuales.'
        : 'Adelanta cobros, posterga pagos no críticos o consigue financiamiento antes de ese mes.',
  };
}

function criticalPointCheck(months: MonthProjection[]): AuditCheck {
  const lowestBalance = minBy(months, (m) => m.closingBalance);
  const lowestNet = minBy(months, (m) => m.net);
  const samMonth = lowestBalance.month === lowestNet.month;
  return {
    id: 'punto-critico',
    title: 'Punto crítico de caja',
    criterion: 'Identificar el periodo de menor liquidez y su monto exacto.',
    status: 'info',
    finding: samMonth
      ? `${formatMonthLabel(lowestBalance.month)} es el mes más ajustado, tanto por saldo como por resultado.`
      : `Por saldo el punto crítico es ${formatMonthLabel(lowestBalance.month)}; por resultado del mes es ${formatMonthLabel(lowestNet.month)}, que es el que casi no genera caja.`,
    figures: [
      { label: 'Saldo más bajo', amount: lowestBalance.closingBalance, month: lowestBalance.month },
      { label: 'Resultado más bajo', amount: lowestNet.net, month: lowestNet.month },
    ],
    advice: 'Concentra el control de gastos y la gestión de cobranza en esos meses.',
  };
}

function incomeQualityCheck(months: MonthProjection[]): AuditCheck {
  const operational = sum(months, (m) => m.operationalIncome);
  const nonOperational = sum(months, (m) => m.nonOperationalIncome);
  const financing = sum(months, (m) => m.financingIncome);
  const total = operational + nonOperational + financing;
  const nonRecurringShare = share(nonOperational + financing, total);
  return {
    id: 'ingresos-clasificados',
    title: 'Ingresos operacionales separados de los no operacionales y del financiamiento',
    criterion: 'Distinguir las ventas del giro del dinero que entra por deuda o hechos puntuales.',
    status: total === 0 ? 'info' : nonRecurringShare > 20 ? 'warn' : 'pass',
    finding:
      total === 0
        ? 'Todavía no hay ingresos cargados.'
        : `${percent(share(operational, total))} de los ingresos son del giro; ${percent(nonRecurringShare)} no se repite.`,
    figures: [
      { label: 'Operacionales', amount: operational },
      { label: 'No operacionales', amount: nonOperational },
      { label: 'Financiamiento', amount: financing },
    ],
    advice:
      nonRecurringShare > 20
        ? 'Buena parte de la caja no viene del giro: el año se ve mejor de lo que la operación sostiene.'
        : 'La caja se explica por la operación, que es lo que se busca.',
  };
}

function receivablesCheck(months: MonthProjection[]): AuditCheck {
  return {
    id: 'desfase-cobro',
    title: 'Desfase de cobro',
    criterion: 'Los ingresos deben registrarse cuando el dinero entra, no cuando se factura.',
    status: 'info',
    finding:
      'El modelo es de caja pura: cada movimiento cae en el mes en que la plata se mueve. No hay fecha de factura, así que la app no puede detectar una venta cargada al facturar.',
    figures: [{ label: 'Ingresos del año', amount: sum(months, (m) => m.totalIncome) }],
    advice: 'Al cargar una venta a 30 o 60 días, anótala en el mes de cobro, no en el de emisión.',
  };
}

function expenseStructureCheck(months: MonthProjection[]): AuditCheck {
  const fixed = sum(months, (m) => m.fixedExpense);
  const variable = sum(months, (m) => m.variableExpense);
  const debt = sum(months, (m) => m.financingExpense);
  const interest = sum(months, (m) => m.debtInterest);
  const total = fixed + variable;
  const fixedShare = share(fixed, total);
  return {
    id: 'estructura-gastos',
    title: 'Gastos fijos, variables y servicio de la deuda catalogados',
    criterion:
      'Clasificar los egresos y separar las cuotas de préstamos de los costos de operación.',
    status: total === 0 ? 'info' : fixedShare > 70 ? 'warn' : 'pass',
    finding:
      total === 0
        ? 'Todavía no hay gastos cargados.'
        : debt === 0
          ? `${percent(fixedShare)} de los egresos son fijos y no hay servicio de deuda cargado.`
          : `${percent(fixedShare)} de los egresos son fijos. El servicio de deuda va separado de los costos de operación.`,
    figures: [
      { label: 'Fijos', amount: fixed },
      { label: 'Variables', amount: variable },
      { label: 'Servicio de deuda', amount: debt },
      { label: 'De eso, interés', amount: interest },
    ],
    advice:
      fixedShare > 70
        ? 'Estructura rígida: si las ventas caen, el gasto no baja con ellas.'
        : 'La estructura deja margen para ajustar gasto si caen las ventas.',
  };
}

function provisionsCheck(state: CashflowState, months: MonthProjection[]): AuditCheck {
  const { taxRatePercent, contingencyMonths } = state.settings.provisions;
  const last = months[months.length - 1];
  const both = taxRatePercent > 0 && contingencyMonths > 0;
  const none = taxRatePercent === 0 && contingencyMonths === 0;
  return {
    id: 'provisiones',
    title: 'Fondo para imprevistos e impuestos',
    criterion: 'Confirmar provisiones para pagos periódicos y contingencias.',
    status: none ? 'fail' : both ? 'pass' : 'warn',
    finding: none
      ? 'No hay provisiones configuradas: el saldo mostrado es bruto y sobreestima lo disponible.'
      : `Impuestos al ${percent(taxRatePercent)} del resultado operacional y colchón de ${contingencyMonths} mes(es) de gastos fijos.`,
    figures: [
      { label: 'Impuestos provisionados al cierre', amount: last.accumulatedTaxProvision },
      { label: 'Colchón objetivo', amount: last.contingencyTarget },
      { label: 'Falta para el colchón', amount: Math.max(0, last.contingencyGap) },
      { label: 'Disponible al cierre', amount: last.availableBalance, month: last.month },
    ],
    advice: none
      ? 'Define al menos la tasa de impuestos en Ajustes → Provisiones.'
      : last.contingencyGap > 0
        ? 'El disponible ya descuenta los impuestos, pero el colchón todavía no está constituido.'
        : 'El disponible ya descuenta los impuestos y el colchón está cubierto.',
  };
}

function capacityCheck(months: MonthProjection[]): AuditCheck {
  const minNet = minBy(months, (m) => m.net);
  const capacity = debtCapacity(months);
  const worstAvailable = minBy(months, (m) => m.availableBalance);
  return {
    id: 'capacidad-pago',
    title: 'Capacidad para asumir nuevos compromisos',
    criterion: 'Margen libre después de cubrir egresos, compromisos y provisiones.',
    status: capacity <= 0 ? 'fail' : minNet.net < 0 ? 'warn' : 'pass',
    finding:
      capacity <= 0
        ? 'No hay margen para una cuota nueva sin quedar bajo las provisiones.'
        : minNet.net < 0
          ? `El flujo aguanta la cuota indicada usando el saldo acumulado, pero ${formatMonthLabel(minNet.month)} no se autofinancia.`
          : 'El flujo aguanta la cuota indicada y cada mes se paga solo.',
    figures: [
      { label: 'Cuota mensual máxima', amount: capacity },
      { label: 'Peor resultado mensual', amount: minNet.net, month: minNet.month },
      {
        label: 'Disponible más bajo',
        amount: worstAvailable.availableBalance,
        month: worstAvailable.month,
      },
    ],
    advice:
      capacity <= 0
        ? 'Antes de tomar deuda nueva, sube el resultado operacional o baja el gasto fijo.'
        : 'La cuota máxima descuenta impuestos, pero no reserva el colchón de imprevistos.',
  };
}

function surplusCheck(months: MonthProjection[]): AuditCheck {
  const last = months[months.length - 1];
  const { negatives } = statusOf(months);
  const fixedMonthly = sum(months, (m) => m.fixedRecurringExpense) / months.length;
  // Ocioso es lo que sobra por encima del colchón, o de tres meses de gastos fijos.
  const idleThreshold = Math.max(last.contingencyTarget, fixedMonthly * 3);
  const idle = last.availableBalance > idleThreshold && idleThreshold > 0;
  return {
    id: 'excedentes',
    title: 'Destino del superávit o estrategia ante déficit',
    criterion:
      'Definir reinversión o ahorro en periodos positivos, financiamiento o recorte en los negativos.',
    status: negatives.length > 0 ? 'fail' : idle ? 'warn' : 'pass',
    finding:
      negatives.length > 0
        ? `Hay ${negatives.length} mes(es) en déficit sin una fuente de financiamiento cargada en el flujo.`
        : idle
          ? 'El disponible al cierre supera la reserva razonable y queda sin destino asignado.'
          : 'El excedente al cierre está dentro de un rango razonable de colchón operativo.',
    figures: [
      { label: 'Disponible al cierre', amount: last.availableBalance, month: last.month },
      { label: 'Reserva razonable', amount: idleThreshold },
    ],
    advice:
      negatives.length > 0
        ? 'Carga la línea de crédito o el aporte de capital como ingreso de financiamiento para ver el flujo real.'
        : idle
          ? 'Define destino: reinversión, prepago de deuda o depósito a plazo. Caja ociosa pierde valor.'
          : 'Mantén el monitoreo; no hay excedente ocioso relevante.',
  };
}

export function buildAudit(
  state: CashflowState,
  months: MonthProjection[],
  year: number,
): AuditReport {
  const checks = [
    liquidityCheck(months),
    criticalPointCheck(months),
    incomeQualityCheck(months),
    receivablesCheck(months),
    expenseStructureCheck(months),
    provisionsCheck(state, months),
    capacityCheck(months),
    surplusCheck(months),
  ];
  return {
    year,
    checks,
    passed: checks.filter((c) => c.status === 'pass').length,
    warned: checks.filter((c) => c.status === 'warn').length,
    failed: checks.filter((c) => c.status === 'fail').length,
  };
}
