import { CashflowState, OneOffItem } from './models';
import { CATEGORIA_VENTAS_ID, DEFAULT_CATEGORIES } from './seed';
import { projectMonth, projectYear } from './projection';
import { SyncedIncome } from './synced-income';

function estadoVacio(): CashflowState {
  return {
    version: 3,
    settings: { baseYear: 2026, openingBalance: 0, provisions: { taxRatePercent: 0, contingencyMonths: 0 } },
    categories: DEFAULT_CATEGORIES.map((c) => ({ ...c })),
    recurring: [],
    oneOff: [],
    overrides: [],
  };
}

function cobroDeEjemplo(overrides: Partial<SyncedIncome> = {}): SyncedIncome {
  return {
    id: 'pago-1',
    month: '2026-03',
    description: 'Cobro Cliente X — Venta V-10',
    amount: 50000,
    cuentaPorCobrarId: 3,
    ...overrides,
  };
}

describe('projectMonth con cobros sincronizados', () => {
  it('agrega el cobro como línea de ingreso del mes correspondiente', () => {
    const proyeccion = projectMonth(estadoVacio(), '2026-03', 0, 0, [cobroDeEjemplo()]);

    expect(proyeccion.incomes.length).toBe(1);
    expect(proyeccion.incomes[0].source).toBe('synced');
    expect(proyeccion.incomes[0].amount).toBe(50000);
    expect(proyeccion.incomes[0].cuentaPorCobrarId).toBe(3);
    expect(proyeccion.totalIncome).toBe(50000);
  });

  it('no agrega el cobro en un mes distinto al suyo', () => {
    const proyeccion = projectMonth(estadoVacio(), '2026-04', 0, 0, [cobroDeEjemplo({ month: '2026-03' })]);

    expect(proyeccion.incomes.length).toBe(0);
  });

  it('clasifica el cobro bajo la categoría Ventas existente', () => {
    const proyeccion = projectMonth(estadoVacio(), '2026-03', 0, 0, [cobroDeEjemplo()]);

    expect(proyeccion.incomes[0].categoryId).toBe(CATEGORIA_VENTAS_ID);
    expect(proyeccion.incomeByCategory.get(CATEGORIA_VENTAS_ID)).toBe(50000);
  });

  it('cuenta como ingreso operacional para el resultado del mes', () => {
    const proyeccion = projectMonth(estadoVacio(), '2026-03', 0, 0, [cobroDeEjemplo({ amount: 30000 })]);

    expect(proyeccion.operationalIncome).toBe(30000);
    expect(proyeccion.operationalNet).toBe(30000);
  });

  it('si la categoría Ventas fue borrada, cae en la clasificación genérica sin romper', () => {
    const estado = estadoVacio();
    estado.categories = estado.categories.filter((c) => c.id !== CATEGORIA_VENTAS_ID);

    const proyeccion = projectMonth(estado, '2026-03', 0, 0, [cobroDeEjemplo()]);

    expect(proyeccion.incomes[0].nature).toBe('operational');
    expect(proyeccion.incomes[0].variability).toBe('variable');
    expect(proyeccion.totalIncome).toBe(50000);
  });

  it('suma junto con los ingresos manuales del mismo mes sin reemplazarlos', () => {
    const estado = estadoVacio();
    const manual: OneOffItem = {
      id: 'one-1',
      kind: 'income',
      categoryId: CATEGORIA_VENTAS_ID,
      description: 'Venta proyectada',
      amount: 20000,
      month: '2026-03',
      interestAmount: null,
    };
    estado.oneOff = [manual];

    const proyeccion = projectMonth(estado, '2026-03', 0, 0, [cobroDeEjemplo({ amount: 50000 })]);

    expect(proyeccion.incomes.length).toBe(2);
    expect(proyeccion.totalIncome).toBe(70000);
  });
});

describe('projectYear con cobros sincronizados', () => {
  it('arrastra el efecto del cobro al saldo final de los meses siguientes', () => {
    const meses = projectYear(estadoVacio(), 2026, [cobroDeEjemplo({ month: '2026-01', amount: 10000 })]);

    expect(meses[0].closingBalance).toBe(10000);
    expect(meses[1].openingBalance).toBe(10000);
  });
});
