import { computed, effect, inject, Injectable, signal } from '@angular/core';
import {
  CashflowState,
  Category,
  ItemKind,
  MonthKey,
  Provisions,
  OneOffItem,
  RecurringItem,
  Settings,
} from './models';
import { currentMonthKey, currentYear, januaryOf, monthKeysFrom } from './month';
import { newId } from './id';
import { projectYear } from './projection';
import { sampleState } from './sample';
import { MONTHS_PER_YEAR, seedState } from './seed';
import { StorageService } from './storage.service';

export type RecurringInput = Omit<RecurringItem, 'id'>;
export type OneOffInput = Omit<OneOffItem, 'id'>;

@Injectable({ providedIn: 'root' })
export class CashflowStore {
  private readonly storage = inject(StorageService);

  private readonly loaded = this.storage.load();

  private readonly _state = signal<CashflowState>(this.loaded.state);
  /** `true` si al iniciar había datos guardados ilegibles. */
  readonly loadCorrupted = signal(this.loaded.corrupted);

  readonly state = this._state.asReadonly();
  readonly settings = computed(() => this._state().settings);
  readonly categories = computed(() => this._state().categories);
  readonly incomeCategories = computed(() =>
    this._state().categories.filter((c) => c.kind === 'income'),
  );
  readonly expenseCategories = computed(() =>
    this._state().categories.filter((c) => c.kind === 'expense'),
  );

  /** Primer año del flujo: el único con saldo inicial propio. */
  readonly baseYear = computed(() => this._state().settings.baseYear);

  private readonly _year = signal(this.initialYear());
  /** Año calendario que se está viendo. */
  readonly year = this._year.asReadonly();

  readonly canGoToPreviousYear = computed(() => this._year() > this.baseYear());

  readonly months = computed(() => monthKeysFrom(januaryOf(this._year()), MONTHS_PER_YEAR));
  readonly projection = computed(() => projectYear(this._state(), this._year()));

  /** Saldo con el que abre el año visible; en años posteriores viene arrastrado. */
  readonly yearOpeningBalance = computed(() => this.projection()[0].openingBalance);
  readonly isCarriedOver = computed(() => this._year() > this.baseYear());

  private readonly _selectedMonth = signal<MonthKey>(this.initialSelectedMonth());
  readonly selectedMonth = this._selectedMonth.asReadonly();
  /** El mes visible, forzado al horizonte si el rango cambió. */
  readonly currentProjection = computed(() => {
    const months = this.projection();
    return months.find((m) => m.month === this._selectedMonth()) ?? months[0];
  });

  constructor() {
    effect(() => this.storage.save(this._state()));
  }

  /** Al abrir, se muestra el año en curso; nunca uno anterior al año base. */
  private initialYear(): number {
    return Math.max(this.loaded.state.settings.baseYear, currentYear());
  }

  private initialSelectedMonth(): MonthKey {
    const months = monthKeysFrom(januaryOf(this.initialYear()), MONTHS_PER_YEAR);
    const today = currentMonthKey();
    return months.includes(today) ? today : months[0];
  }

  categoryName(id: string): string {
    return this.categories().find((c) => c.id === id)?.name ?? 'Sin categoría';
  }

  selectMonth(month: MonthKey): void {
    this._selectedMonth.set(month);
  }

  // --- Ajustes ---

  updateSettings(patch: Partial<Settings>): void {
    this._state.update((s) => ({ ...s, settings: { ...s.settings, ...patch } }));
    this.reframeSelectedMonth();
  }

  /** Cambia el año visible. No puede ser anterior al año base. */
  setYear(year: number): void {
    this._year.set(Math.max(year, this.baseYear()));
    this.reframeSelectedMonth();
  }

  stepYear(delta: number): void {
    this.setYear(this._year() + delta);
  }

  /**
   * Mueve el año base, o sea el año en que arranca el flujo con el saldo
   * inicial. Si el año visible queda antes, se arrastra con él.
   */
  setBaseYear(year: number): void {
    this.updateSettings({ baseYear: year });
    this.setYear(Math.max(this._year(), year));
  }

  /** Al cambiar el año, el mes visible puede quedar fuera de la ventana. */
  private reframeSelectedMonth(): void {
    const months = this.months();
    if (months.includes(this._selectedMonth())) return;
    const today = currentMonthKey();
    this._selectedMonth.set(months.includes(today) ? today : months[0]);
  }

