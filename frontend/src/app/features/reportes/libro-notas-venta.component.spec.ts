import { of, throwError } from 'rxjs';
import { LibroNotasVentaComponent } from './libro-notas-venta.component';
import { ReporteService } from '../../core/services/reporte.service';
import { LibroNotasVentaResponse } from '../../core/models/models';

describe('LibroNotasVentaComponent', () => {
  function libro(): LibroNotasVentaResponse {
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
      libroNotasVenta: jasmine.createSpy('libroNotasVenta').and.returnValue(of(libro())),
      libroNotasVentaExcel: jasmine.createSpy('libroNotasVentaExcel').and.returnValue(of(new Blob())),
    } as unknown as ReporteService;
    return { component: new LibroNotasVentaComponent(reporteService), reporteService };
  }

  it('no consulta si "desde" es posterior a "hasta"', () => {
    const { component, reporteService } = crear();
    component.desde = '2026-09-30';
    component.hasta = '2026-09-01';

    component.consultar();

    expect(reporteService.libroNotasVenta).not.toHaveBeenCalled();
    expect(component.error).toContain('no puede ser posterior');
  });

  it('consulta enviando el estado seleccionado', () => {
    const { component, reporteService } = crear();
    component.desde = '2026-09-01';
    component.hasta = '2026-09-30';
    component.estado = 'ENTREGADA';

    component.consultar();

    expect(reporteService.libroNotasVenta).toHaveBeenCalledWith('2026-09-01', '2026-09-30', 'ENTREGADA');
    expect(component.libro).not.toBeNull();
  });

  it('exporta usando los valores del libro cargado, no los filtros vivos', () => {
    const { component, reporteService } = crear();
    component.consultar();
    component.desde = '2026-01-01';

    component.exportarExcel();

    expect(reporteService.libroNotasVentaExcel).toHaveBeenCalledWith('2026-09-01', '2026-09-30', null);
    expect(component.exportando).toBeFalse();
  });

  it('muestra un error si falla la consulta', () => {
    const { component, reporteService } = crear();
    (reporteService.libroNotasVenta as jasmine.Spy).and.returnValue(throwError(() => ({ error: {} })));

    component.consultar();

    expect(component.error).toContain('No se pudo generar');
    expect(component.cargando).toBeFalse();
  });
});