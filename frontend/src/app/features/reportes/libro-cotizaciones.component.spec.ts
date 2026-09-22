import { of, throwError } from 'rxjs';
import { LibroCotizacionesComponent } from './libro-cotizaciones.component';
import { ReporteService } from '../../core/services/reporte.service';
import { LibroCotizacionesResponse } from '../../core/models/models';

describe('LibroCotizacionesComponent', () => {
  function libro(): LibroCotizacionesResponse {
    return {
      desde: '2026-09-01',
      hasta: '2026-09-30',
      estado: null,
      filas: [],
      resumen: { cantidad: 0, montoNeto: 0, montoIva: 0, montoTotal: 0 },
    };
  }

  function crear() {
    const reporteService = {
      libroCotizaciones: jasmine.createSpy('libroCotizaciones').and.returnValue(of(libro())),
      libroCotizacionesExcel: jasmine.createSpy('libroCotizacionesExcel').and.returnValue(of(new Blob())),
    } as unknown as ReporteService;
    return { component: new LibroCotizacionesComponent(reporteService), reporteService };
  }

  it('no consulta si "desde" es posterior a "hasta"', () => {
    const { component, reporteService } = crear();
    component.desde = '2026-09-30';
    component.hasta = '2026-09-01';

    component.consultar();

    expect(reporteService.libroCotizaciones).not.toHaveBeenCalled();
    expect(component.error).toContain('no puede ser posterior');
  });

  it('consulta enviando el estado seleccionado', () => {
    const { component, reporteService } = crear();
    component.desde = '2026-09-01';
    component.hasta = '2026-09-30';
    component.estado = 'ACEPTADA';

    component.consultar();

    expect(reporteService.libroCotizaciones).toHaveBeenCalledWith('2026-09-01', '2026-09-30', 'ACEPTADA');
    expect(component.libro).not.toBeNull();
  });

  it('exporta usando los valores del libro cargado, no los filtros vivos', () => {
    const { component, reporteService } = crear();
    component.consultar();
    component.desde = '2026-01-01';

    component.exportarExcel();

    expect(reporteService.libroCotizacionesExcel).toHaveBeenCalledWith('2026-09-01', '2026-09-30', null);
    expect(component.exportando).toBeFalse();
  });

  it('muestra un error si falla la consulta', () => {
    const { component, reporteService } = crear();
    (reporteService.libroCotizaciones as jasmine.Spy).and.returnValue(throwError(() => ({ error: {} })));

    component.consultar();

    expect(component.error).toContain('No se pudo generar');
    expect(component.cargando).toBeFalse();
  });
});
