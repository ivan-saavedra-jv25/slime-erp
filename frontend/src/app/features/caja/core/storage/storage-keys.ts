/** Claves de LocalStorage usadas por el módulo (spec §12). */
export const STORAGE_KEYS = {
  registers: 'cash_registers',
  sessions: 'cash_sessions',
  movements: 'cash_movements',
  audits: 'cash_audits',
} as const;

export type StorageKey = (typeof STORAGE_KEYS)[keyof typeof STORAGE_KEYS];

/** Prefijo para no chocar con otras apps en el mismo origen. */
export const STORAGE_PREFIX = 'fc.';

/** Versión del esquema, para futuras migraciones. */
export const SCHEMA_VERSION = 1;
export const SCHEMA_VERSION_KEY = `${STORAGE_PREFIX}schema_version`;
