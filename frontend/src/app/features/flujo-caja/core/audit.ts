import { formatMonthLabelLong } from './month';
import { MesResumen } from './flujo-caja.service';

export type AuditStatus = 'pass' | 'info' | 'warn' | 'fail';

export interface AuditExpectation {
  label: string;
  met: boolean;
  detail?: string;
}

export interface AuditFigure {
  label: string;
  value: number;
}

export interface AuditCheck {
  id: string;
  title: string;
  icon: string;
  status: AuditStatus;
  /** Titular que resume el estado del check. */
  info: string;
  expectations: AuditExpectation[];
  figures: AuditFigure[];
}

export interface AuditReport {
  year: number;
  checks: AuditCheck[];
}

const MONTHS_WITHOUT_SALDO = 0;

const avg = (values: number[]): number =>
  values.length === 0 ? 0 : values.reduce((a, b) => a + b, 0) / values.length;

const std = (values: number[]): number => {
  const m = avg(values);
  const variance = avg(values.map((v) => (v - m) ** 2));
  return Math.sqrt(variance);
};

/**
 * Sugerencias para leer el flujo de caja del año existen en un set fijo de 7
 * preguntas. Cada pregunta devuelve un diagnóstico (`pass`/`warn`/`fail`/`info`)
 * más las cifras que la respaldan.
 */
export function buildAuditReport(year: number, months: MesResumen[]): AuditReport {
  const checks = [
    liquidityCheck(months),
    criticalPointCheck(months),
    incomeRegularityCheck(months),
    receivablesCheck(months),
    expenseConcentrationCheck(months),
    capacityCheck(months),
    surplusCheck(months),
  ];
  return { year, checks };
}

/** 1. ¿Hubo liquidez todos los meses? */
function liquidityCheck(months: MesResumen[]): AuditCheck {
  const balances = months.map((m) => m.saldo);
  const minSaldo = Math.min(...balances);
  const minSaldoMonth = months[balances.indexOf(minSaldo)];
  const totalEgresos = months.reduce((a, m) => a + m.compras + m.gastos, 0);
  const avgMonthlyEgresos =
    months.length === 0 ? 0 : avg(months.map((m) => m.compras + m.gastos));
  const lowCount = months.filter((m) => m.saldo < avgMonthlyEgresos * 0.3).length;

  if (totalEgresos === 0) {
    return {
      id: 'liquidez',
      title: '¿Hubo liquidez todos los meses?',
      icon: 'account_balance_wallet',
      status: 'info',
      info: `No hay egresos registrados en tesorería para el año ${months[0]?.mes.slice(0, 4) ?? ''}, así que no hay una presión de caja que evaluar.`,
      expectations: [{ label: 'Presencia de egresos', met: false }],
      figures: [{ label: 'Saldo mínimo', value: minSaldo }],
    };
  }

  const met = minSaldo >= MONTHS_WITHOUT_SALDO && lowCount === 0;
  const status: AuditStatus = met ? 'pass' : minSaldo < 0 ? 'fail' : 'warn';
  const low = lowCount === 0 ? 'Ninguno' : `${lowCount} mesa(s)`;
  return {
    id: 'liquidez',
    title: '¿Hubo liquidez todos los meses?',
    icon: 'account_balance_wallet',
    status,
    info: met
      ? 'El saldo se mantuvo positivo todos los meses del año.'
      : minSaldo < 0
        ? 'Hubo al menos un mes con saldo negativo en caja.'
        : 'El año tuvo meses con saldos bajos, pero sin llegar a negativo.',
    expectations: [
      {
        label: 'Saldo positivo cada mes',
        met: minSaldo >= 0,
        detail: minSaldoMonth ? `Mínimo en ${formatMonthLabelLong(minSaldoMonth.mes)}` : undefined,
      },
      { label: 'Cierres holgados (≥ 30% del gasto mensual promedio)', met: lowCount === 0, detail: low },
    ],
    figures: [
      { label: 'Saldo mínimo del año', value: minSaldo },
      { label: 'Gasto mensual promedio', value: Math.round(avgMonthlyEgresos) },
    ],
  };
}

