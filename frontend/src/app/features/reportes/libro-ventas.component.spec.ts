import { fakeAsync, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { LibroVentasComponent } from './libro-ventas.component';
import { ReporteService } from '../../core/services/reporte.service';
import { LibroVentasResponse } from '../../core/models/models';

function libroDeEjemplo(overrides: Partial<LibroVentasResponse> = {}): LibroVentasResponse {
  return {
    desde: '2026-09-01',
    hasta: '2026-09-30',
    tipoDocumento: null,
    busqueda: null,
    filas: [],
    subtotales: [],
    totalGeneral: {
      tipoDocumento: 'Total',
      cantidad: 0,
      montoNetoAfecto: 0,
      montoNetoExento: 0,
      montoIva: 0,
      montoTotal: 0,
    },
    totalFilas: 0,
    pagina: 0,
    tamano: 10,
    ...overrides,
  };
}

describe('LibroVentasComponent', () => {
  function crear() {
    const reporteServiceStub = {
      libroVentas: jasmine.createSpy('libroVentas').and.returnValue(of(libroDeEjemplo())),
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

  it('consulta el libro cuando el rango es válido, sin filtro de tipo ni búsqueda, desde la página 0', () => {
    const { component, reporteServiceStub } = crear();
    component.desde = '2026-09-01';
    component.hasta = '2026-09-30';

    component.consultar();

    expect(reporteServiceStub.libroVentas).toHaveBeenCalledWith('2026-09-01', '2026-09-30', null, '', 0, 10);
    expect(component.libro).not.toBeNull();
    expect(component.error).toBe('');
  });

  it('consulta el libro pasando el tipo de documento seleccionado', () => {
    const { component, reporteServiceStub } = crear();
    component.desde = '2026-09-01';
    component.hasta = '2026-09-30';
    component.tipoDocumento = 'Factura';

    component.consultar();

    expect(reporteServiceStub.libroVentas).toHaveBeenCalledWith('2026-09-01', '2026-09-30', 'Factura', '', 0, 10);
  });

  it('consultar() resetea a la página 0 aunque se estuviera viendo otra página', () => {
    const { component } = crear();
    component.paginaActual = 3;

    component.consultar();

    expect(component.paginaActual).toBe(0);
  });

  it('onPageChange() actualiza página/tamaño y consulta sin resetear a la página 0', () => {
    const { component, reporteServiceStub } = crear();
    component.desde = '2026-09-01';
    component.hasta = '2026-09-30';
    (reporteServiceStub.libroVentas as jasmine.Spy).calls.reset();

    component.onPageChange({ pageIndex: 2, pageSize: 25, length: 100 });

    expect(component.paginaActual).toBe(2);
    expect(component.tamanoPagina).toBe(25);
    expect(reporteServiceStub.libroVentas).toHaveBeenCalledWith('2026-09-01', '2026-09-30', null, '', 2, 25);
  });

  it('onBusquedaChange() consulta 300ms después de dejar de escribir, reseteando a la página 0', fakeAsync(() => {
    const { component, reporteServiceStub } = crear();
    component.ngOnInit();
    component.desde = '2026-09-01';
    component.hasta = '2026-09-30';
    component.paginaActual = 3;
    component.busqueda = 'andes';
    (reporteServiceStub.libroVentas as jasmine.Spy).calls.reset();

    component.onBusquedaChange();
    tick(299);
    expect(reporteServiceStub.libroVentas).not.toHaveBeenCalled();

    tick(1);
    expect(component.paginaActual).toBe(0);
    expect(reporteServiceStub.libroVentas).toHaveBeenCalledWith('2026-09-01', '2026-09-30', null, 'andes', 0, 10);

    component.ngOnDestroy();
  }));

  it('onBusquedaChange() no consulta con fechas inválidas', fakeAsync(() => {
    const { component, reporteServiceStub } = crear();
    component.ngOnInit();
    component.desde = '2026-09-30';
    component.hasta = '2026-09-01';
    (reporteServiceStub.libroVentas as jasmine.Spy).calls.reset();

    component.onBusquedaChange();
    tick(300);

    expect(reporteServiceStub.libroVentas).not.toHaveBeenCalled();

    component.ngOnDestroy();
  }));

  it('exportarExcel() exporta el período, tipo y búsqueda del libro cargado, no los de los inputs en vivo', () => {
    const { component, reporteServiceStub } = crear();
    component.desde = '2026-10-01';
    component.hasta = '2026-10-31';
    component.tipoDocumento = 'Boleta';
    component.busqueda = 'otra cosa';
    component.libro = libroDeEjemplo({
      desde: '2026-09-01',
      hasta: '2026-09-30',
      tipoDocumento: 'Factura',
      busqueda: 'andes',
    });
    spyOn(URL, 'createObjectURL').and.returnValue('blob:fake');
    spyOn(URL, 'revokeObjectURL');

    expect(component.exportando).toBeFalse();
    component.exportarExcel();

    expect(reporteServiceStub.libroVentasExcel).toHaveBeenCalledWith('2026-09-01', '2026-09-30', 'Factura', 'andes');
    expect(component.exportando).toBeFalse();
  });

  it('exportarExcel() maneja errores del servicio', () => {
    const { component, reporteServiceStub } = crear();
    component.libro = libroDeEjemplo();
    (reporteServiceStub.libroVentasExcel as jasmine.Spy).and.returnValue(throwError(() => new Error('fail')));

    component.exportarExcel();

    expect(component.error).toBe('No se pudo exportar el Excel.');
    expect(component.exportando).toBeFalse();
  });
});
