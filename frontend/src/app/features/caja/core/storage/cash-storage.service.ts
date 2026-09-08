import { Injectable } from '@angular/core';
import { SCHEMA_VERSION, SCHEMA_VERSION_KEY, STORAGE_PREFIX, StorageKey } from './storage-keys';

/** Entidad persistible: todo lo que guardamos tiene id. */
interface Identifiable {
  id: string;
}

/** Campos que deben rehidratarse de string ISO a Date al leer. */
const DATE_FIELDS = new Set([
  'fecha',
  'fechaApertura',
  'fechaCierre',
  'fechaCreacion',
  'fechaActualizacion',
]);

const ISO_DATE = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z$/;

/**
 * Único punto de la aplicación que toca LocalStorage (spec §12).
 * Los componentes nunca lo usan directamente: siempre pasan por los servicios de dominio.
 */
@Injectable({ providedIn: 'root' })
export class CashStorageService {
  constructor() {
    this.ensureSchemaVersion();
  }

  /** Lee una colección. Devuelve [] si no existe o si el contenido está corrupto. */
  read<T extends Identifiable>(key: StorageKey): T[] {
    const raw = this.getItem(key);
    if (!raw) return [];
    try {
      const parsed = JSON.parse(raw, this.dateReviver);
      return Array.isArray(parsed) ? (parsed as T[]) : [];
    } catch {
      // Payload corrupto: no reventamos la app, arrancamos vacíos.
      console.warn(`[CashStorage] Contenido inválido en "${key}". Se ignora.`);
      return [];
    }
  }

  /** Reemplaza la colección completa. */
  write<T extends Identifiable>(key: StorageKey, items: readonly T[]): void {
    this.setItem(key, JSON.stringify(items));
  }

  /** Inserta o actualiza por id y devuelve la colección resultante. */
  upsert<T extends Identifiable>(key: StorageKey, item: T): T[] {
    const items = this.read<T>(key);
    const index = items.findIndex((existing) => existing.id === item.id);
    if (index >= 0) {
      items[index] = item;
    } else {
      items.push(item);
    }
    this.write(key, items);
    return items;
  }

  /** Agrega un elemento al final sin buscar duplicados (para auditoría, que solo crece). */
  append<T extends Identifiable>(key: StorageKey, item: T): T[] {
    const items = this.read<T>(key);
    items.push(item);
    this.write(key, items);
    return items;
  }

  /** Elimina por id. No debe usarse para auditoría (spec §15). */
  remove<T extends Identifiable>(key: StorageKey, id: string): T[] {
    const items = this.read<T>(key).filter((item) => item.id !== id);
    this.write(key, items);
    return items;
  }

  /** Borra todos los datos del módulo. Solo para reset manual / tests. */
  clearAll(keys: readonly StorageKey[]): void {
    for (const key of keys) {
      try {
        localStorage.removeItem(STORAGE_PREFIX + key);
      } catch {
        // LocalStorage no disponible: nada que limpiar.
      }
    }
  }

  private getItem(key: StorageKey): string | null {
    try {
      return localStorage.getItem(STORAGE_PREFIX + key);
    } catch {
      // Modo privado o storage bloqueado.
      return null;
    }
  }

  private setItem(key: StorageKey, value: string): void {
    try {
      localStorage.setItem(STORAGE_PREFIX + key, value);
    } catch (error) {
      // QuotaExceededError o storage bloqueado: avisamos sin romper el flujo.
      console.error(`[CashStorage] No se pudo escribir "${key}".`, error);
    }
  }

  /** Convierte strings ISO en Date al deserializar. */
  private dateReviver = (key: string, value: unknown): unknown => {
    if (typeof value === 'string' && DATE_FIELDS.has(key) && ISO_DATE.test(value)) {
      return new Date(value);
    }
    return value;
  };

  private ensureSchemaVersion(): void {
    try {
      if (localStorage.getItem(SCHEMA_VERSION_KEY) === null) {
        localStorage.setItem(SCHEMA_VERSION_KEY, String(SCHEMA_VERSION));
      }
    } catch {
      // Sin storage no hay versión que mantener.
    }
  }
}
