import { Injectable, computed, inject, signal } from '@angular/core';
import {
  AuditAction,
  CashCount,
  CashCountItem,
  CashMovement,
  CashMovementType,
  CashRegister,
  CashSession,
} from '../models';
import { MOCK_USER } from '../seed/mock-data';
import {
  buildCashCount,
  differenceStatus,
  expectedBalance,
  formatCLP,
  totalEntradas,
  totalSalidas,
} from '../utils/money';
import { CashAuditService } from './cash-audit.service';
import { CashMovementService } from './cash-movement.service';
import { CashRegisterService } from './cash-register.service';
import {
  assertCanClose,
  assertCanOpen,
  assertCanRegisterMovement,
  assertConceptoValido,
  assertMontoValido,
  assertSalidaPermitida,
} from './cash-rules';
import { CashSessionService } from './cash-session.service';

/**
 * Orquesta el flujo apertura → movimientos → arqueo → cierre.
 * Es el único punto donde se aplican las reglas de negocio antes de persistir,
 * y la única API que consumen los componentes.
 */
@Injectable({ providedIn: 'root' })
export class CajaFacade {
  private readonly registers = inject(CashRegisterService);
  private readonly sessions = inject(CashSessionService);
  private readonly movements = inject(CashMovementService);
  private readonly audit = inject(CashAuditService);

  /** Caja sobre la que opera la UI. Por defecto, la que tenga sesión abierta. */
  private readonly cajaSeleccionadaId = signal<string | null>(null);

  /**
   * Conteo del arqueo en curso, compartido entre la pantalla Arqueo y la de Cierre.
   * No se persiste hasta confirmar el cierre.
   */
  private readonly arqueoEnCurso = signal<CashCount | null>(null);

  readonly cajas = this.registers.all;
  readonly historial = this.sessions.historial;
  readonly auditoria = this.audit.all;

  readonly cajaActual = computed<CashRegister | null>(() => {
    const id = this.cajaSeleccionadaId();
    if (id) return this.registers.byId(id) ?? null;
    const abierta = this.sessions.active();
    if (abierta) return this.registers.byId(abierta.cajaId) ?? null;
    return this.registers.all()[0] ?? null;
  });

  readonly sesionActiva = computed<CashSession | null>(() => {
    const caja = this.cajaActual();
    if (!caja) return null;
    return this.sessions.activeFor(caja.id) ?? null;
  });

  readonly hayCajaAbierta = computed(() => this.sesionActiva() !== null);

  /** Movimientos de la sesión activa, del más reciente al más antiguo. */
  readonly movimientosSesion = computed<CashMovement[]>(() => {
    const sesion = this.sesionActiva();
    // Leemos la señal completa para que el computed reaccione a cada alta.
    const todos = this.movements.all();
    if (!sesion) return [];
    return todos
      .filter((m) => m.sesionCajaId === sesion.id)
      .sort((a, b) => b.fecha.getTime() - a.fecha.getTime());
  });

  readonly totalEntradas = computed(() => totalEntradas(this.movimientosSesion()));
  readonly totalSalidas = computed(() => totalSalidas(this.movimientosSesion()));

  /** Saldo esperado derivado: saldo inicial + entradas − salidas (spec §4). */
  readonly saldoEsperado = computed(() => {
    const sesion = this.sesionActiva();
    if (!sesion) return 0;
    return expectedBalance(sesion.saldoInicial, this.movimientosSesion());
  });

  readonly ultimaEntrada = computed(() =>
    this.movimientosSesion().find((m) => m.tipo === CashMovementType.ENTRADA) ?? null,
  );

  readonly ultimaSalida = computed(() =>
    this.movimientosSesion().find((m) => m.tipo === CashMovementType.SALIDA) ?? null,
  );

  readonly conteoArqueo = this.arqueoEnCurso.asReadonly();

  /** Diferencia del arqueo en curso: contado − esperado (spec §8). */
  readonly diferenciaArqueo = computed(() => {
    const conteo = this.arqueoEnCurso();
    if (!conteo) return null;
    return conteo.total - this.saldoEsperado();
  });

  readonly estadoDiferenciaArqueo = computed(() => {
    const diff = this.diferenciaArqueo();
    return diff === null ? null : differenceStatus(diff);
  });

  seleccionarCaja(id: string): void {
    this.cajaSeleccionadaId.set(id);
    this.arqueoEnCurso.set(null);
  }

  /** Guarda el conteo del arqueo en memoria para reutilizarlo en el cierre (spec §9). */
  registrarArqueo(items: readonly CashCountItem[]): CashCount {
    const sesion = this.sesionActiva();
    assertCanRegisterMovement(sesion);
    const conteo = buildCashCount(items);
    this.arqueoEnCurso.set(conteo);
    const diferencia = conteo.total - this.saldoEsperado();
    this.audit.log(AuditAction.ARQUEO_REALIZADO, {
      cajaId: sesion.cajaId,
      sesionCajaId: sesion.id,
      descripcion: `Arqueo: esperado ${formatCLP(this.saldoEsperado())}, contado ${formatCLP(conteo.total)}, diferencia ${formatCLP(diferencia)}.`,
    });
    return conteo;
  }

