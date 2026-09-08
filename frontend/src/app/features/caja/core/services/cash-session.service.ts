import { Injectable, computed, inject, signal } from '@angular/core';
import { CashCount, CashSession, CashSessionStatus } from '../models';
import { CashStorageService } from '../storage/cash-storage.service';
import { STORAGE_KEYS } from '../storage/storage-keys';
import { newId } from '../utils/id';
import { difference } from '../utils/money';
import { BusinessRuleError } from './business-rule-error';
import { assertMutable } from './cash-rules';

/** Ciclo de vida de las sesiones de caja (spec §3). */
@Injectable({ providedIn: 'root' })
export class CashSessionService {
  private readonly storage = inject(CashStorageService);
  private readonly sessions = signal<CashSession[]>(
    this.storage.read<CashSession>(STORAGE_KEYS.sessions),
  );

  readonly all = computed(() => this.sessions());

  /** Historial: sesiones cerradas y abiertas, de la más reciente a la más antigua (spec §11). */
  readonly historial = computed(() =>
    [...this.sessions()].sort((a, b) => b.fechaApertura.getTime() - a.fechaApertura.getTime()),
  );

  byId(id: string): CashSession | undefined {
    return this.sessions().find((s) => s.id === id);
  }

  requireById(id: string): CashSession {
    const sesion = this.byId(id);
    if (!sesion) {
      throw new BusinessRuleError('SESION_NO_ENCONTRADA', `No existe la sesión ${id}.`);
    }
    return sesion;
  }

  /** Sesión abierta de una caja, si la hay. */
  activeFor(cajaId: string): CashSession | undefined {
    return this.sessions().find(
      (s) => s.cajaId === cajaId && s.estado === CashSessionStatus.ABIERTA,
    );
  }

  /** Cualquier sesión abierta del sistema (esta versión opera de a una caja a la vez). */
  readonly active = computed(() =>
    this.sessions().find((s) => s.estado === CashSessionStatus.ABIERTA),
  );

  bySession(cajaId: string): CashSession[] {
    return this.sessions().filter((s) => s.cajaId === cajaId);
  }

  /** Abre la sesión. La validación de "caja ya abierta" ocurre en CajaFacade. */
  open(data: { cajaId: string; responsableId: string; conteoInicial: CashCount }): CashSession {
    const saldoInicial = data.conteoInicial.total;
    const sesion: CashSession = {
      id: newId('ses'),
      cajaId: data.cajaId,
      responsableId: data.responsableId,
      fechaApertura: new Date(),
      fechaCierre: null,
      saldoInicial,
      saldoEsperado: saldoInicial,
      saldoContado: null,
      diferencia: null,
      estado: CashSessionStatus.ABIERTA,
      conteoInicial: data.conteoInicial,
      conteoFinal: null,
    };
    this.persist(sesion);
    return sesion;
  }

  /**
   * Cierra la sesión guardando el snapshot final (spec §9).
   * La diferencia se conserva siempre, incluso si es 0 (spec §16.8).
   */
  close(
    id: string,
    data: { saldoEsperado: number; conteoFinal: CashCount; observacionCierre?: string },
  ): CashSession {
    const sesion = this.requireById(id);
    assertMutable(sesion);
    const saldoContado = data.conteoFinal.total;
    const cerrada: CashSession = {
      ...sesion,
      fechaCierre: new Date(),
      saldoEsperado: data.saldoEsperado,
      saldoContado,
      diferencia: difference(data.saldoEsperado, saldoContado),
      estado: CashSessionStatus.CERRADA,
      conteoFinal: data.conteoFinal,
      observacionCierre: data.observacionCierre?.trim() || undefined,
    };
    this.persist(cerrada);
    return cerrada;
  }

  /** Guarda el snapshot de saldo esperado mientras la sesión sigue abierta. */
  syncSaldoEsperado(id: string, saldoEsperado: number): void {
    const sesion = this.byId(id);
    if (!sesion || sesion.estado === CashSessionStatus.CERRADA) return;
    this.persist({ ...sesion, saldoEsperado });
  }

  reload(): void {
    this.sessions.set(this.storage.read<CashSession>(STORAGE_KEYS.sessions));
  }

  private persist(sesion: CashSession): void {
    this.storage.upsert(STORAGE_KEYS.sessions, sesion);
    this.sessions.update((items) => {
      const index = items.findIndex((s) => s.id === sesion.id);
      if (index < 0) return [...items, sesion];
      const copy = [...items];
      copy[index] = sesion;
      return copy;
    });
  }
}
