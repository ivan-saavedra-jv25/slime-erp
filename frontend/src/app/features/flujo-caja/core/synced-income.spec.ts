import { TransaccionPago } from '../../../core/models/models';
import { mapPagoToSyncedIncome } from './synced-income';

function pagoDeEjemplo(overrides: Partial<TransaccionPago> = {}): TransaccionPago {
  return {
    id: 42,
    cuentaPorCobrarId: 7,
    ventaId: 26,
    clienteId: 5,
    fecha: '2026-09-10T19:37:00',
    monto: 70000,
    medioPago: 'TRANSFERENCIA',
    estado: 'CONFIRMADA',
    usuarioId: 1,
    observaciones: null,
    transferenciaBancoOrigen: null,
    transferenciaBancoDestino: null,
    transferenciaNumeroOperacion: null,
    transferenciaFecha: null,
    tarjetaEntidad: null,
    tarjetaTipo: null,
    tarjetaNumeroOperacion: null,
    tarjetaFecha: null,
    chequeBanco: null,
    chequeNumero: null,
    chequeFechaEmision: null,
    chequeFechaPago: null,
    motivoAnulacion: null,
    ...overrides,
  };
}

describe('mapPagoToSyncedIncome', () => {
  it('arma el id con el prefijo pago-', () => {
    const resultado = mapPagoToSyncedIncome(pagoDeEjemplo({ id: 42 }), 'Abogado Pablo Fernández');
    expect(resultado.id).toBe('pago-42');
  });

  it('toma el mes desde los primeros 7 caracteres de la fecha', () => {
    const resultado = mapPagoToSyncedIncome(pagoDeEjemplo({ fecha: '2026-03-15T10:00:00' }), 'Cliente X');
    expect(resultado.month).toBe('2026-03');
  });

  it('arma la descripción con el nombre del cliente y el número de venta', () => {
    const resultado = mapPagoToSyncedIncome(pagoDeEjemplo({ ventaId: 26 }), 'Abogado Pablo Fernández');
    expect(resultado.description).toBe('Cobro Abogado Pablo Fernández — Venta V-26');
  });

  it('copia el monto y la cuenta por cobrar tal cual', () => {
    const resultado = mapPagoToSyncedIncome(pagoDeEjemplo({ monto: 70000, cuentaPorCobrarId: 7 }), 'Cliente X');
    expect(resultado.amount).toBe(70000);
    expect(resultado.cuentaPorCobrarId).toBe(7);
  });
});
