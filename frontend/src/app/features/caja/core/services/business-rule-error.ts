/** Códigos de las reglas de negocio del spec §16. */
export type BusinessRuleCode =
  | 'CAJA_YA_ABIERTA'
  | 'SESION_NO_ABIERTA'
  | 'SIN_CONTEO_FINAL'
  | 'SESION_INMUTABLE'
  | 'SALDO_INSUFICIENTE'
  | 'MONTO_INVALIDO'
  | 'CANTIDAD_INVALIDA'
  | 'CONCEPTO_REQUERIDO'
  | 'CAJA_NO_ENCONTRADA'
  | 'SESION_NO_ENCONTRADA';

/** Error de dominio con código, para que la UI muestre un mensaje claro. */
export class BusinessRuleError extends Error {
  constructor(
    readonly code: BusinessRuleCode,
    message: string,
  ) {
    super(message);
    this.name = 'BusinessRuleError';
  }
}
