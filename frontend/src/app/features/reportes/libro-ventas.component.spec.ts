import { of, throwError } from 'rxjs';
import { LibroVentasComponent } from './libro-ventas.component';
import { ReporteService } from '../../core/services/reporte.service';
import { LibroVentasResponse } from '../../core/models/models';

describe('LibroVentasComponent', () => {
  function crear() {
    const libroVacio: LibroVentasResponse = {
      desde: '2026-09-01',
      hasta: '2026-09-30',
      filas: [],
      subtotales: [],
      totalGeneral: { tipoDocumento: 'Total', cantidad: 0, montoNeto: 0, montoIva: 0, montoTotal: 0 },
    };
    const reporteServiceStub = {
      libroVentas: jasmine.createSpy('libroVentas').and.returnValue(of(libroVacio)),
      libroVentasExcel: jasmine.createSpy('libroVentasExcel').and.returnValue(of(new Blob())),
    } as unknown as ReporteService;
    return { component: new LibroVentasComponent(reporteServiceStub), reporteServiceStub };
  }

  it('no consulta si "desde" es posterior a "hasta"', () => {
    const { component, reporteServiceStub } = crear();
    component.desde = '2026-09-30';
    component.hasta = '2026-09-01';

    component.consultar();

    expect(reporteServiceStub.libroVentas).not.toHaveBeenCalled();
    expect(component.error).toContain('no puede ser posterior');
  });

  it('consulta el libro cuando el rango es válido', () => {
    const { component, reporteServiceStub } = crear();
    component.desde = '2026-09-01';
    component.hasta = '2026-09-30';

    component.consultar();

    expect(reporteServiceStub.libroVentas).toHaveBeenCalledWith('2026-09-01', '2026-09-30');
    expect(component.libro).not.toBeNull();
    expect(component.error).toBe('');
  });

  it('exportarExcel() exporta el período del libro cargado, no el de los inputs en vivo', () => {
    const { component, reporteServiceStub } = crear();
    component.desde = '2026-10-01';
    component.hasta = '2026-10-31';
    component.libro = {
      desde: '2026-09-01',
      hasta: '2026-09-30',
      filas: [],
      subtotales: [],
      totalGeneral: { tipoDocumento: 'Total', cantidad: 0, montoNeto: 0, montoIva: 0, montoTotal: 0 },
    };
    spyOn(URL, 'createObjectURL').and.returnValue('blob:fake');
    spyOn(URL, 'revokeObjectURL');

    expect(component.exportando).toBeFalse();
    component.exportarExcel();

    expect(reporteServiceStub.libroVentasExcel).toHaveBeenCalledWith('2026-09-01', '2026-09-30');
    expect(component.exportando).toBeFalse();
  });

  it('exportarExcel() maneja errores del servicio', () => {
    const { component, reporteServiceStub } = crear();
    component.libro = {
      desde: '2026-09-01',
      hasta: '2026-09-30',
      filas: [],
      subtotales: [],
      totalGeneral: { tipoDocumento: 'Total', cantidad: 0, montoNeto: 0, montoIva: 0, montoTotal: 0 },
    };
    (reporteServiceStub.libroVentasExcel as jasmine.Spy).and.returnValue(throwError(() => new Error('fail')));

    component.exportarExcel();

    expect(component.error).toBe('No se pudo exportar el Excel.');
    expect(component.exportando).toBeFalse();
  });
});
