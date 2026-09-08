import { Injectable, computed, inject, signal } from '@angular/core';
import { AuditAction, CashAudit } from '../models';
import { MOCK_USER } from '../seed/mock-data';
import { CashStorageService } from '../storage/cash-storage.service';
import { STORAGE_KEYS } from '../storage/storage-keys';
import { newId } from '../utils/id';

/** Bitácora de acciones importantes. Solo agrega: nunca elimina registros (spec §15). */
@Injectable({ providedIn: 'root' })
export class CashAuditService {
  private readonly storage = inject(CashStorageService);
  private readonly audits = signal<CashAudit[]>(this.storage.read<CashAudit>(STORAGE_KEYS.audits));

  /** Auditoría completa, de la más reciente a la más antigua. */
  readonly all = computed(() =>
    [...this.audits()].sort((a, b) => b.fecha.getTime() - a.fecha.getTime()),
  );

  /** Registros de una sesión concreta, en orden cronológico. */
  bySession(sesionCajaId: string): CashAudit[] {
    return this.audits()
      .filter((a) => a.sesionCajaId === sesionCajaId)
      .sort((a, b) => a.fecha.getTime() - b.fecha.getTime());
  }

  log(
    accion: AuditAction,
    params: { cajaId: string; sesionCajaId?: string | null; descripcion: string; usuarioId?: string },
  ): CashAudit {
    const registro: CashAudit = {
      id: newId('aud'),
      fecha: new Date(),
      usuarioId: params.usuarioId ?? MOCK_USER.id,
      accion,
      cajaId: params.cajaId,
      sesionCajaId: params.sesionCajaId ?? null,
      descripcion: params.descripcion,
    };
    this.storage.append(STORAGE_KEYS.audits, registro);
    this.audits.update((items) => [...items, registro]);
    return registro;
  }

  /** Recarga desde LocalStorage. Usado tras un reset. */
  reload(): void {
    this.audits.set(this.storage.read<CashAudit>(STORAGE_KEYS.audits));
  }
}
