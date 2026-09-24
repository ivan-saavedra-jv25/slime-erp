import {
  EstadoNotaCredito,
  RecuperacionInventario,
  TipoCorreccion,
  TipoMovimientoNotaCredito,
} from '../../core/models/models';

export const ETIQUETAS_ESTADO: Record<EstadoNotaCredito, string> = {
  BORRADOR: 'Borrador',
  EMITIDA: 'Emitida',
  ANULADA: 'Anulada',
};

// Las clases tag--* son globales (styles/_components.scss).
export const TAGS_ESTADO: Record<EstadoNotaCredito, string> = {
  BORRADOR: 'tag--warning',
  EMITIDA: 'tag--success',
  ANULADA: 'tag--error',
};

export const ESTADOS: EstadoNotaCredito[] = ['BORRADOR', 'EMITIDA', 'ANULADA'];

export const ETIQUETAS_TIPO_CORRECCION: Record<TipoCorreccion, string> = {
  CORRIGE_DOCUMENTO: 'Corrige documento',
  CORRIGE_MONTO: 'Corrige monto',
  CORRIGE_TEXTO: 'Corrige texto',
};

// Se muestra bajo cada opción del formulario: el tipo determina el
// comportamiento funcional de la nota, así que conviene explicarlo al elegirlo.
export const AYUDA_TIPO_CORRECCION: Record<TipoCorreccion, string> = {
  CORRIGE_DOCUMENTO: 'Anula o reemplaza el documento completo. Se cargan todas sus líneas.',
  CORRIGE_MONTO: 'Corrige total o parcialmente los valores. Elija las líneas a corregir.',
  CORRIGE_TEXTO: 'Corrige información textual. No afecta montos ni inventario.',
};

export const TIPOS_CORRECCION: TipoCorreccion[] = [
  'CORRIGE_DOCUMENTO',
  'CORRIGE_MONTO',
  'CORRIGE_TEXTO',
];

export const ETIQUETAS_RECUPERACION: Record<RecuperacionInventario, string> = {
  NO: 'No',
  PARCIAL: 'Parcial',
  TOTAL: 'Sí',
};

export const TAGS_RECUPERACION: Record<RecuperacionInventario, string> = {
  NO: '',
  PARCIAL: 'tag--warning',
  TOTAL: 'tag--success',
};

export const ETIQUETAS_MOVIMIENTO: Record<TipoMovimientoNotaCredito, string> = {
  RECUPERACION: 'Recuperación',
  REVERSA_ANULACION: 'Reversa por anulación',
};

export const TAGS_MOVIMIENTO: Record<TipoMovimientoNotaCredito, string> = {
  RECUPERACION: 'tag--success',
  REVERSA_ANULACION: 'tag--error',
};
