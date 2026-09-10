import { Injectable, inject } from '@angular/core';
import { Observable, firstValueFrom, from } from 'rxjs';
import { ClienteService } from '../../../core/services/cliente.service';
import { TransaccionPago } from '../../../core/models/models';
import { TransaccionPagoService } from '../../../core/services/transaccion-pago.service';
import { mapPagoToSyncedIncome, SyncedIncome } from './synced-income';

const TAMANO_PAGINA = 200;

@Injectable({ providedIn: 'root' })
export class CobrosSyncService {
  private readonly transaccionPagoService = inject(TransaccionPagoService);
  private readonly clienteService = inject(ClienteService);

  sincronizar(): Observable<SyncedIncome[]> {
    return from(this.cargarTodo());
  }

  private async cargarTodo(): Promise<SyncedIncome[]> {
    const [pagos, clientes] = await Promise.all([
      this.traerTodosLosConfirmados(),
      firstValueFrom(this.clienteService.listar()),
    ]);
    const nombrePorId = new Map(clientes.map((c) => [c.id, c.nombre]));
    return pagos.map((pago) =>
      mapPagoToSyncedIncome(pago, nombrePorId.get(pago.clienteId) ?? `Cliente #${pago.clienteId}`),
    );
  }

  /** Pagina hasta traer todos los pagos confirmados: `projection.ts` necesita el conjunto completo, no una página. */
  private async traerTodosLosConfirmados(): Promise<TransaccionPago[]> {
    const acumulado: TransaccionPago[] = [];
    let pagina = 0;
    for (;;) {
      const resp = await firstValueFrom(
        this.transaccionPagoService.buscar({ estado: 'CONFIRMADA', pagina, tamano: TAMANO_PAGINA }),
      );
      acumulado.push(...resp.contenido);
      if (acumulado.length >= resp.total || resp.contenido.length === 0) break;
      pagina++;
    }
    return acumulado;
  }
}
