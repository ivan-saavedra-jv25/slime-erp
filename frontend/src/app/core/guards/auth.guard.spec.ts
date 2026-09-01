import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';

describe('authGuard', () => {
  // Tipado como objeto plano (no Partial<AuthService>): `estaAutenticado` es
  // un getter sin setter en AuthService, por lo que TypeScript lo infiere
  // como read-only incluso a través de Partial<T>, lo que impediría la
  // asignación de abajo bajo `strict: true`. Ver task-12-18-report.md.
  let authServiceStub: { estaAutenticado: boolean };
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(() => {
    authServiceStub = { estaAutenticado: false };
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceStub },
        { provide: Router, useValue: routerSpy },
      ],
    });
  });

  it('permite el acceso cuando hay sesión activa', () => {
    authServiceStub.estaAutenticado = true;
    const resultado = TestBed.runInInjectionContext(() => authGuard({} as any, {} as any));
    expect(resultado).toBeTrue();
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });

  it('redirige a /login cuando no hay sesión', () => {
    const resultado = TestBed.runInInjectionContext(() => authGuard({} as any, {} as any));
    expect(resultado).toBeFalse();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/login']);
  });
});
