import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from '../core/services/auth.service';
import { Permiso } from '../core/models/models';

interface NavItem {
  ruta: string;
  label: string;
  icono: string;
  permiso?: Permiso;
}

const NAV_ITEMS: NavItem[] = [
  { ruta: '/dashboard', label: 'Dashboard', icono: 'dashboard' },
  { ruta: '/clientes', label: 'Clientes', icono: 'group', permiso: 'CLIENTES_VER' },
  { ruta: '/productos', label: 'Productos', icono: 'inventory_2', permiso: 'PRODUCTOS_VER' },
  { ruta: '/usuarios', label: 'Usuarios', icono: 'manage_accounts', permiso: 'USUARIOS_VER' },
  { ruta: '/admin/empresas', label: 'Empresas', icono: 'apartment', permiso: 'EMPRESAS_ADMINISTRAR' },
];

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [
    CommonModule,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatSidenavModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
  ],
  templateUrl: './layout.component.html',
  styleUrl: './layout.component.scss',
})
export class LayoutComponent {
  constructor(public auth: AuthService, private router: Router) {}

  get items(): NavItem[] {
    return NAV_ITEMS.filter((item) => !item.permiso || this.auth.tienePermiso(item.permiso));
  }

  cerrarSesion(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
