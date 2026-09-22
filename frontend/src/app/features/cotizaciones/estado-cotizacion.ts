import { EstadoCotizacion } from '../../core/models/models';

export const ETIQUETAS_ESTADO: Record<EstadoCotizacion, string> = {
  BORRADOR: 'Borrador',
  ENVIADA: 'Enviada',
  ACEPTADA: 'Aceptada',
  RECHAZADA: 'Rechazada',
  VENCIDA: 'Vencida',
  CANCELADA: 'Cancelada',
};

export const TAGS_ESTADO: Record<EstadoCotizacion, string> = {
  BORRADOR: '',
  ENVIADA: 'tag--info',
  ACEPTADA: 'tag--success',
  RECHAZADA: 'tag--error',
  VENCIDA: 'tag--warning',
  CANCELADA: '',
};

export const ESTADOS: EstadoCotizacion[] = [
  'BORRADOR',
  'ENVIADA',
  'ACEPTADA',
  'RECHAZADA',
  'VENCIDA',
  'CANCELADA',
];
