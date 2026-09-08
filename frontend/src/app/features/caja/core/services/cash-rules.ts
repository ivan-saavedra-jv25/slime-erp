import {
  CashCount,
  CashRegister,
  CashRegisterStatus,
  CashSession,
  CashSessionStatus,
} from '../models';
import { normalizeAmount, normalizeQuantity } from '../utils/money';
import { BusinessRuleError } from './business-rule-error';

/**
 * Reglas de negocio del spec §16, como funciones puras.
 * Los servicios las invocan antes de persistir; cada una lanza BusinessRuleError al fallar.
 */

/** Regla 1: no se puede abrir una caja que ya está abierta. */
export function assertCanOpen(caja: CashRegister, sesiones: readonly CashSession[]): void {
  if (caja.estado === CashRegisterStatus.ABIERTA) {
    throw new BusinessRuleError('CAJA_YA_ABIERTA', `La caja "${caja.nombre}" ya está abierta.`);
  }
  const activa = sesiones.some(
    (s) => s.cajaId === caja.id && s.estado === CashSessionStatus.ABIERTA,
  );
  if (activa) {
    throw new BusinessRuleError(
      'CAJA_YA_ABIERTA',
      `La caja "${caja.nombre}" tiene una sesión abierta sin cerrar.`,
    );
  }
}

/** Regla 2: no se pueden registrar movimientos en una caja cerrada. */
export function assertCanRegisterMovement(sesion: CashSession | null | undefined): asserts sesion is CashSession {
  if (!sesion) {
    throw new BusinessRuleError('SESION_NO_ABIERTA', 'No hay una sesión de caja abierta.');
  }
  if (sesion.estado !== CashSessionStatus.ABIERTA) {
    throw new BusinessRuleError(
      'SESION_NO_ABIERTA',
      'La sesión está cerrada: no admite nuevos movimientos.',
    );
  }
}

/** Regla 3: no se puede cerrar una caja sin realizar el conteo final. */
export function assertCanClose(sesion: CashSession, conteoFinal: CashCount | null): void {
  assertCanRegisterMovement(sesion);
  if (!conteoFinal || conteoFinal.items.length === 0) {
    throw new BusinessRuleError(
      'SIN_CONTEO_FINAL',
      'Debes realizar el conteo final de efectivo antes de cerrar la caja.',
    );
  }
}

/** Regla 4: una sesión cerrada no puede modificarse. */
export function assertMutable(sesion: CashSession): void {
  if (sesion.estado === CashSessionStatus.CERRADA) {
    throw new BusinessRuleError(
      'SESION_INMUTABLE',
      'La sesión ya fue cerrada y no puede modificarse.',
    );
  }
}

/** Regla 5: una salida no puede superar el saldo disponible ni dejar el saldo negativo. */
export function assertSalidaPermitida(monto: number, saldoEsperado: number): void {
  if (monto > saldoEsperado) {
    throw new BusinessRuleError(
      'SALDO_INSUFICIENTE',
      `La salida supera el saldo disponible. Disponible: ${saldoEsperado}, solicitado: ${monto}.`,
    );
  }
}

/** Reglas 11-12: montos enteros y positivos en pesos chilenos, sin decimales. */
export function assertMontoValido(monto: unknown): number {
  const normalized = normalizeAmount(monto);
  if (normalized <= 0) {
    throw new BusinessRuleError('MONTO_INVALIDO', 'El monto debe ser un entero mayor que cero.');
  }
  return normalized;
}

/** Regla 6: las cantidades de billetes/monedas no pueden ser negativas. */
export function assertCantidadValida(cantidad: unknown): number {
  const parsed = typeof cantidad === 'number' ? cantidad : Number(cantidad);
  if (Number.isFinite(parsed) && parsed < 0) {
    throw new BusinessRuleError('CANTIDAD_INVALIDA', 'La cantidad no puede ser negativa.');
  }
  return normalizeQuantity(cantidad);
}

/** El concepto del movimiento es obligatorio (spec §5, §6). */
export function assertConceptoValido(concepto: string): string {
  const trimmed = (concepto ?? '').trim();
  if (!trimmed) {
    throw new BusinessRuleError('CONCEPTO_REQUERIDO', 'Debes seleccionar un concepto.');
  }
  return trimmed;
}
