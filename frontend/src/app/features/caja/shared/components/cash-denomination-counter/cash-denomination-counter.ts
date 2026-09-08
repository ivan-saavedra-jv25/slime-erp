import { ChangeDetectionStrategy, Component, computed, input, model, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { CashCountItem } from '../../../core/models';
import { ClpPipe } from '../../pipes/clp.pipe';
import { DENOMINATIONS, buildCountItem, countTotal, emptyCountItems } from '../../../core/utils/money';

/**
 * Conteo físico de billetes y monedas (spec §14).
 * Componente único reutilizado en Apertura, Arqueo y Cierre: la lógica no se duplica.
 *
 * La persona solo escribe la CANTIDAD de cada denominación; el subtotal y el total
 * los calcula el sistema (spec §17).
 */
@Component({
  selector: 'app-cash-denomination-counter',
  standalone: true,
  imports: [ClpPipe, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './cash-denomination-counter.html',
  styleUrl: './cash-denomination-counter.css',
})
export class CashDenominationCounterComponent {
  /** Denominaciones a mostrar. Por defecto, las del peso chileno (spec §2). */
  readonly denominations = input<readonly number[]>(DENOMINATIONS);

  /** Conteo actual. Two-way: [(value)]="conteo" */
  readonly value = model<CashCountItem[]>(emptyCountItems());

  /** Modo solo lectura, para el detalle de sesiones históricas. */
  readonly readonly = input(false);

  /** Etiqueta de la fila de total. */
  readonly totalLabel = input('TOTAL EFECTIVO');

  /** Emite el total cada vez que cambia el conteo. */
  readonly totalChange = output<number>();

  /** Total = Σ(denominación × cantidad) (spec §14). */
  readonly total = computed(() => countTotal(this.value()));

  /** Filas alineadas con las denominaciones configuradas, completando las ausentes con 0. */
  readonly rows = computed<CashCountItem[]>(() => {
    const actuales = this.value();
    return this.denominations().map(
      (denomination) =>
        actuales.find((item) => item.denomination === denomination) ?? {
          denomination,
          quantity: 0,
          subtotal: 0,
        },
    );
  });

  /** Cantidad total de billetes y monedas contados. */
  readonly totalPiezas = computed(() => this.rows().reduce((sum, row) => sum + row.quantity, 0));

  onQuantityChange(denomination: number, raw: string): void {
    if (this.readonly()) return;
    // buildCountItem normaliza: sin negativos, sin decimales (spec §16.6).
    const actualizado = buildCountItem(denomination, raw);
    const siguiente = this.rows().map((row) =>
      row.denomination === denomination ? actualizado : row,
    );
    this.value.set(siguiente);
    this.totalChange.emit(countTotal(siguiente));
  }

  /** Enter salta a la siguiente denominación: el conteo se hace sin soltar el teclado. */
  onEnter(event: Event, index: number): void {
    event.preventDefault();
    const inputs = (event.target as HTMLElement)
      .closest('table')
      ?.querySelectorAll<HTMLInputElement>('input[data-row]');
    inputs?.[index + 1]?.focus();
    inputs?.[index + 1]?.select();
  }

  limpiar(): void {
    if (this.readonly()) return;
    const vacio = emptyCountItems();
    this.value.set(vacio);
    this.totalChange.emit(0);
  }

  /** Muestra vacío en vez de 0 para que la persona escriba directo. */
  displayQuantity(quantity: number): string {
    return quantity === 0 ? '' : String(quantity);
  }
}
