/** Conceptos disponibles al registrar una entrada de dinero (spec §5). */
export const CONCEPTOS_ENTRADA: readonly string[] = [
  'Ingreso de efectivo',
  'Devolución',
  'Rendición',
  'Transferencia recibida',
  'Ajuste positivo',
  'Otro',
];

/** Conceptos disponibles al registrar una salida de dinero (spec §6). */
export const CONCEPTOS_SALIDA: readonly string[] = [
  'Pago a proveedor',
  'Compra',
  'Gasto',
  'Entrega de efectivo',
  'Transferencia enviada',
  'Retiro',
  'Ajuste negativo',
  'Otro',
];
