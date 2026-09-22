import { of } from 'rxjs';
import { LibroComprasComponent } from './libro-compras.component';
import { ReporteService } from '../../core/services/reporte.service';
import { LibroComprasResponse } from '../../core/models/models';

describe('LibroComprasComponent', () => {
  function crear(libro: LibroComprasResponse) {
    const reporteServiceStub = {
      libroCompras: jasmine.createSpy('libroCompras').and.returnValue(of(libro)),
      libroComprasExcel: jasmine.createSpy('libroComprasExcel'),
    } as unknown as ReporteService;
    return { component: new LibroComprasComponent(reporteServiceStub), reporteServiceStub };
  }

  function libroVacio(): LibroComprasResponse {
    return {
      desde: '2026-09-01',
      hasta: '2026-09-30',
      filas: [],
      resumen: { cantidadCompras: 0, montoNeto: 0, montoIva: 0, montoTotal: 0 },
      evolucion: [],
    };
  }

  it('no consulta si "desde" es posterior a "hasta"', () => {
    const { component, reporteServiceStub } = crear(libroVacio());
    component.desde = '2026-09-30';
    component.hasta = '2026-09-01';

    component.consultar();

    expect(reporteServiceStub.libroCompras).not.toHaveBeenCalled();
    expect(component.error).toContain('no puede ser posterior');
  });

  it('consulta el libro cuando el rango es válido', () => {
    const { component, reporteServiceStub } = crear(libroVacio());
    component.desde = '2026-09-01';
    component.hasta = '2026-09-30';

    component.consultar();

    expect(reporteServiceStub.libroCompras).toHaveBeenCalledWith('2026-09-01', '2026-09-30');
    expect(component.libro).not.toBeNull();
    expect(component.error).toBe('');
  });

  it('calcula el proveedor principal como el de mayor monto total en el período', () => {
    const libro: LibroComprasResponse = {
      desde: '2026-09-01',
      hasta: '2026-09-30',
      filas: [
        { compraId: 1, fecha: '2026-09-01T10:00:00', numeroDocumento: null, proveedorNombre: 'Proveedor A', proveedorRut: null, cantidadItems: 1, montoNeto: 100, montoIva: 19, montoTotal: 119, estadoPago: 'En deuda' },
        { compraId: 2, fecha: '2026-09-02T10:00:00', numeroDocumento: null, proveedorNombre: 'Proveedor B', proveedorRut: null, cantidadItems: 1, montoNeto: 500, montoIva: 95, montoTotal: 595, estadoPago: 'Pagado' },
        { compraId: 3, fecha: '2026-09-03T10:00:00', numeroDocumento: null, proveedorNombre: 'Proveedor A', proveedorRut: null, cantidadItems: 1, montoNeto: 50, montoIva: 9.5, montoTotal: 59.5, estadoPago: 'Parcial' },
      ],
      resumen: { cantidadCompras: 3, montoNeto: 650, montoIva: 123.5, montoTotal: 773.5 },
      evolucion: [],
    };
    const { component } = crear(libro);

    component.consultar();

    expect(component.proveedorPrincipal).toBe('Proveedor B');
  });

  it('devuelve guion como proveedor principal cuando no hay filas', () => {
    const { component } = crear(libroVacio());

    component.consultar();

    expect(component.proveedorPrincipal).toBe('—');
  });
});