/** 2. ¿Cuál fue el punto crítico del año? */
function criticalPointCheck(months: MesResumen[]): AuditCheck {
  const balances = months.map((m) => m.saldo);
  const minSaldo = Math.min(...balances);
  const idx = balances.indexOf(minSaldo);
  const minMonth = months[idx];
  const totalMovimientos = months.reduce((a, m) => a + m.ingresos + m.compras + m.gastos, 0);

  if (totalMovimientos === 0) {
    return {
      id: 'punto-critico',
      title: '¿Cuál fue el punto crítico del año?',
      icon: 'trending_down',
      status: 'info',
      info: 'Sin movimientos en tesorería aún no hay un punto crítico que identificar.',
      expectations: [],
      figures: [],
    };
  }

  const status: AuditStatus = minSaldo < 0 ? 'warn' : 'info';
  return {
    id: 'punto-critico',
    title: '¿Cuál fue el punto crítico del año?',
    icon: 'trending_down',
    status,
    info: minMonth
      ? `El peor momento de la caja fue ${formatMonthLabelLong(minMonth.mes)}, con un saldo final de $${Math.abs(minSaldo).toLocaleString('es-CL')}.`
      : 'Sin información de saldo para el año.',
    expectations: [
      {
        label: 'Mes más ajustado identificado',
        met: Boolean(minMonth),
        detail: minMonth ? formatMonthLabelLong(minMonth.mes) : undefined,
      },
    ],
    figures: minMonth ? [{ label: `Saldo final de ${minMonth.mes}`, value: minSaldo }] : [],
  };
}

/** 3. ¿Fueron regulares los ingresos? */
function incomeRegularityCheck(months: MesResumen[]): AuditCheck {
  const incomes = months.map((m) => m.ingresos);
  const totalIngresos = incomes.reduce((a, b) => a + b, 0);
  const monthsWithIncome = incomes.filter((v) => v > 0).length;
  const mean = avg(incomes);
  const variation = mean > 0 ? std(incomes) / mean : 0;

  let status: AuditStatus = 'pass';
  let info = 'Los ingresos se mantuvieron estables mes a mes a lo largo del año.';

  if (totalIngresos === 0) {
    status = 'warn';
    info = 'No hubo ingresos cobrados (ni pagos confirmados de cuentas por cobrar) en el año.';
  } else if (variation > 0.4) {
    status = 'warn';
    info = 'Los ingresos se concentran en pocos meses; la caja depende de cobros puntuales.';
  }

  return {
    id: 'regularidad-ingresos',
    title: '¿Fueron regulares los ingresos?',
    icon: 'trending_up',
    status,
    info,
    expectations: [
      { label: 'Ingresos todos los meses', met: monthsWithIncome === months.length, detail: `${monthsWithIncome} de ${months.length}` },
      { label: 'Variación moderada (≤ 40%)', met: variation <= 0.4 },
    ],
    figures: [
      { label: 'Ingreso mensual promedio', value: Math.round(mean) },
      { label: 'Mes más fuerte', value: Math.max(...incomes, 0) },
    ],
  };
}

/** 4. ¿Qué tan rápido se cobra y en qué momentos? */
function receivablesCheck(months: MesResumen[]): AuditCheck {
  const totalIngresos = months.reduce((a, m) => a + m.ingresos, 0);
  const abono = totalIngresos === 0 ? 0 : totalIngresos / months.length;

  return {
    id: 'desfase-cobro',
    title: '¿Cómo se comportan los cobros?',
    icon: 'hourglass_bottom',
    status: 'info',
    info:
      totalIngresos === 0
        ? 'Los ingresos se registran cuando se confirman los pagos en Tesorería; aún no hay cobros en el año.'
        : `Los ingresos entran a caja cuando se confirman los cobros en Tesorería: en promedio ${Math.round(abono).toLocaleString('es-CL')} por mes.`,
    expectations: [
      {
        label: 'Cobros confirmados reflejados',
        met: totalIngresos > 0,
        detail: 'Los ingresos provienen de pagos confirmados de cuentas por cobrar',
      },
    ],
    figures: [{ label: 'Total cobrado en el año', value: totalIngresos }],
  };
}

/** 5. ¿Hay concentración en los egresos? */
function expenseConcentrationCheck(months: MesResumen[]): AuditCheck {
  const totalEgresos = months.reduce((a, m) => a + m.compras + m.gastos, 0);
  const byCategory = new Map<string, number>();
  for (const month of months) {
    for (const c of month.gastosPorCategoria) {
      byCategory.set(c.categoria, (byCategory.get(c.categoria) ?? 0) + c.total);
    }
  }
  if (totalEgresos === 0 || byCategory.size === 0) {
    return {
      id: 'concentracion',
      title: '¿Hay concentración en los egresos?',
      icon: 'pie_chart',
      status: 'info',
      info: 'Sin egresos registrados no hay concentración que evaluar.',
      expectations: [],
      figures: [],
    };
  }

  const entries = [...byCategory.entries()].sort((a, b) => b[1] - a[1]);
  const [topName, topTotal] = entries[0];
  const share = topTotal / totalEgresos;
  const status: AuditStatus = share > 0.6 ? 'warn' : 'pass';

  return {
    id: 'concentracion',
    title: '¿Hay concentración en los egresos?',
    icon: 'pie_chart',
    status,
    info:
      share > 0.6
        ? `${topName} concentra el ${Math.round(share * 100)}% de los egresos: el negocio depende de una sola partida de gasto.`
        : 'Los egresos están repartidos entre varias categorías: no hay una dependencia peligrosa de una sola partida.',
    expectations: [
      {
        label: 'Ninguna categoría supera el 60%',
        met: share <= 0.6,
        detail: `${Math.round(share * 100)}%`,
      },
    ],
    figures: [
      { label: 'Categoría dominante', value: topTotal },
      { label: 'Total de egresos del año', value: totalEgresos },
    ],
  };
}

