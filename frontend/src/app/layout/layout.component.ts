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
  exact?: boolean;
}

interface NavGroup {
  key: string;
  titulo: string;
  icono: string;
  items: NavItem[];
}

const DASHBOARD: NavItem = { ruta: '/dashboard', label: 'Dashboard', icono: 'dashboard' };

const GRUPOS: NavGroup[] = [
  {
    key: 'contactos',
    titulo: 'Contactos',
    icono: 'group',
    items: [{ ruta: '/clientes', label: 'Clientes', icono: 'group', permiso: 'CLIENTES_VER' }],
  },
  {
    key: 'catalogo',
    titulo: 'Catálogo',
    icono: 'inventory_2',
    items: [
      { ruta: '/productos', label: 'Productos', icono: 'inventory_2', permiso: 'PRODUCTOS_VER' },
      { ruta: '/categorias', label: 'Categorías', icono: 'category', permiso: 'CATEGORIAS_VER' },
      { ruta: '/bodegas', label: 'Bodegas', icono: 'warehouse', permiso: 'BODEGAS_VER' },
      { ruta: '/formas-pago', label: 'Formas de pago', icono: 'payments', permiso: 'FORMAS_PAGO_VER' },
    ],
  },
  {
    key: 'inventario',
    titulo: 'Inventario',
    icono: 'inventory',
    items: [
      { ruta: '/movimientos', label: 'Movimientos', icono: 'swap_horiz', permiso: 'MOVIMIENTOS_VER', exact: true },
      { ruta: '/movimientos/historial', label: 'Historial', icono: 'history', permiso: 'MOVIMIENTOS_VER' },
    ],
  },
  {
    key: 'administracion',
    titulo: 'Administración',
    icono: 'admin_panel_settings',
    items: [
      { ruta: '/usuarios', label: 'Usuarios', icono: 'manage_accounts', permiso: 'USUARIOS_VER' },
      { ruta: '/admin/empresas', label: 'Empresas', icono: 'apartment', permiso: 'EMPRESAS_ADMINISTRAR' },
    ],
  },
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
  dashboard = DASHBOARD;
  tituloPagina = signal('');
  esMovil = signal(false);
  sidenavAbierto = signal(true);
  private grupoExpandido = signal<string | null>(null);

  constructor(
    public auth: AuthService,
    private router: Router,
    private breakpointObserver: BreakpointObserver
  ) {}

  get grupos(): NavGroup[] {
    return GRUPOS.map((grupo) => ({
      ...grupo,
      items: grupo.items.filter((item) => !item.permiso || this.auth.tienePermiso(item.permiso)),
    })).filter((grupo) => grupo.items.length > 0);
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
    this.expandirGrupoActivo(this.router.url);
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe((e) => {
      const url = (e as NavigationEnd).urlAfterRedirects;
      this.actualizarTitulo(url);
      this.expandirGrupoActivo(url);
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

  toggleGrupo(key: string): void {
    this.grupoExpandido.update((actual) => (actual === key ? null : key));
  }

  expandido(key: string): boolean {
    return this.grupoExpandido() === key;
  }

  esGrupoActivo(grupo: NavGroup): boolean {
    return grupo.items.some((item) => this.router.url.startsWith(item.ruta));
  }

  private actualizarTitulo(url: string): void {
    const todos = [this.dashboard, ...this.grupos.flatMap((g) => g.items)];
    const candidatos = todos.filter((i) => url === i.ruta || (!i.exact && url.startsWith(`${i.ruta}/`)));
    const item = candidatos.sort((a, b) => b.ruta.length - a.ruta.length)[0];
    this.tituloPagina.set(item?.label ?? '');
  }

  private expandirGrupoActivo(url: string): void {
    const grupo = this.grupos.find((g) => g.items.some((item) => url.startsWith(item.ruta)));
    if (grupo) {
      this.grupoExpandido.set(grupo.key);
    }
  }

  cerrarSesion(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
