/** Tipos de caja soportados (spec §1). */
export enum CashRegisterType {
  SUCURSAL = 'SUCURSAL',
  VENDEDOR = 'VENDEDOR',
  POS = 'POS',
}

/** Estado de una caja (spec §1). */
export enum CashRegisterStatus {
  ABIERTA = 'ABIERTA',
  CERRADA = 'CERRADA',
}

/** Estado de una sesión de caja (spec §3). */
export enum CashSessionStatus {
  ABIERTA = 'ABIERTA',
  CERRADA = 'CERRADA',
}

/** Tipo de movimiento de efectivo (spec §13). */
export enum CashMovementType {
  ENTRADA = 'ENTRADA',
  SALIDA = 'SALIDA',
}

/**
 * Origen del movimiento (spec §20).
 * En esta versión solo se emite MANUAL; el resto queda declarado para que el
 * módulo pueda recibir movimientos del ERP sin cambiar el modelo.
 */
export enum CashMovementOrigin {
  MANUAL = 'MANUAL',
  VENTA = 'VENTA',
  COMPRA = 'COMPRA',
  PAGO = 'PAGO',
  COBRO = 'COBRO',
  TRANSFERENCIA = 'TRANSFERENCIA',
  RENDICION = 'RENDICION',
}

/** Acciones registradas en auditoría (spec §15). */
export enum AuditAction {
  CAJA_CREADA = 'CAJA_CREADA',
  CAJA_ABIERTA = 'CAJA_ABIERTA',
  ENTRADA_REGISTRADA = 'ENTRADA_REGISTRADA',
  SALIDA_REGISTRADA = 'SALIDA_REGISTRADA',
  ARQUEO_REALIZADO = 'ARQUEO_REALIZADO',
  CAJA_CERRADA = 'CAJA_CERRADA',
}

/** Resultado de comparar saldo esperado contra efectivo contado (spec §8). */
export enum CashDifferenceStatus {
  CUADRADA = 'CUADRADA',
  SOBRANTE = 'SOBRANTE',
  FALTANTE = 'FALTANTE',
}