/** 6. ¿Con qué holgura podría la caja absorber egresos? */
function capacityCheck(months: MesResumen[]): AuditCheck {
  const series = months.map((m, i) => ({ saldo: m.saldo, index: i }));
  const capacity = months.length === 0 ? 0 : Math.min(...series.map((s) => s.saldo / (s.index + 1)));
  const maxMonthEgreso = Math.max(...months.map((m) => m.compras + m.gastos), 0);

  if (capacity === 0 && maxMonthEgreso === 0) {
    return {
      id: 'capacidad',
      title: '¿Con qué holgura aguanta la caja?',
      icon: 'shield',
      status: 'info',
      info: 'Sin movimientos en tesorería no hay presión que evaluar.',
      expectations: [],
      figures: [],
    };
  }

  const met = capacity >= maxMonthEgreso;
  const status: AuditStatus = capacity < 0 ? 'fail' : met ? 'pass' : 'warn';

  return {
    id: 'capacidad',
    title: '¿Con qué holgura aguanta la caja?',
    icon: 'shield',
    status,
    info: met
      ? `La caja soporta, como gasto mensual constante, hasta ${Math.round(capacity).toLocaleString('es-CL')}: holgura para absorber el mes más caro.`
      : capacity < 0
        ? 'La caja inicia algunos meses en terreno negativo: no alcanza para sostener un egreso mensual constante.'
        : `La caja aguanta hasta ${Math.round(capacity).toLocaleString('es-CL')} por mes, pero el mes más caro llega a ${Math.round(maxMonthEgreso).toLocaleString('es-CL')}.`,
    expectations: [
      {
        label: 'Aguanta el mes más caro sin descuadrarse',
        met,
        detail: maxMonthEgreso > 0 ? `${Math.round(maxMonthEgreso).toLocaleString('es-CL')} este año` : undefined,
      },
    ],
    figures: [
      { label: 'Capacidad mensual de la caja', value: Math.round(capacity) },
      { label: 'Mes más caro', value: maxMonthEgreso },
    ],
  };
}

/** 7. ¿El año cerró con excedentes o déficits? */
function surplusCheck(months: MesResumen[]): AuditCheck {
  const totalIngresos = months.reduce((a, m) => a + m.ingresos, 0);
  const totalEgresos = months.reduce((a, m) => a + m.compras + m.gastos, 0);
  const resultado = totalIngresos - totalEgresos;
  const monthsWithResultNegative = months.filter((m) => m.resultado < 0).length;
  const finalSaldo = months.length > 0 ? months[months.length - 1].saldo : 0;

  if (totalIngresos === 0 && totalEgresos === 0) {
    return {
      id: 'excedentes',
      title: '¿El año cerró con excedentes o déficits?',
      icon: 'savings',
      status: 'info',
      info: 'Sin movimientos registrados en el año.',
      expectations: [],
      figures: [],
    };
  }

  const positiveResult = resultado > 0;
  const cushion = totalEgresos > 0 ? resultado >= totalEgresos * 0.1 : positiveResult;
  const status: AuditStatus = !positiveResult ? 'fail' : cushion ? 'pass' : 'warn';

  return {
    id: 'excedentes',
    title: '¿El año cerró con excedentes o déficits?',
    icon: 'savings',
    status,
    info: status === 'fail'
      ? 'El año cerró en terreno negativo: se gastó más de lo que se cobró.'
      : status === 'warn'
        ? 'El resultado anual es positivo pero ajustado: menos del 10% de margen sobre los gastos.'
        : 'El año cerró con excedentes: los ingresos superaron con holgura a los gastos.',
    expectations: [
      { label: 'Resultado anual positivo', met: positiveResult, detail: resultado.toLocaleString('es-CL') },
      { label: 'Colchón de al menos un 10%', met: cushion, detail: `${Math.round((totalEgresos ? (resultado / totalEgresos) * 100 : 100))}%` },
    ],
    figures: [
      { label: 'Resultado del año', value: resultado },
      { label: 'Saldo final del año', value: finalSaldo },
      { label: 'Meses con resultado negativo', value: monthsWithResultNegative },
    ],
  };
}