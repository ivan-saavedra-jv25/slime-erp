import { Injectable, inject } from '@angular/core';
import { CashRegister, CashRegisterType } from '../models';
import { CashRegisterService } from '../services/cash-register.service';
import { CashStorageService } from '../storage/cash-storage.service';
import { STORAGE_KEYS } from '../storage/storage-keys';
import { MOCK_BRANCH, MOCK_USER } from './mock-data';

/**
 * Crea los datos mock la primera vez que arranca la aplicación (spec §19).
 * Idempotente: si ya hay cajas guardadas, no hace nada.
 */
@Injectable({ providedIn: 'root' })
export class SeedService {
  private readonly storage = inject(CashStorageService);
  private readonly registers = inject(CashRegisterService);

  seed(): void {
    const existentes = this.storage.read<CashRegister>(STORAGE_KEYS.registers);
    if (existentes.length > 0) return;

    this.registers.create({
      nombre: 'Caja Sucursal Principal',
      tipo: CashRegisterType.SUCURSAL,
      sucursalId: MOCK_BRANCH.id,
      responsableId: MOCK_USER.id,
    });
  }
}
