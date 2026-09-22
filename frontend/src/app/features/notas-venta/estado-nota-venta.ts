import { EstadoNotaVenta, OrigenNotaVenta } from '../../core/models/models';

export const ETIQUETAS_ESTADO: Record<EstadoNotaVenta, string> = {
  BORRADOR: 'Borrador',
  CONFIRMADA: 'Confirmada',
  EN_PREPARACION: 'En preparación',
  PARCIALMENTE_ENTREGADA: 'Parcialmente entregada',
  ENTREGADA: 'Entregada',
  FACTURADA: 'Facturada',
  CANCELADA: 'Cancelada',
};

export const TAGS_ESTADO: Record<EstadoNotaVenta, string> = {
  BORRADOR: '',
  CONFIRMADA: 'tag--info',
  EN_PREPARACION: 'tag--warning',
  PARCIALMENTE_ENTREGADA: 'tag--warning',
  ENTREGADA: 'tag--success',
  FACTURADA: 'tag--success',
  CANCELADA: '',
};

export const ESTADOS: EstadoNotaVenta[] = [
  'BORRADOR',
  'CONFIRMADA',
  'EN_PREPARACION',
  'PARCIALMENTE_ENTREGADA',
  'ENTREGADA',
  'FACTURADA',
  'CANCELADA',
];

export const ETIQUETAS_ORIGEN: Record<OrigenNotaVenta, string> = {
  COTIZACION: 'Cotización',
  VENTA_DIRECTA: 'Venta directa',
};