  limpiarArqueo(): void {
    this.arqueoEnCurso.set(null);
  }

  /** Apertura de caja: crea la sesión, guarda el conteo inicial y audita (spec §2). */
  abrirCaja(cajaId: string, items: readonly CashCountItem[], responsableId = MOCK_USER.id): CashSession {
    const caja = this.registers.requireById(cajaId);
    assertCanOpen(caja, this.sessions.all());

    const conteoInicial = buildCashCount(items);
    const sesion = this.sessions.open({ cajaId, responsableId, conteoInicial });
    this.registers.markOpened(cajaId, conteoInicial.total);
    this.audit.log(AuditAction.CAJA_ABIERTA, {
      cajaId,
      sesionCajaId: sesion.id,
      descripcion: `Apertura con saldo inicial ${formatCLP(conteoInicial.total)}.`,
      usuarioId: responsableId,
    });
    this.cajaSeleccionadaId.set(cajaId);
    this.arqueoEnCurso.set(null);
    return sesion;
  }

  /** Registra una entrada: aumenta el saldo esperado (spec §5). */
  registrarEntrada(data: {
    concepto: string;
    monto: unknown;
    observacion?: string;
    fecha?: Date;
    responsableId?: string;
  }): CashMovement {
    return this.registrarMovimiento(CashMovementType.ENTRADA, data);
  }

  /** Registra una salida: valida que no deje el saldo esperado negativo (spec §6). */
  registrarSalida(data: {
    concepto: string;
    monto: unknown;
    observacion?: string;
    fecha?: Date;
    responsableId?: string;
  }): CashMovement {
    return this.registrarMovimiento(CashMovementType.SALIDA, data);
  }

  /** Cierre de caja: exige conteo final, guarda el snapshot y bloquea la sesión (spec §9). */
  cerrarCaja(data: { items?: readonly CashCountItem[]; observacionCierre?: string }): CashSession {
    const sesion = this.sesionActiva();
    assertCanRegisterMovement(sesion);

    const conteoFinal = data.items ? buildCashCount(data.items) : this.arqueoEnCurso();
    assertCanClose(sesion, conteoFinal);

    const saldoEsperado = this.saldoEsperado();
    const cerrada = this.sessions.close(sesion.id, {
      saldoEsperado,
      conteoFinal: conteoFinal!,
      observacionCierre: data.observacionCierre,
    });
    this.registers.markClosed(sesion.cajaId, cerrada.saldoContado ?? 0);
    this.audit.log(AuditAction.CAJA_CERRADA, {
      cajaId: sesion.cajaId,
      sesionCajaId: sesion.id,
      descripcion:
        `Cierre: esperado ${formatCLP(saldoEsperado)}, contado ${formatCLP(cerrada.saldoContado ?? 0)}, ` +
        `diferencia ${formatCLP(cerrada.diferencia ?? 0)}.` +
        (cerrada.observacionCierre ? ` Observación: ${cerrada.observacionCierre}` : ''),
    });
    this.arqueoEnCurso.set(null);
    return cerrada;
  }

  /** Detalle de una sesión histórica: sus movimientos en orden cronológico (spec §11). */
  movimientosDe(sesionCajaId: string): CashMovement[] {
    return this.movements
      .all()
      .filter((m) => m.sesionCajaId === sesionCajaId)
      .sort((a, b) => a.fecha.getTime() - b.fecha.getTime());
  }

  cajaNombre(cajaId: string): string {
    return this.registers.byId(cajaId)?.nombre ?? 'Caja eliminada';
  }

  private registrarMovimiento(
    tipo: CashMovementType,
    data: {
      concepto: string;
      monto: unknown;
      observacion?: string;
      fecha?: Date;
      responsableId?: string;
    },
  ): CashMovement {
    const sesion = this.sesionActiva();
    assertCanRegisterMovement(sesion);

    const concepto = assertConceptoValido(data.concepto);
    const monto = assertMontoValido(data.monto);
    if (tipo === CashMovementType.SALIDA) {
      assertSalidaPermitida(monto, this.saldoEsperado());
    }

    const responsableId = data.responsableId ?? MOCK_USER.id;
    const movimiento = this.movements.register({
      cajaId: sesion.cajaId,
      sesionCajaId: sesion.id,
      tipo,
      concepto,
      monto,
      observacion: data.observacion,
      fecha: data.fecha,
      responsableId,
    });

    // Mantiene sincronizados los snapshots que se muestran en listados.
    const nuevoSaldo = this.saldoEsperado();
    this.sessions.syncSaldoEsperado(sesion.id, nuevoSaldo);
    this.registers.updateSaldoActual(sesion.cajaId, nuevoSaldo);

    this.audit.log(
      tipo === CashMovementType.ENTRADA
        ? AuditAction.ENTRADA_REGISTRADA
        : AuditAction.SALIDA_REGISTRADA,
      {
        cajaId: sesion.cajaId,
        sesionCajaId: sesion.id,
        descripcion: `${tipo} ${formatCLP(monto)} — ${concepto}.`,
        usuarioId: responsableId,
      },
    );
    return movimiento;
  }
}
