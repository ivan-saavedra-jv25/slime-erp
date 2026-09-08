import { Inject, Injectable, InjectionToken } from '@angular/core';
import {
  CashflowState,
  Category,
  ItemKind,
  Nature,
  OneOffItem,
  Override,
  Provisions,
  RecurringItem,
  STATE_VERSION,
  Settings,
  Variability,
} from './models';
import { isValidMonthKey } from './month';
import { migrateRawState } from './migrate';
import { seedState } from './seed';

export const STORAGE_KEY = 'flujo-caja:v1';

/** Subconjunto de `Storage` que necesita la app. */
export interface StorageLike {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}

function resolveLocalStorage(): StorageLike | null {
  try {
    const candidate = (globalThis as { localStorage?: unknown }).localStorage as
      StorageLike | undefined;
    if (
      candidate &&
      typeof candidate.getItem === 'function' &&
      typeof candidate.setItem === 'function' &&
      typeof candidate.removeItem === 'function'
    ) {
      return candidate;
    }
  } catch {
    // Acceso bloqueado (modo privado, permisos del navegador).
  }
  return null;
}

/** `null` cuando el navegador no expone un almacenamiento utilizable. */
export const STORAGE_BACKEND = new InjectionToken<StorageLike | null>('STORAGE_BACKEND', {
  providedIn: 'root',
  factory: resolveLocalStorage,
});

export interface LoadResult {
  state: CashflowState;
  /** `true` si había datos guardados pero no se pudieron leer. */
  corrupted: boolean;
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isKind(value: unknown): value is ItemKind {
  return value === 'income' || value === 'expense';
}

function isNature(value: unknown): value is Nature {
  return value === 'operational' || value === 'non_operational' || value === 'financing';
}

function isVariability(value: unknown): value is Variability {
  return value === 'fixed' || value === 'variable';
}

function isPercent(value: unknown): value is number {
  return isFiniteNumber(value) && value >= 0 && value <= 100;
}

/** `null` o un monto positivo: el interés nunca puede superar la cuota. */
function parseInterest(value: unknown, amount: number): number | null | undefined {
  if (value === null || value === undefined) return null;
  if (!isFiniteNumber(value) || value < 0 || value > Math.abs(amount)) return undefined;
  return value;
}

function parseProvisions(raw: unknown): Provisions | null {
  if (!isObject(raw)) return null;
  const months = raw['contingencyMonths'];
  if (!isPercent(raw['taxRatePercent'])) return null;
  if (!isFiniteNumber(months) || months < 0 || months > 24) return null;
  return { taxRatePercent: raw['taxRatePercent'], contingencyMonths: months };
}

function isFiniteNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.length > 0;
}

function parseSettings(raw: unknown): Settings | null {
  if (!isObject(raw)) return null;
  const year = raw['baseYear'];
  if (!isFiniteNumber(year) || year < 1970 || year > 2999) return null;
  if (!isFiniteNumber(raw['openingBalance'])) return null;
  const provisions = parseProvisions(raw['provisions']);
  if (provisions === null) return null;
  return { baseYear: Math.trunc(year), openingBalance: raw['openingBalance'], provisions };
}

function parseCategory(raw: unknown): Category | null {
  if (!isObject(raw)) return null;
  if (!isNonEmptyString(raw['id']) || !isNonEmptyString(raw['name']) || !isKind(raw['kind'])) {
    return null;
  }
  if (!isNature(raw['nature']) || !isVariability(raw['variability'])) return null;
  return {
    id: raw['id'],
    name: raw['name'],
    kind: raw['kind'],
    nature: raw['nature'],
    variability: raw['variability'],
  };
}

function parseRecurring(raw: unknown): RecurringItem | null {
  if (!isObject(raw)) return null;
  if (!isNonEmptyString(raw['id']) || !isKind(raw['kind'])) return null;
  if (!isNonEmptyString(raw['categoryId']) || typeof raw['description'] !== 'string') return null;
  if (!isFiniteNumber(raw['amount'])) return null;
  if (!isValidMonthKey(raw['fromMonth'])) return null;
  const to = raw['toMonth'];
  if (to !== null && !isValidMonthKey(to)) return null;
  const interest = parseInterest(raw['interestAmount'], raw['amount']);
  if (interest === undefined) return null;
  return {
    id: raw['id'],
    kind: raw['kind'],
    categoryId: raw['categoryId'],
    description: raw['description'],
    amount: raw['amount'],
    fromMonth: raw['fromMonth'],
    toMonth: to as string | null,
    interestAmount: interest,
  };
}

