import { MonthKey } from './models';
import { TransaccionPago } from '../../../core/models/models';

/** Un cobro confirmado en Tesorería, representado como ingreso del mes en el Flujo de Caja. */
export interface SyncedIncome {
  id: string;
  month: MonthKey;
  description: string;
  amount: number;
  cuentaPorCobrarId: number;
}

export function mapPagoToSyncedIncome(pago: TransaccionPago, nombreCliente: string): SyncedIncome {
  return {
    id: `pago-${pago.id}`,
    month: pago.fecha.slice(0, 7),
    description: `Cobro ${nombreCliente} — Venta V-${pago.ventaId}`,
    amount: pago.monto,
    cuentaPorCobrarId: pago.cuentaPorCobrarId,
  };
}
