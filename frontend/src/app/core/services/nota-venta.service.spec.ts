import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { NotaVentaService } from './nota-venta.service';
import { environment } from '../../../environments/environment';

describe('NotaVentaService', () => {
  let service: NotaVentaService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(NotaVentaService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('listar hace GET a /notas-venta con página y tamaño por defecto', () => {
    service.listar({}).subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/notas-venta`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('pagina')).toBe('0');
    expect(req.request.params.get('tamano')).toBe('10');
    req.flush({ contenido: [], total: 0 });
  });

  it('listar envía los filtros indicados', () => {
    service
      .listar({ estado: 'ENTREGADA', desde: '2026-09-01', hasta: '2026-09-30', q: 'ABC', sort: 'montoTotal', dir: 'asc' })
      .subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/notas-venta`);
    expect(req.request.params.get('estado')).toBe('ENTREGADA');
    expect(req.request.params.get('desde')).toBe('2026-09-01');
    expect(req.request.params.get('hasta')).toBe('2026-09-30');
    expect(req.request.params.get('q')).toBe('ABC');
    expect(req.request.params.get('sort')).toBe('montoTotal');
    expect(req.request.params.get('dir')).toBe('asc');
    req.flush({ contenido: [], total: 0 });
  });

  it('dashboard hace GET a /notas-venta/dashboard con el rango', () => {
    service.dashboard('2026-09-01', '2026-09-30').subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/notas-venta/dashboard`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('desde')).toBe('2026-09-01');
    expect(req.request.params.get('hasta')).toBe('2026-09-30');
    req.flush({
      desde: '2026-09-01',
      hasta: '2026-09-30',
      cantidad: 0,
      confirmadas: 0,
      enPreparacion: 0,
      pendientesEntrega: 0,
      entregadas: 0,
      facturadas: 0,
      canceladas: 0,
      montoTotalVendido: 0,
      porEstado: [],
    });
  });

  it('crear hace POST a /notas-venta', () => {
    const body = { clienteId: 5, formaPagoId: null, fechaEmision: '2026-09-22', fechaEntregaEstimada: null, exenta: false, descuento: 0, items: [] };
    service.crear(body).subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/notas-venta` && r.method === 'POST');
    expect(req.request.body).toEqual(body);
    req.flush({ id: 1, numero: 'NV-000001' });
  });

  it('crearDesdeCotizacion hace POST a /notas-venta/desde-cotizacion/{id}', () => {
    service.crearDesdeCotizacion(42).subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/notas-venta/desde-cotizacion/42`);
    expect(req.request.method).toBe('POST');
    req.flush({ id: 7, numero: 'NV-000001' });
  });

  it('confirmar hace POST a {id}/confirmar', () => {
    service.confirmar(9).subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/notas-venta/9/confirmar`);
    expect(req.request.method).toBe('POST');
    req.flush({ id: 9, numero: 'NV-000001', estado: 'CONFIRMADA' });
  });

  it('preparar hace POST a {id}/preparar', () => {
    service.preparar(9).subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/notas-venta/9/preparar`);
    expect(req.request.method).toBe('POST');
    req.flush({ id: 9, numero: 'NV-000001', estado: 'EN_PREPARACION' });
  });

  it('registrarEntrega hace POST a {id}/entregas con el cuerpo enviado', () => {
    const body = { observacion: 'Entrega parcial', lineas: [{ productoId: 3, cantidad: 2 }] };
    service.registrarEntrega(9, body).subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/notas-venta/9/entregas`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({ id: 9, numero: 'NV-000001', estado: 'PARCIALMENTE_ENTREGADA' });
  });

  it('cancelar hace POST a {id}/cancelar con el motivo', () => {
    service.cancelar(9, 'Cliente anuló').subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/notas-venta/9/cancelar`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ motivo: 'Cliente anuló' });
    req.flush({ id: 9, numero: 'NV-000001', estado: 'CANCELADA' });
  });

  it('duplicar hace POST a {id}/duplicar', () => {
    service.duplicar(9).subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/notas-venta/9/duplicar`);
    expect(req.request.method).toBe('POST');
    req.flush({ id: 10, numero: 'NV-000002', estado: 'BORRADOR' });
  });

  it('eliminar hace DELETE a {id}', () => {
    service.eliminar(9).subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/notas-venta/9` && r.method === 'DELETE');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('obtenerPdf hace GET con responseType blob', () => {
    service.obtenerPdf(9).subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/notas-venta/9/pdf`);
    expect(req.request.method).toBe('GET');
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob());
  });
});