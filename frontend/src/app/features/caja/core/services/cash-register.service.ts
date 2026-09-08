import { Injectable, computed, inject, signal } from '@angular/core';
import { AuditAction, CashRegister, CashRegisterStatus, CashRegisterType } from '../models';
import { MOCK_BRANCH, MOCK_USER } from '../seed/mock-data';
import { CashStorageService } from '../storage/cash-storage.service';
import { STORAGE_KEYS } from '../storage/storage-keys';
import { newId } from '../utils/id';
import { BusinessRuleError } from './business-rule-error';
import { CashAuditService } from './cash-audit.service';

/** CRUD de cajas y control de su estado ABIERTA/CERRADA (spec §1). */
@Injectable({ providedIn: 'root' })
export class CashRegisterService {
  private readonly storage = inject(CashStorageService);
  private readonly audit = inject(CashAuditService);

  private readonly registers = signal<CashRegister[]>(
    this.storage.read<CashRegister>(STORAGE_KEYS.registers),
  );

  readonly all = computed(() =>
    [...this.registers()].sort((a, b) => a.nombre.localeCompare(b.nombre)),
  );

  readonly abiertas = computed(() =>
    this.registers().filter((c) => c.estado === CashRegisterStatus.ABIERTA),
  );

  byId(id: string): CashRegister | undefined {
    return this.registers().find((c) => c.id === id);
  }

  /** Igual que byId pero lanza si no existe: evita chequeos repetidos en los servicios. */
  requireById(id: string): CashRegister {
    const caja = this.byId(id);
    if (!caja) {
      throw new BusinessRuleError('CAJA_NO_ENCONTRADA', `No existe la caja ${id}.`);
    }
    return caja;
  }

  create(data: {
    nombre: string;
    tipo: CashRegisterType;
    sucursalId?: string;
    responsableId?: string;
  }): CashRegister {
    const ahora = new Date();
    const caja: CashRegister = {
      id: newId('caja'),
      nombre: data.nombre.trim(),
      tipo: data.tipo,
      sucursalId: data.sucursalId ?? MOCK_BRANCH.id,
      responsableId: data.responsableId ?? MOCK_USER.id,
      estado: CashRegisterStatus.CERRADA,
      saldoInicial: 0,
      saldoActual: 0,
      fechaCreacion: ahora,
      fechaActualizacion: ahora,
    };
    this.persist(caja);
    this.audit.log(AuditAction.CAJA_CREADA, {
      cajaId: caja.id,
      descripcion: `Caja "${caja.nombre}" creada (${caja.tipo}).`,
    });
    return caja;
  }

  update(id: string, cambios: Partial<Pick<CashRegister, 'nombre' | 'tipo' | 'responsableId' | 'sucursalId'>>): CashRegister {
    const caja = this.requireById(id);
    const actualizada: CashRegister = { ...caja, ...cambios, fechaActualizacion: new Date() };
    this.persist(actualizada);
    return actualizada;
  }

  /** Marca la caja como abierta y fija su saldo inicial (llamado por CashSessionService). */
  markOpened(id: string, saldoInicial: number): CashRegister {
    const caja = this.requireById(id);
    const actualizada: CashRegister = {
      ...caja,
      estado: CashRegisterStatus.ABIERTA,
      saldoInicial,
      saldoActual: saldoInicial,
      fechaActualizacion: new Date(),
    };
    this.persist(actualizada);
    return actualizada;
  }

  /** Mantiene el snapshot de saldo esperado al día tras cada movimiento. */
  updateSaldoActual(id: string, saldoActual: number): void {
    const caja = this.byId(id);
    if (!caja) return;
    this.persist({ ...caja, saldoActual, fechaActualizacion: new Date() });
  }

  markClosed(id: string, saldoContado: number): CashRegister {
    const caja = this.requireById(id);
    const actualizada: CashRegister = {
      ...caja,
      estado: CashRegisterStatus.CERRADA,
      saldoActual: saldoContado,
      fechaActualizacion: new Date(),
    };
    this.persist(actualizada);
    return actualizada;
  }

  reload(): void {
    this.registers.set(this.storage.read<CashRegister>(STORAGE_KEYS.registers));
  }

  private persist(caja: CashRegister): void {
    this.storage.upsert(STORAGE_KEYS.registers, caja);
    this.registers.update((items) => {
      const index = items.findIndex((c) => c.id === caja.id);
      if (index < 0) return [...items, caja];
      const copy = [...items];
      copy[index] = caja;
      return copy;
    });
  }
}
