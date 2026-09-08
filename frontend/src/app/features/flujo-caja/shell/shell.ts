import { Component, computed, effect, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { CashflowStore } from '../core/cashflow.store';
import { yearAlerts } from '../core/health';

@Component({
  selector: 'app-flujo-caja-shell',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.css',
})
export class Shell {
  private readonly store = inject(CashflowStore);

  readonly alerts = computed(() => yearAlerts(this.store.projection()));
  private readonly snackBar = inject(MatSnackBar);

  constructor() {
    effect(() => {
      if (this.store.loadCorrupted()) {
        this.snackBar.open(
          'Los datos guardados no se pudieron leer. Se partió de un flujo vacío.',
          'Entendido',
          { duration: 8000 },
        );
        this.store.loadCorrupted.set(false);
      }
    });
  }
}