  // --- Recurrentes ---

  addRecurring(input: RecurringInput): RecurringItem {
    const item: RecurringItem = { ...input, id: newId('rec') };
    this._state.update((s) => ({ ...s, recurring: [...s.recurring, item] }));
    return item;
  }

  updateRecurring(id: string, patch: Partial<RecurringInput>): void {
    this._state.update((s) => ({
      ...s,
      recurring: s.recurring.map((r) => (r.id === id ? { ...r, ...patch } : r)),
    }));
  }

  removeRecurring(id: string): void {
    // Los overrides sin plantilla quedarían huérfanos: se borran junto con ella.
    this._state.update((s) => ({
      ...s,
      recurring: s.recurring.filter((r) => r.id !== id),
      overrides: s.overrides.filter((o) => o.recurringId !== id),
    }));
  }

  // --- Puntuales ---

  addOneOff(input: OneOffInput): OneOffItem {
    const item: OneOffItem = { ...input, id: newId('one') };
    this._state.update((s) => ({ ...s, oneOff: [...s.oneOff, item] }));
    return item;
  }

  updateOneOff(id: string, patch: Partial<OneOffInput>): void {
    this._state.update((s) => ({
      ...s,
      oneOff: s.oneOff.map((o) => (o.id === id ? { ...o, ...patch } : o)),
    }));
  }

  removeOneOff(id: string): void {
    this._state.update((s) => ({ ...s, oneOff: s.oneOff.filter((o) => o.id !== id) }));
  }

  // --- Overrides (ajuste de un recurrente en un mes) ---

  setOverride(recurringId: string, month: MonthKey, amount: number | null): void {
    this._state.update((s) => {
      const rest = s.overrides.filter((o) => !(o.recurringId === recurringId && o.month === month));
      return { ...s, overrides: [...rest, { recurringId, month, amount }] };
    });
  }

  clearOverride(recurringId: string, month: MonthKey): void {
    this._state.update((s) => ({
      ...s,
      overrides: s.overrides.filter((o) => !(o.recurringId === recurringId && o.month === month)),
    }));
  }

  hasOverride(recurringId: string, month: MonthKey): boolean {
    return this._state().overrides.some((o) => o.recurringId === recurringId && o.month === month);
  }

  // --- Categorías ---

  addCategory(input: Omit<Category, 'id'>): Category {
    const category: Category = { ...input, id: newId('cat') };
    this._state.update((s) => ({ ...s, categories: [...s.categories, category] }));
    return category;
  }

  updateCategory(id: string, patch: Partial<Omit<Category, 'id'>>): void {
    this._state.update((s) => ({
      ...s,
      categories: s.categories.map((c) => (c.id === id ? { ...c, ...patch } : c)),
    }));
  }

  renameCategory(id: string, name: string): void {
    this.updateCategory(id, { name });
  }

  updateProvisions(patch: Partial<Provisions>): void {
    this._state.update((s) => ({
      ...s,
      settings: { ...s.settings, provisions: { ...s.settings.provisions, ...patch } },
    }));
  }

  /** Cantidad de ítems que usan la categoría; una categoría en uso no se borra. */
  categoryUsage(id: string): number {
    const s = this._state();
    return (
      s.recurring.filter((r) => r.categoryId === id).length +
      s.oneOff.filter((o) => o.categoryId === id).length
    );
  }

  removeCategory(id: string): boolean {
    if (this.categoryUsage(id) > 0) return false;
    this._state.update((s) => ({ ...s, categories: s.categories.filter((c) => c.id !== id) }));
    return true;
  }

  // --- Respaldo ---

  replaceState(state: CashflowState): void {
    this._state.set(state);
    this._year.set(Math.max(state.settings.baseYear, currentYear()));
    this.reframeSelectedMonth();
    this.loadCorrupted.set(false);
  }

  resetAll(): void {
    this.replaceState(seedState());
  }

  /** Reemplaza todo por un flujo de ejemplo, útil para partir con algo cargado. */
  loadSample(): void {
    this.replaceState(sampleState(this.baseYear()));
  }

  exportBackup(): void {
    this.storage.download(this._state());
  }

  /** `true` si el respaldo era válido y quedó aplicado. */
  importBackup(json: string): boolean {
    const state = this.storage.deserialize(json);
    if (state === null) return false;
    this.replaceState(state);
    return true;
  }
}
