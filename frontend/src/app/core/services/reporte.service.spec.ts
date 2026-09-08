import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ReporteService } from './reporte.service';
import { environment } from '../../../environments/environment';

describe('ReporteService', () => {
  let service: ReporteService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ReporteService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('libroVentas hace GET a /reportes/libro-ventas con los parámetros de fecha', () => {
    service.libroVentas('2026-09-01', '2026-09-30').subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/reportes/libro-ventas`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('desde')).toBe('2026-09-01');
    expect(req.request.params.get('hasta')).toBe('2026-09-30');
    req.flush({ desde: '2026-09-01', hasta: '2026-09-30', filas: [], subtotales: [], totalGeneral: null });
  });

  it('libroVentasExcel hace GET con responseType blob y los parámetros de fecha', () => {
    service.libroVentasExcel('2026-09-01', '2026-09-30').subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/reportes/libro-ventas/excel`);
    expect(req.request.method).toBe('GET');
    expect(req.request.responseType).toBe('blob');
    expect(req.request.params.get('desde')).toBe('2026-09-01');
    req.flush(new Blob());
  });

  it('libroVentas no envía el parámetro tipoDocumento cuando no se especifica', () => {
    service.libroVentas('2026-09-01', '2026-09-30').subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/reportes/libro-ventas`);
    expect(req.request.params.has('tipoDocumento')).toBeFalse();
    req.flush({ desde: '2026-09-01', hasta: '2026-09-30', tipoDocumento: null, filas: [], subtotales: [], totalGeneral: null });
  });

  it('libroVentas envía el parámetro tipoDocumento cuando se especifica', () => {
    service.libroVentas('2026-09-01', '2026-09-30', 'Factura').subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/reportes/libro-ventas`);
    expect(req.request.params.get('tipoDocumento')).toBe('Factura');
    req.flush({ desde: '2026-09-01', hasta: '2026-09-30', tipoDocumento: 'Factura', filas: [], subtotales: [], totalGeneral: null });
  });

  it('libroVentasExcel envía el parámetro tipoDocumento cuando se especifica', () => {
    service.libroVentasExcel('2026-09-01', '2026-09-30', 'Boleta').subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/reportes/libro-ventas/excel`);
    expect(req.request.params.get('tipoDocumento')).toBe('Boleta');
    req.flush(new Blob());
  });
});
