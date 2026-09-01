import { TestBed } from '@angular/core/testing';
import { HttpRequest, HttpHandlerFn, HttpEvent } from '@angular/common/http';
import { of } from 'rxjs';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from '../services/auth.service';

describe('authInterceptor', () => {
  // Tipado como objeto plano (no Partial<AuthService>): `token` es un getter
  // sin setter en AuthService, por lo que TypeScript lo infiere como
  // read-only incluso a través de Partial<T>, lo que impediría la asignación
  // de abajo bajo `strict: true`. Ver task-12-18-report.md.
  let authServiceStub: { token: string | null };

  beforeEach(() => {
    authServiceStub = { token: null };
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: authServiceStub }],
    });
  });

  it('no agrega Authorization si no hay token', (done) => {
    const req = new HttpRequest('GET', '/api/clientes');
    const next: HttpHandlerFn = (r) => {
      expect(r.headers.has('Authorization')).toBeFalse();
      done();
      return of({} as HttpEvent<unknown>);
    };

    TestBed.runInInjectionContext(() => authInterceptor(req, next));
  });

  it('agrega el header Authorization cuando hay token', (done) => {
    authServiceStub.token = 'tok-123';
    const req = new HttpRequest('GET', '/api/clientes');
    const next: HttpHandlerFn = (r) => {
      expect(r.headers.get('Authorization')).toBe('Bearer tok-123');
      done();
      return of({} as HttpEvent<unknown>);
    };

    TestBed.runInInjectionContext(() => authInterceptor(req, next));
  });
});
