import { fakeAsync, tick } from '@angular/core/testing';
import { of } from 'rxjs';
import { Router } from '@angular/router';
import { CotizacionesComponent } from './cotizaciones.component';
import { CotizacionService } from '../../core/services/cotizacion.service';
import { AuthService } from '../../core/services/auth.service';

describe('CotizacionesComponent', () => {
  const dashboardVacio = {
    desde: '2026-09-01',
    hasta: '2026-09-30',
    cantidad: 0,
    pendientes: 0,
    aceptadas: 0,
    rechazadas: 0,
    enviadas: 0,
    montoCotizado: 0,
    montoAceptado: 0,
    tasaConversion: null,
    porEstado: [],
  };

  function crear() {
    const cotizacionService = {
      listar: jasmine.createSpy('listar').and.returnValue(of({ contenido: [], total: 0 })),
      dashboard: jasmine.createSpy('dashboard').and.returnValue(of(dashboardVacio)),
    } as unknown as CotizacionService;
    const router = { navigate: jasmine.createSpy('navigate') } as unknown as Router;
    const auth = { tienePermiso: () => true } as unknown as AuthService;
    return { component: new CotizacionesComponent(cotizacionService, router, auth), cotizacionService, router };
  }

  it('no consulta si "desde" es posterior a "hasta"', () => {
    const { component, cotizacionService } = crear();
    component.desde = '2026-09-30';
    component.hasta = '2026-09-01';

    component.consultar();

    expect(cotizacionService.listar).not.toHaveBeenCalled();
    expect(cotizacionService.dashboard).not.toHaveBeenCalled();
    expect(component.error).toContain('no puede ser posterior');
  });

  it('seleccionar un período calcula el rango y recarga métricas y listado', () => {
    const { component, cotizacionService } = crear();

    component.seleccionarPeriodo('anio');

    expect(component.periodoActivo).toBe('anio');
    expect(component.desde.endsWith('-01-01')).toBeTrue();
    expect(cotizacionService.dashboard).toHaveBeenCalledWith(component.desde, component.hasta);
    expect(cotizacionService.listar).toHaveBeenCalledWith(
      jasmine.objectContaining({ desde: component.desde, hasta: component.hasta })
    );
  });

  it('el período personalizado respeta las fechas escritas por el usuario', () => {
    const { component, cotizacionService } = crear();
    component.desde = '2026-01-15';
    component.hasta = '2026-02-15';

    component.seleccionarPeriodo('personalizado');

    expect(component.desde).toBe('2026-01-15');
    expect(cotizacionService.dashboard).toHaveBeenCalledWith('2026-01-15', '2026-02-15');
  });

  it('consulta con los filtros cuando el rango es válido', () => {
    const { component, cotizacionService } = crear();
    component.estado = 'ENVIADA';
    component.desde = '2026-09-01';
    component.hasta = '2026-09-30';

    component.consultar();

    expect(cotizacionService.listar).toHaveBeenCalledWith(
      jasmine.objectContaining({ estado: 'ENVIADA', desde: '2026-09-01', hasta: '2026-09-30', pagina: 0 })
    );
    expect(component.error).toBe('');
  });

  it('onSortChange cambia el orden y vuelve a la primera página', () => {
    const { component, cotizacionService } = crear();
    component.paginaActual = 3;

    component.onSortChange({ active: 'montoTotal', direction: 'asc' });

    expect(component.orden).toBe('montoTotal');
    expect(component.direccion).toBe('asc');
    expect(component.paginaActual).toBe(0);
    expect(cotizacionService.listar).toHaveBeenCalledWith(
      jasmine.objectContaining({ sort: 'montoTotal', dir: 'asc', pagina: 0 })
    );
  });

  it('onPageChange mantiene la página seleccionada', () => {
    const { component, cotizacionService } = crear();

    component.onPageChange({ pageIndex: 2, pageSize: 25, length: 100 });

    expect(component.paginaActual).toBe(2);
    expect(component.tamanoPagina).toBe(25);
    expect(cotizacionService.listar).toHaveBeenCalledWith(
      jasmine.objectContaining({ pagina: 2, tamano: 25 })
    );
  });

  it('la búsqueda espera el debounce antes de consultar', fakeAsync(() => {
    const { component, cotizacionService } = crear();
    component.ngOnInit();
    (cotizacionService.listar as jasmine.Spy).calls.reset();

    component.busqueda = 'ABC';
    component.onBusquedaChange();
    tick(299);
    expect(cotizacionService.listar).not.toHaveBeenCalled();

    tick(1);
    expect(cotizacionService.listar).toHaveBeenCalledWith(jasmine.objectContaining({ q: 'ABC', pagina: 0 }));

    component.ngOnDestroy();
  }));

  it('abrir navega al detalle de la cotización', () => {
    const { component, router } = crear();

    component.abrir({ id: 42 } as never);

    expect(router.navigate).toHaveBeenCalledWith(['/cotizaciones', 42]);
  });
});
