import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { LoginComponent } from './login.component';
import { AuthService } from '../../core/services/auth.service';
import { AdminAuthService } from '../../core/services/admin-auth.service';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let component: LoginComponent;
  let authSpy: jasmine.SpyObj<AuthService>;
  let adminAuthSpy: jasmine.SpyObj<AdminAuthService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    authSpy = jasmine.createSpyObj('AuthService', ['login']);
    adminAuthSpy = jasmine.createSpyObj('AdminAuthService', ['login']);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        { provide: AuthService, useValue: authSpy },
        { provide: AdminAuthService, useValue: adminAuthSpy },
        { provide: Router, useValue: routerSpy },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: { get: () => null } } },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
  });

  it('navega a /dashboard cuando el login de empresa es exitoso', () => {
    authSpy.login.and.returnValue(of({} as any));

    component.ingresar();

    expect(routerSpy.navigate).toHaveBeenCalledWith(['/dashboard']);
    expect(component.error).toBe('');
  });

  it('usa el login administrativo y navega a /admin en modo admin', () => {
    component.modoAdmin = true;
    adminAuthSpy.login.and.returnValue(of({} as any));

    component.ingresar();

    expect(adminAuthSpy.login).toHaveBeenCalled();
    expect(authSpy.login).not.toHaveBeenCalled();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/admin']);
  });

  it('muestra un error cuando el login falla', () => {
    authSpy.login.and.returnValue(throwError(() => new Error('401')));

    component.ingresar();

    expect(component.error).toBe('Email o contraseña incorrectos.');
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });
});