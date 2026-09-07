import { Pipe, PipeTransform } from '@angular/core';

const FORMATO_CLP = new Intl.NumberFormat('es-CL', {
  style: 'currency',
  currency: 'CLP',
  maximumFractionDigits: 0,
});

export function formatearCLP(valor: number | null | undefined): string {
  if (valor == null || isNaN(valor)) return '$0';
  return FORMATO_CLP.format(valor);
}

@Pipe({ name: 'moneda', standalone: true })
export class MonedaPipe implements PipeTransform {
  transform(valor: number | null | undefined): string {
    return formatearCLP(valor);
  }
}
