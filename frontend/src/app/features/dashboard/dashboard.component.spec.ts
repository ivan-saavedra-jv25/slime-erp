import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { DashboardComponent } from './dashboard.component';
import { AuthService } from '../../core/services/auth.service';
import { DashboardService } from '../../core/services/dashboard.service';
import { DashboardResponse } from '../../core/models/models';

const RESUMEN_VACIO: DashboardResponse = {
  kpis: {
    ventasPeriodo: 0,
    ventasPeriodoAnterior: 0,
    variacionPct: null,
    comprasPeriodo: 0,
    documentosEmitidos: 0,
    totalClientes: 0,
    totalProductos: 0,
    stockDisponible: 0,
    productosStockBajo: 0,
    productosSinStock: 0,
    cuentasPorCobrarSaldo: 0,
    cuentasPorCobrarPendientes: 0,
  },
  alertas: [],
  ultimasVentas: [],
};

describe('DashboardComponent', () => {
  let fixture: ComponentFixture<DashboardComponent>;

  beforeEach(async () => {
    const authStub = { session: () => ({ nombre: 'Admin Demo' }) } as unknown as AuthService;
    const dashboardStub = {
      resumen: () => of(RESUMEN_VACIO),
      ventasEvolucion: () => of([]),
    } as unknown as DashboardService;

    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authStub },
        { provide: DashboardService, useValue: dashboardStub },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
  });

  it('muestra el nombre del usuario logueado', () => {
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Admin Demo');
  });

  it('muestra las tarjetas de KPI una vez cargado el resumen', () => {
    const tarjetas = (fixture.nativeElement as HTMLElement).querySelectorAll('.kpi-card');
    expect(tarjetas.length).toBeGreaterThan(0);
  });
});
