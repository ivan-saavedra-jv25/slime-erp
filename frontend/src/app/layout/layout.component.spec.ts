import { LayoutComponent } from './layout.component';
import { AuthService } from '../core/services/auth.service';
import { Router } from '@angular/router';

describe('LayoutComponent', () => {
  function crear(permisos: string[]) {
    const authStub = {
      session: () => ({ nombre: 'Admin Demo' }),
      tienePermiso: (p: string) => permisos.includes(p),
      logout: jasmine.createSpy('logout'),
    } as unknown as AuthService;
    const routerStub = { navigate: jasmine.createSpy('navigate') } as unknown as Router;
    return { layout: new LayoutComponent(authStub, routerStub), authStub, routerStub };
  }

  it('siempre incluye Dashboard sin requerir permiso', () => {
    const { layout } = crear([]);
    expect(layout.items.map((i) => i.label)).toContain('Dashboard');
  });

  it('oculta Usuarios si no se tiene USUARIOS_VER', () => {
    const { layout } = crear(['CLIENTES_VER']);
    expect(layout.items.map((i) => i.label)).not.toContain('Usuarios');
  });

  it('muestra Usuarios si se tiene USUARIOS_VER', () => {
    const { layout } = crear(['USUARIOS_VER']);
    expect(layout.items.map((i) => i.label)).toContain('Usuarios');
  });

  it('cerrarSesion desloguea y navega a /login', () => {
    const { layout, authStub, routerStub } = crear([]);
    layout.cerrarSesion();
    expect(authStub.logout).toHaveBeenCalled();
    expect(routerStub.navigate).toHaveBeenCalledWith(['/login']);
  });
});
