import { Injectable, computed, inject, signal } from '@angular/core';
import { CashMovement, CashMovementOrigin, CashMovementType } from '../models';
import { CashStorageService } from '../storage/cash-storage.service';
import { STORAGE_KEYS } from '../storage/storage-keys';
import { newId } from '../utils/id';

/** Filtros de la pantalla Movimientos (spec §7). */
export interface MovementFilter {
  tipo?: CashMovementType | null;
  concepto?: string | null;
  desde?: Date | null;
  hasta?: Date | null;
}

/** Persistencia y consulta de entradas y salidas de efectivo (spec §5, §6, §7). */
@Injectable({ providedIn: 'root' })
export class CashMovementService {
  private readonly storage = inject(CashStorageService);
  private readonly movements = signal<CashMovement[]>(
    this.storage.read<CashMovement>(STORAGE_KEYS.movements),
  );

  readonly all = computed(() => this.movements());

  /** Movimientos de una sesión, del más reciente al más antiguo. */
  bySession(sesionCajaId: string): CashMovement[] {
    return this.movements()
      .filter((m) => m.sesionCajaId === sesionCajaId)
      .sort((a, b) => b.fecha.getTime() - a.fecha.getTime());
  }

  /**
   * Crea el movimiento. Las validaciones de negocio (sesión abierta, saldo suficiente,
   * monto entero) las aplica CajaFacade antes de llamar aquí.
   */
  register(data: {
    cajaId: string;
    sesionCajaId: string;
    tipo: CashMovementType;
    concepto: string;
    monto: number;
    observacion?: string;
    fecha?: Date;
    responsableId: string;
  }): CashMovement {
    const movimiento: CashMovement = {
      id: newId('mov'),
      cajaId: data.cajaId,
      sesionCajaId: data.sesionCajaId,
      tipo: data.tipo,
      // Spec §20: solo MANUAL en esta versión; el campo existe para futuras integraciones.
      origen: CashMovementOrigin.MANUAL,
      concepto: data.concepto,
      monto: data.monto,
      observacion: data.observacion?.trim() ?? '',
      fecha: data.fecha ?? new Date(),
      responsableId: data.responsableId,
    };
    this.storage.append(STORAGE_KEYS.movements, movimiento);
    this.movements.update((items) => [...items, movimiento]);
    return movimiento;
  }

  /** Aplica los filtros de la pantalla Movimientos sobre una lista ya acotada a la sesión. */
  filter(movimientos: readonly CashMovement[], filtro: MovementFilter): CashMovement[] {
    return movimientos.filter((m) => {
      if (filtro.tipo && m.tipo !== filtro.tipo) return false;
      if (filtro.concepto && m.concepto !== filtro.concepto) return false;
      if (filtro.desde && m.fecha < startOfDay(filtro.desde)) return false;
      if (filtro.hasta && m.fecha > endOfDay(filtro.hasta)) return false;
      return true;
    });
  }

  reload(): void {
    this.movements.set(this.storage.read<CashMovement>(STORAGE_KEYS.movements));
  }
}

function startOfDay(date: Date): Date {
  const copy = new Date(date);
  copy.setHours(0, 0, 0, 0);
  return copy;
}

function endOfDay(date: Date): Date {
  const copy = new Date(date);
  copy.setHours(23, 59, 59, 999);
  return copy;
}
