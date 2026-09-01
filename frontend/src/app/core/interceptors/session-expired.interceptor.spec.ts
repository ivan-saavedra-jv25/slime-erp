import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpHandlerFn, HttpEvent, HttpRequest } from '@angular/common/http';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { sessionExpiredInterceptor } from './session-expired.interceptor';
import { AuthService } from '../services/auth.service';

describe('sessionExpiredInterceptor', () => {
  let authServiceStub: { estaAutenticado: boolean; logout: jasmine.Spy };
  let routerStub: { navigate: jasmine.Spy };

  beforeEach(() => {
    authServiceStub = { estaAutenticado: false, logout: jasmine.createSpy('logout') };
    routerStub = { navigate: jasmine.createSpy('navigate') };
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceStub },
        { provide: Router, useValue: routerStub },
      ],
    });
  });

  it('cierra la sesión y redirige a /login ante un 401 mientras el usuario está autenticado (token expirado/inválido)', (done) => {
    authServiceStub.estaAutenticado = true;
    const req = new HttpRequest('GET', '/api/clientes');
    const error = new HttpErrorResponse({ status: 401 });
    const next: HttpHandlerFn = () => throwError(() => error);

    TestBed.runInInjectionContext(() => sessionExpiredInterceptor(req, next)).subscribe({
      error: () => {
        expect(authServiceStub.logout).toHaveBeenCalled();
        expect(routerStub.navigate).toHaveBeenCalledWith(['/login']);
        done();
      },
    });
  });

  it('no cierra la sesión ni redirige ante un 401 si el usuario no está autenticado (ej. login fallido)', (done) => {
    authServiceStub.estaAutenticado = false;
    const req = new HttpRequest('GET', '/api/clientes');
    const error = new HttpErrorResponse({ status: 401 });
    const next: HttpHandlerFn = () => throwError(() => error);

    TestBed.runInInjectionContext(() => sessionExpiredInterceptor(req, next)).subscribe({
      error: () => {
        expect(authServiceStub.logout).not.toHaveBeenCalled();
        expect(routerStub.navigate).not.toHaveBeenCalled();
        done();
      },
    });
  });

  it('no cierra la sesión ante un 403 (falta de permiso) aunque el usuario esté autenticado', (done) => {
    authServiceStub.estaAutenticado = true;
    const req = new HttpRequest('GET', '/api/admin/empresas');
    const error = new HttpErrorResponse({ status: 403 });
    const next: HttpHandlerFn = () => throwError(() => error);

    TestBed.runInInjectionContext(() => sessionExpiredInterceptor(req, next)).subscribe({
      error: () => {
        expect(authServiceStub.logout).not.toHaveBeenCalled();
        expect(routerStub.navigate).not.toHaveBeenCalled();
        done();
      },
    });
  });

  it('deja pasar una respuesta exitosa sin modificarla', (done) => {
    const req = new HttpRequest('GET', '/api/clientes');
    const respuesta = { ok: true } as unknown as HttpEvent<unknown>;
    const next: HttpHandlerFn = () => of(respuesta);

    TestBed.runInInjectionContext(() => sessionExpiredInterceptor(req, next)).subscribe((event) => {
      expect(event).toBe(respuesta);
      expect(authServiceStub.logout).not.toHaveBeenCalled();
      expect(routerStub.navigate).not.toHaveBeenCalled();
      done();
    });
  });
});
