import { Injectable, inject } from '@angular/core';
import { CashRegister, CashRegisterStatus, CashRegisterType, CashSessionStatus } from '../models';
import { CashAuditService } from '../services/cash-audit.service';
import { CashMovementService } from '../services/cash-movement.service';
import { CashRegisterService } from '../services/cash-register.service';
import { CashSessionService } from '../services/cash-session.service';
import { CashStorageService } from '../storage/cash-storage.service';
import { STORAGE_KEYS } from '../storage/storage-keys';
import { MOCK_BRANCH, MOCK_USER } from './mock-data';
import { sampleCajaData } from './sample';
import { SeedService } from './seed.service';

/**
 * Carga o borra un escenario de ejemplo para el módulo de Caja, análogo al
 * "Cargar ejemplo" de Flujo de Caja: reemplaza sesiones, movimientos y
 * auditoría por datos deterministas para poder ver el módulo poblado.
 */
@Injectable({ providedIn: 'root' })
export class SampleService {
  private readonly storage = inject(CashStorageService);
  private readonly seed = inject(SeedService);
  private readonly registers = inject(CashRegisterService);
  private readonly sessions = inject(CashSessionService);
  private readonly movements = inject(CashMovementService);
  private readonly audits = inject(CashAuditService);

  loadSample(): void {
    let caja = this.registers.all()[0];
    if (!caja) {
      caja = this.registers.create({
        nombre: 'Caja Sucursal Principal',
        tipo: CashRegisterType.SUCURSAL,
        sucursalId: MOCK_BRANCH.id,
        responsableId: MOCK_USER.id,
      });
    }

    const { sessions, movements, audits } = sampleCajaData(caja.id, MOCK_USER.id);
    this.storage.write(STORAGE_KEYS.sessions, sessions);
    this.storage.write(STORAGE_KEYS.movements, movements);
    this.storage.write(STORAGE_KEYS.audits, audits);

    const abierta = sessions.find((s) => s.estado === CashSessionStatus.ABIERTA)!;
    const actualizada: CashRegister = {
      ...caja,
      estado: CashRegisterStatus.ABIERTA,
      saldoInicial: abierta.saldoInicial,
      saldoActual: abierta.saldoEsperado,
      fechaActualizacion: new Date(),
    };
    this.storage.upsert(STORAGE_KEYS.registers, actualizada);

    this.reloadAll();
  }

  /** Borra todos los datos del módulo y vuelve a la caja mock inicial, cerrada. */
  resetAll(): void {
    this.storage.clearAll([
      STORAGE_KEYS.registers,
      STORAGE_KEYS.sessions,
      STORAGE_KEYS.movements,
      STORAGE_KEYS.audits,
    ]);
    this.seed.seed();
    this.reloadAll();
  }

  private reloadAll(): void {
    this.registers.reload();
    this.sessions.reload();
    this.movements.reload();
    this.audits.reload();
  }
}
