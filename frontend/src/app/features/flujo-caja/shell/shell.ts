import { Component } from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-flujo-caja-shell',
  standalone: true,
  imports: [MatIcon, RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './shell.html',
  styleUrl: './shell.css',
})
export class Shell {
  readonly tabs = [
    { path: 'resumen', label: 'Resumen', icon: 'insights' },
    { path: 'mes', label: 'Mes', icon: 'calendar_month' },
    { path: 'auditoria', label: 'Auditoría', icon: 'fact_check' },
  ];
}