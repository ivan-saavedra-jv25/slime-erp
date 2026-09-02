import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenavModule } from '@angular/material/sidenav';
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
    MatIconModule,
    MatButtonModule,
  ],
  templateUrl: './layout.component.html',
  styleUrl: './layout.component.scss',
})
export class LayoutComponent implements OnInit {
  tituloPagina = signal('');
  esMovil = signal(false);
  sidenavAbierto = signal(true);

  constructor(
    public auth: AuthService,
    private router: Router,
    private breakpointObserver: BreakpointObserver
  ) {}

  get items(): NavItem[] {
    return NAV_ITEMS.filter((item) => !item.permiso || this.auth.tienePermiso(item.permiso));
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

  ngOnInit(): void {
    this.actualizarTitulo(this.router.url);
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe((e) => {
      this.actualizarTitulo((e as NavigationEnd).urlAfterRedirects);
      if (this.esMovil()) {
        this.sidenavAbierto.set(false);
      }
    });

    this.breakpointObserver.observe(Breakpoints.Handset).subscribe((result) => {
      this.esMovil.set(result.matches);
      this.sidenavAbierto.set(!result.matches);
    });
  }

  toggleSidenav(): void {
    this.sidenavAbierto.update((v) => !v);
  }

  private actualizarTitulo(url: string): void {
    const item = this.items.find((i) => url === i.ruta || url.startsWith(`${i.ruta}/`));
    this.tituloPagina.set(item?.label ?? '');
  }

  cerrarSesion(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