function parseOneOff(raw: unknown): OneOffItem | null {
  if (!isObject(raw)) return null;
  if (!isNonEmptyString(raw['id']) || !isKind(raw['kind'])) return null;
  if (!isNonEmptyString(raw['categoryId']) || typeof raw['description'] !== 'string') return null;
  if (!isFiniteNumber(raw['amount'])) return null;
  if (!isValidMonthKey(raw['month'])) return null;
  const interest = parseInterest(raw['interestAmount'], raw['amount']);
  if (interest === undefined) return null;
  return {
    id: raw['id'],
    kind: raw['kind'],
    categoryId: raw['categoryId'],
    description: raw['description'],
    amount: raw['amount'],
    month: raw['month'],
    interestAmount: interest,
  };
}

function parseOverride(raw: unknown): Override | null {
  if (!isObject(raw)) return null;
  if (!isNonEmptyString(raw['recurringId']) || !isValidMonthKey(raw['month'])) return null;
  const amount = raw['amount'];
  if (amount !== null && !isFiniteNumber(amount)) return null;
  return { recurringId: raw['recurringId'], month: raw['month'], amount: amount as number | null };
}

function parseAll<T>(raw: unknown, parse: (item: unknown) => T | null): T[] | null {
  if (!Array.isArray(raw)) return null;
  const parsed: T[] = [];
  for (const item of raw) {
    const value = parse(item);
    if (value === null) return null;
    parsed.push(value);
  }
  return parsed;
}

/** Valida la forma completa del estado. Devuelve `null` si algo no calza. */
export function parseState(raw: unknown): CashflowState | null {
  if (!isObject(raw)) return null;
  if (raw['version'] !== STATE_VERSION) return null;
  const settings = parseSettings(raw['settings']);
  const categories = parseAll(raw['categories'], parseCategory);
  const recurring = parseAll(raw['recurring'], parseRecurring);
  const oneOff = parseAll(raw['oneOff'], parseOneOff);
  const overrides = parseAll(raw['overrides'], parseOverride);
  if (!settings || !categories || !recurring || !oneOff || !overrides) return null;
  return { version: STATE_VERSION, settings, categories, recurring, oneOff, overrides };
}

@Injectable({ providedIn: 'root' })
export class StorageService {
  constructor(@Inject(STORAGE_BACKEND) private readonly backend: StorageLike | null) {}

  load(): LoadResult {
    if (this.backend === null) return { state: seedState(), corrupted: false };
    let raw: string | null = null;
    try {
      raw = this.backend.getItem(STORAGE_KEY);
    } catch {
      // Almacenamiento bloqueado: se parte de cero.
      return { state: seedState(), corrupted: false };
    }
    if (raw === null) return { state: seedState(), corrupted: false };
    const state = this.deserialize(raw);
    if (state === null) return { state: seedState(), corrupted: true };
    return { state, corrupted: false };
  }

  save(state: CashflowState): void {
    if (this.backend === null) return;
    try {
      this.backend.setItem(STORAGE_KEY, JSON.stringify(state));
    } catch {
      // Cuota llena o almacenamiento bloqueado: la app sigue funcionando en memoria.
    }
  }

  clear(): void {
    if (this.backend === null) return;
    try {
      this.backend.removeItem(STORAGE_KEY);
    } catch {
      // Nada que hacer si el almacenamiento no está disponible.
    }
  }

  /** Parsea y valida un JSON de respaldo. `null` si no es un estado válido. */
  deserialize(json: string): CashflowState | null {
    try {
      return parseState(migrateRawState(JSON.parse(json)));
    } catch {
      return null;
    }
  }

  serialize(state: CashflowState): string {
    return JSON.stringify(state, null, 2);
  }

  /** Dispara la descarga del respaldo en el navegador. */
  download(state: CashflowState, now: Date = new Date()): void {
    const stamp = [
      now.getFullYear(),
      String(now.getMonth() + 1).padStart(2, '0'),
      String(now.getDate()).padStart(2, '0'),
    ].join('-');
    const blob = new Blob([this.serialize(state)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `flujo-caja-${stamp}.json`;
    anchor.click();
    URL.revokeObjectURL(url);
  }
}
