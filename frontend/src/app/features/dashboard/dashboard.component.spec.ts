import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DashboardComponent } from './dashboard.component';
import { AuthService } from '../../core/services/auth.service';

describe('DashboardComponent', () => {
  let fixture: ComponentFixture<DashboardComponent>;

  beforeEach(async () => {
    const authStub = { session: () => ({ nombre: 'Admin Demo' }) } as unknown as AuthService;
    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [{ provide: AuthService, useValue: authStub }],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
  });

  it('muestra el nombre del usuario logueado', () => {
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Admin Demo');
  });

  it('muestra tres tarjetas de KPI', () => {
    const tarjetas = (fixture.nativeElement as HTMLElement).querySelectorAll('mat-card');
    expect(tarjetas.length).toBe(3);
  });
});
