import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { SeedService } from '../core/seed/seed.service';
import { CajaFacade } from '../core/services/caja-facade.service';

@Component({
  selector: 'app-caja-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatButtonModule, MatIconModule],
  templateUrl: './shell.html',
  styleUrl: './shell.css',
})
export class Shell {
  private readonly facade = inject(CajaFacade);

  readonly abierta = this.facade.hayCajaAbierta;

  constructor() {
    inject(SeedService).seed();
  }
}
