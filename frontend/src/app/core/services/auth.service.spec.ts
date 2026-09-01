import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { AuthService } from './auth.service';
import { environment } from '../../../environments/environment';
import { LoginResponse } from '../models/models';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  const respuesta: LoginResponse = {
    token: 'tok-123',
    usuarioId: 1,
    tenantId: 1,
    tenantNombre: 'Empresa Demo',
    nombre: 'Admin Demo',
    email: 'admin@demo.cl',
    rut: '15.234.567-8',
    rol: 'ADMIN',
    permisos: ['CLIENTES_VER', 'CLIENTES_EDITAR'],
  };

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('guarda la sesión y expone el token tras un login exitoso', () => {
    service.login('admin@demo.cl', 'admin123').subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/auth/login`);
    expect(req.request.method).toBe('POST');
    req.flush(respuesta);

    expect(service.estaAutenticado).toBeTrue();
    expect(service.token).toBe('tok-123');
    expect(JSON.parse(localStorage.getItem('slime_erp_session')!)).toEqual(respuesta);
  });

  it('tienePermiso refleja los permisos de la sesión activa', () => {
    service.login('admin@demo.cl', 'admin123').subscribe();
    httpMock.expectOne(`${environment.apiUrl}/auth/login`).flush(respuesta);

    expect(service.tienePermiso('CLIENTES_VER')).toBeTrue();
    expect(service.tienePermiso('USUARIOS_EDITAR')).toBeFalse();
  });

  it('logout limpia la sesión', () => {
    service.login('admin@demo.cl', 'admin123').subscribe();
    httpMock.expectOne(`${environment.apiUrl}/auth/login`).flush(respuesta);

    service.logout();

    expect(service.estaAutenticado).toBeFalse();
    expect(localStorage.getItem('slime_erp_session')).toBeNull();
  });
});

describe('AuthService - sesión corrupta en localStorage', () => {
  afterEach(() => {
    localStorage.clear();
  });

  it('no lanza excepción, deja estaAutenticado en false y limpia la clave corrupta', () => {
    localStorage.clear();
    localStorage.setItem('slime_erp_session', 'not-json{');

    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    let service!: AuthService;
    expect(() => (service = TestBed.inject(AuthService))).not.toThrow();
    expect(service.estaAutenticado).toBeFalse();
    expect(localStorage.getItem('slime_erp_session')).toBeNull();
  });
});
