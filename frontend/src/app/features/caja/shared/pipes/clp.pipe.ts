import { Pipe, PipeTransform } from '@angular/core';
import { formatCLP } from '../../core/utils/money';

/** Formatea enteros CLP como "$156.630". Uso: {{ monto | clp }} */
@Pipe({ name: 'clp', standalone: true })
export class ClpPipe implements PipeTransform {
  transform(value: number | null | undefined): string {
    return formatCLP(value ?? 0);
  }
}
