import { LayoutComponent } from './layout.component';
import { AuthService } from '../core/services/auth.service';
import { Router } from '@angular/router';
import { BreakpointObserver } from '@angular/cdk/layout';
import { of } from 'rxjs';

describe('LayoutComponent', () => {
  function crear(permisos: string[]) {
    const authStub = {
      session: () => ({ nombre: 'Admin Demo' }),
      tienePermiso: (p: string) => permisos.includes(p),
      logout: jasmine.createSpy('logout'),
    } as unknown as AuthService;
    const routerStub = { navigate: jasmine.createSpy('navigate') } as unknown as Router;
    const breakpointObserverStub = { observe: () => of({ matches: false }) } as unknown as BreakpointObserver;
    return {
      layout: new LayoutComponent(authStub, routerStub, breakpointObserverStub),
      authStub,
      routerStub,
    };
  }

  function labels(layout: LayoutComponent): string[] {
    return layout.grupos.flatMap((g) => g.items.map((i) => i.label));
  }

  it('siempre incluye Dashboard sin requerir permiso', () => {
    const { layout } = crear([]);
    expect(layout.dashboard.label).toBe('Dashboard');
  });

  it('oculta Usuarios si no se tiene USUARIOS_VER', () => {
    const { layout } = crear(['CLIENTES_VER']);
    expect(labels(layout)).not.toContain('Usuarios');
  });

  it('muestra Usuarios si se tiene USUARIOS_VER', () => {
    const { layout } = crear(['USUARIOS_VER']);
    expect(labels(layout)).toContain('Usuarios');
  });

  it('oculta un grupo completo si no queda ningún ítem visible', () => {
    const { layout } = crear(['USUARIOS_VER']);
    expect(layout.grupos.find((g) => g.key === 'contactos')).toBeUndefined();
    expect(layout.grupos.find((g) => g.key === 'catalogo')).toBeUndefined();
  });

  it('cerrarSesion desloguea y navega a /login', () => {
    const { layout, authStub, routerStub } = crear([]);
    layout.cerrarSesion();
    expect(authStub.logout).toHaveBeenCalled();
    expect(routerStub.navigate).toHaveBeenCalledWith(['/login']);
  });
});
