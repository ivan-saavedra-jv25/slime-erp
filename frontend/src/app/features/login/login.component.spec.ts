import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { LoginComponent } from './login.component';
import { AuthService } from '../../core/services/auth.service';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let component: LoginComponent;
  let authSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    authSpy = jasmine.createSpyObj('AuthService', ['login']);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        { provide: AuthService, useValue: authSpy },
        { provide: Router, useValue: routerSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
  });

  it('navega a /dashboard cuando el login es exitoso', () => {
    authSpy.login.and.returnValue(of({} as any));

    component.ingresar();

    expect(routerSpy.navigate).toHaveBeenCalledWith(['/dashboard']);
    expect(component.error).toBe('');
  });

  it('muestra un error cuando el login falla', () => {
    authSpy.login.and.returnValue(throwError(() => new Error('401')));

    component.ingresar();

    expect(component.error).toBe('Email o contraseña incorrectos.');
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });
});
