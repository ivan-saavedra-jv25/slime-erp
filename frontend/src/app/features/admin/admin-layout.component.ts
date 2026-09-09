import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { AdminAuthService } from '../../core/services/admin-auth.service';
import { AlertasService } from '../../core/services/alertas.service';

interface NavItem {
  ruta: string;
  label: string;
  icono: string;
  permiso: string;
  exact?: boolean;
}

const ITEMS: NavItem[] = [
  { ruta: '/admin/dashboard', label: 'Dashboard', icono: 'dashboard', permiso: 'EMPRESAS_VER' },
  { ruta: '/admin/empresas', label: 'Empresas', icono: 'apartment', permiso: 'EMPRESAS_VER' },
  { ruta: '/admin/suscripciones', label: 'Suscripciones', icono: 'event_repeat', permiso: 'SUSCRIPCIONES_VER' },
  { ruta: '/admin/planes', label: 'Planes', icono: 'inventory_2', permiso: 'PLANES_VER' },
  { ruta: '/admin/pagos', label: 'Pagos', icono: 'account_balance_wallet', permiso: 'PAGOS_VER' },
  { ruta: '/admin/dte', label: 'DTE', icono: 'description', permiso: 'DTE_VER' },
  { ruta: '/admin/sii', label: 'SII', icono: 'verified_user', permiso: 'SII_VER' },
  { ruta: '/admin/alertas', label: 'Alertas', icono: 'notifications', permiso: 'ALERTAS_VER' },
  { ruta: '/admin/soporte', label: 'Soporte', icono: 'support_agent', permiso: 'SOPORTE_VER' },
  { ruta: '/admin/auditoria', label: 'Auditoría', icono: 'history', permiso: 'AUDITORIA_VER' },
  { ruta: '/admin/configuracion', label: 'Configuración', icono: 'settings', permiso: 'CONFIG_GLOBAL_VER' },
  { ruta: '/admin/perfil', label: 'Mi perfil', icono: 'person', permiso: 'SESIONES_VER' },
];

@Component({
  selector: 'app-admin-layout',
  standalone: true,
  imports: [
    CommonModule,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatSidenavModule,
    MatIconModule,
    MatButtonModule,
  ],
  templateUrl: './admin-layout.component.html',
  styleUrl: './admin-layout.component.scss',
})
export class AdminLayoutComponent implements OnInit {
  sidenavAbierto = signal(true);
  alertasCriticas = signal<number | null>(null);

  constructor(
    public auth: AdminAuthService,
    private router: Router,
    private alertasService: AlertasService
  ) {}

  ngOnInit(): void {
    this.cargarContadorAlertas();
  }

  private cargarContadorAlertas(): void {
    this.alertasService.summary().subscribe({
      next: (resumen) => this.alertasCriticas.set(resumen.critical),
      error: () => this.alertasCriticas.set(null),
    });
  }

  get items(): NavItem[] {
    return ITEMS.filter((item) => this.auth.tienePermiso(item.permiso));
  }

  get iniciales(): string {
    const nombre = this.auth.session()?.nombre ?? '';
    return nombre
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map((p) => p[0]?.toUpperCase())
      .join('');
  }

  toggleSidenav(): void {
    this.sidenavAbierto.update((v) => !v);
  }

  cerrarSesion(): void {
    this.auth.cerrarSesion().subscribe({
      next: () => this.router.navigate(['/login']),
      error: () => this.router.navigate(['/login']),
    });
  }
}