import {
  EstadoNotaDebito,
  ImpactoInventario,
  TipoMovimientoNotaDebito,
  TipoReversion,
} from '../../core/models/models';

export const ETIQUETAS_ESTADO: Record<EstadoNotaDebito, string> = {
  BORRADOR: 'Borrador',
  EMITIDA: 'Emitida',
  ANULADA: 'Anulada',
};

// Las clases tag--* son globales (styles/_components.scss).
export const TAGS_ESTADO: Record<EstadoNotaDebito, string> = {
  BORRADOR: 'tag--warning',
  EMITIDA: 'tag--success',
  ANULADA: 'tag--error',
};

export const ESTADOS: EstadoNotaDebito[] = ['BORRADOR', 'EMITIDA', 'ANULADA'];

export const ETIQUETAS_TIPO_REVERSION: Record<TipoReversion, string> = {
  REVIERTE_DOCUMENTO: 'Revierte documento',
  REVIERTE_MONTO: 'Revierte monto',
  REVIERTE_TEXTO: 'Revierte texto',
};

// Se muestra bajo cada opción del formulario: el tipo determina el
// comportamiento funcional de la nota, así que conviene explicarlo al elegirlo.
export const AYUDA_TIPO_REVERSION: Record<TipoReversion, string> = {
  REVIERTE_DOCUMENTO: 'Anula o reemplaza la nota de crédito completa. Se cargan todas sus líneas.',
  REVIERTE_MONTO: 'Revierte total o parcialmente los montos de la nota de crédito. Elija las líneas a revertir.',
  REVIERTE_TEXTO: 'Revierte información textual. No afecta montos ni inventario.',
};

export const TIPOS_REVERSION: TipoReversion[] = ['REVIERTE_DOCUMENTO', 'REVIERTE_MONTO', 'REVIERTE_TEXTO'];

export const ETIQUETAS_IMPACTO: Record<ImpactoInventario, string> = {
  NO: 'No',
  PARCIAL: 'Parcial',
  TOTAL: 'Sí',
};

export const TAGS_IMPACTO: Record<ImpactoInventario, string> = {
  NO: '',
  PARCIAL: 'tag--warning',
  TOTAL: 'tag--success',
};

export const ETIQUETAS_MOVIMIENTO: Record<TipoMovimientoNotaDebito, string> = {
  REVERSION: 'Reversión',
  REVERSA_ANULACION: 'Reversa por anulación',
};

export const TAGS_MOVIMIENTO: Record<TipoMovimientoNotaDebito, string> = {
  REVERSION: 'tag--error',
  REVERSA_ANULACION: 'tag--success',
};