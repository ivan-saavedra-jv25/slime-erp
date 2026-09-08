import {
  AuditAction,
  CashAudit,
  CashCount,
  CashMovement,
  CashMovementOrigin,
  CashMovementType,
  CashSession,
  CashSessionStatus,
} from '../models';
import { DENOMINATIONS, buildCashCount, formatCLP } from '../utils/money';
import { newId } from '../utils/id';

function at(base: Date, dayOffset: number, hour: number, minute: number): Date {
  const d = new Date(base);
  d.setDate(d.getDate() + dayOffset);
  d.setHours(hour, minute, 0, 0);
  return d;
}

function countFor(denominations: readonly [number, number][], fecha: Date): CashCount {
  const items = DENOMINATIONS.map((denomination) => {
    const found = denominations.find(([d]) => d === denomination);
    return { denomination, quantity: found?.[1] ?? 0, subtotal: denomination * (found?.[1] ?? 0) };
  });
  return buildCashCount(items, fecha);
}

export interface SampleData {
  sessions: CashSession[];
  movements: CashMovement[];
  audits: CashAudit[];
}

/**
 * Escenario de ejemplo: una sesión de ayer ya cerrada (con una pequeña
 * diferencia de caja) y una sesión de hoy todavía abierta con movimientos,
 * para que Resumen, Movimientos, Entradas, Salidas e Historial muestren
 * datos reales sin tener que operar la caja a mano.
 */
export function sampleCajaData(cajaId: string, responsableId: string): SampleData {
  const hoy = new Date();
  const sessions: CashSession[] = [];
  const movements: CashMovement[] = [];
  const audits: CashAudit[] = [];

  const log = (fecha: Date, accion: AuditAction, sesionCajaId: string, descripcion: string) => {
    audits.push({
      id: newId('aud'),
      fecha,
      usuarioId: responsableId,
      accion,
      cajaId,
      sesionCajaId,
      descripcion,
    });
  };

  const mov = (
    fecha: Date,
    sesionCajaId: string,
    tipo: CashMovementType,
    concepto: string,
    monto: number,
    observacion = '',
  ): CashMovement => {
    const m: CashMovement = {
      id: newId('mov'),
      cajaId,
      sesionCajaId,
      tipo,
      origen: CashMovementOrigin.MANUAL,
      concepto,
      monto,
      observacion,
      fecha,
      responsableId,
    };
    movements.push(m);
    log(
      fecha,
      tipo === CashMovementType.ENTRADA ? AuditAction.ENTRADA_REGISTRADA : AuditAction.SALIDA_REGISTRADA,
      sesionCajaId,
      `${tipo} ${formatCLP(monto)} — ${concepto}.`,
    );
    return m;
  };

  // --- Sesión de ayer: ciclo completo, con un pequeño faltante al cierre ---
  const ayerId = newId('ses');
  const aperturaAyer = at(hoy, -1, 9, 0);
  const conteoInicialAyer = countFor(
    [
      [20000, 2],
      [10000, 1],
    ],
    aperturaAyer,
  );

  mov(at(hoy, -1, 10, 30), ayerId, CashMovementType.ENTRADA, 'Ingreso de efectivo', 120000);
  mov(at(hoy, -1, 14, 0), ayerId, CashMovementType.SALIDA, 'Pago a proveedor', 35000);
  mov(at(hoy, -1, 16, 0), ayerId, CashMovementType.SALIDA, 'Gasto', 8000, 'Insumos de aseo');
  mov(at(hoy, -1, 17, 0), ayerId, CashMovementType.ENTRADA, 'Rendición', 20000);

  const saldoEsperadoAyer = conteoInicialAyer.total + 120000 + 20000 - 35000 - 8000;
  const cierreAyer = at(hoy, -1, 19, 0);
  const conteoFinalAyer = countFor(
    [
      [20000, 5],
      [10000, 3],
      [5000, 3],
      [1000, 1],
    ],
    cierreAyer,
  );

  const sesionAyer: CashSession = {
    id: ayerId,
    cajaId,
    responsableId,
    fechaApertura: aperturaAyer,
    fechaCierre: cierreAyer,
    saldoInicial: conteoInicialAyer.total,
    saldoEsperado: saldoEsperadoAyer,
    saldoContado: conteoFinalAyer.total,
    diferencia: conteoFinalAyer.total - saldoEsperadoAyer,
    estado: CashSessionStatus.CERRADA,
    conteoInicial: conteoInicialAyer,
    conteoFinal: conteoFinalAyer,
    observacionCierre: 'Faltante de $1.000, posible error de vuelto.',
  };
  sessions.push(sesionAyer);

  log(aperturaAyer, AuditAction.CAJA_ABIERTA, ayerId, `Apertura con saldo inicial ${formatCLP(conteoInicialAyer.total)}.`);
  log(
    cierreAyer,
    AuditAction.CAJA_CERRADA,
    ayerId,
    `Cierre: esperado ${formatCLP(saldoEsperadoAyer)}, contado ${formatCLP(conteoFinalAyer.total)}, ` +
      `diferencia ${formatCLP(sesionAyer.diferencia ?? 0)}. Observación: ${sesionAyer.observacionCierre}`,
  );

  // --- Sesión de hoy: sigue abierta, con movimientos ya registrados ---
  const hoyId = newId('ses');
  const aperturaHoy = at(hoy, 0, 9, 0);
  const conteoInicialHoy = countFor([[20000, 3]], aperturaHoy);

  mov(at(hoy, 0, 11, 0), hoyId, CashMovementType.ENTRADA, 'Ingreso de efectivo', 85000);
  mov(at(hoy, 0, 12, 30), hoyId, CashMovementType.SALIDA, 'Compra', 12000, 'Café y snacks');
  mov(at(hoy, 0, 13, 15), hoyId, CashMovementType.SALIDA, 'Pago a proveedor', 25000);

  const saldoEsperadoHoy = conteoInicialHoy.total + 85000 - 12000 - 25000;

  const sesionHoy: CashSession = {
    id: hoyId,
    cajaId,
    responsableId,
    fechaApertura: aperturaHoy,
    fechaCierre: null,
    saldoInicial: conteoInicialHoy.total,
    saldoEsperado: saldoEsperadoHoy,
    saldoContado: null,
    diferencia: null,
    estado: CashSessionStatus.ABIERTA,
    conteoInicial: conteoInicialHoy,
    conteoFinal: null,
  };
  sessions.push(sesionHoy);

  log(aperturaHoy, AuditAction.CAJA_ABIERTA, hoyId, `Apertura con saldo inicial ${formatCLP(conteoInicialHoy.total)}.`);

  audits.sort((a, b) => a.fecha.getTime() - b.fecha.getTime());

  return { sessions, movements, audits };
}
