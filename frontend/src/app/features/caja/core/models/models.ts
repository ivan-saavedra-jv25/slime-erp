import {
  AuditAction,
  CashMovementOrigin,
  CashMovementType,
  CashRegisterStatus,
  CashRegisterType,
  CashSessionStatus,
} from './enums';

/** Usuario mock: en esta versión no hay autenticación real (spec §1). */
export interface User {
  id: string;
  nombre: string;
}

/** Sucursal mock. */
export interface Branch {
  id: string;
  nombre: string;
}

/** Caja física o lógica administrada por el módulo (spec §1). */
export interface CashRegister {
  id: string;
  nombre: string;
  tipo: CashRegisterType;
  sucursalId: string;
  responsableId: string;
  estado: CashRegisterStatus;
  /** Saldo con el que se abrió la sesión vigente. 0 si la caja está cerrada. */
  saldoInicial: number;
  /** Último saldo esperado conocido. Snapshot para listados; la fuente de verdad son los movimientos. */
  saldoActual: number;
  fechaCreacion: Date;
  fechaActualizacion: Date;
}

/** Una línea del conteo físico: cuántos billetes/monedas de una denominación (spec §14). */
export interface CashCountItem {
  denomination: number;
  quantity: number;
  subtotal: number;
}

/** Conteo completo de efectivo con su total ya calculado (spec §13). */
export interface CashCount {
  items: CashCountItem[];
  total: number;
  fecha: Date;
}

/**
 * Ciclo de vida completo de una caja: apertura → movimientos → arqueo → cierre (spec §3).
 * Una caja tiene muchas sesiones históricas.
 */
export interface CashSession {
  id: string;
  cajaId: string;
  responsableId: string;
  fechaApertura: Date;
  fechaCierre: Date | null;
  saldoInicial: number;
  /** Snapshot congelado al cerrar. Mientras la sesión está abierta se deriva de los movimientos. */
  saldoEsperado: number;
  saldoContado: number | null;
  /** contado - esperado. Se conserva siempre, incluso si es 0 (spec §16.8). */
  diferencia: number | null;
  estado: CashSessionStatus;
  conteoInicial: CashCount;
  conteoFinal: CashCount | null;
  /** Explicación de la diferencia al cerrar (spec §10). */
  observacionCierre?: string;
}

/** Entrada o salida de efectivo dentro de una sesión (spec §5, §6). */
export interface CashMovement {
  id: string;
  cajaId: string;
  sesionCajaId: string;
  tipo: CashMovementType;
  origen: CashMovementOrigin;
  concepto: string;
  monto: number;
  observacion: string;
  fecha: Date;
  responsableId: string;
}

/** Registro de auditoría. Solo se agrega, nunca se elimina (spec §15). */
export interface CashAudit {
  id: string;
  fecha: Date;
  usuarioId: string;
  accion: AuditAction;
  cajaId: string;
  sesionCajaId: string | null;
  descripcion: string;
}
