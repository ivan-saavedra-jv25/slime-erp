import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { AuthService } from '../../core/services/auth.service';

interface Kpi {
  label: string;
  value: string;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, MatCardModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent {
  kpis: Kpi[] = [
    { label: 'Clientes activos', value: '—' },
    { label: 'Productos activos', value: '—' },
    { label: 'Usuarios del tenant', value: '—' },
  ];

  constructor(public auth: AuthService) {}
}
