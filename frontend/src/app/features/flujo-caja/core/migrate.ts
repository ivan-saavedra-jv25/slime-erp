import { isValidMonthKey, yearOf } from './month';
import { DEFAULT_PROVISIONS } from './seed';

type Raw = Record<string, unknown>;

function asObject(value: unknown): Raw | null {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
    ? (value as Raw)
    : null;
}

function asArray(value: unknown): Raw[] | null {
  if (!Array.isArray(value)) return null;
  const items: Raw[] = [];
  for (const item of value) {
    const obj = asObject(item);
    if (obj === null) return null;
    items.push(obj);
  }
  return items;
}

/**
 * v1 → v2: el flujo dejó de partir en cualquier mes y pasó a ser un año
 * calendario, así que `startMonth` se reduce a su año.
 */
function v1ToV2(state: Raw): Raw {
  const settings = asObject(state['settings']);
  if (settings === null || !isValidMonthKey(settings['startMonth'])) return state;
  return {
    ...state,
    version: 2,
    settings: {
      baseYear: yearOf(settings['startMonth']),
      openingBalance: settings['openingBalance'],
    },
  };
}

/**
 * v2 → v3: aparecen la clasificación de movimientos y las provisiones. Lo
 * guardado se asume operacional y fijo, que es el supuesto conservador: deja
 * todo dentro del resultado del giro hasta que el usuario reclasifique.
 */
function v2ToV3(state: Raw): Raw {
  const settings = asObject(state['settings']);
  const categories = asArray(state['categories']);
  const recurring = asArray(state['recurring']);
  const oneOff = asArray(state['oneOff']);
  if (settings === null || categories === null || recurring === null || oneOff === null) {
    return state;
  }
  return {
    ...state,
    version: 3,
    settings: { ...settings, provisions: { ...DEFAULT_PROVISIONS } },
    categories: categories.map((c) => ({
      ...c,
      nature: 'operational',
      variability: c['kind'] === 'expense' ? 'fixed' : 'variable',
    })),
    recurring: recurring.map((r) => ({ ...r, interestAmount: null })),
    oneOff: oneOff.map((o) => ({ ...o, interestAmount: null })),
  };
}

/**
 * Lleva un estado guardado a la forma actual. Devuelve el objeto crudo listo
 * para validar: `parseState` decide si sirve.
 */
export function migrateRawState(raw: unknown): unknown {
  let state = asObject(raw);
  if (state === null) return raw;
  if (state['version'] === 1) state = v1ToV2(state);
  if (state['version'] === 2) state = v2ToV3(state);
  return state;
}
