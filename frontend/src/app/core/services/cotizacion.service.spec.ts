import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { CotizacionService } from './cotizacion.service';
import { environment } from '../../../environments/environment';

describe('CotizacionService', () => {
  let service: CotizacionService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CotizacionService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('listar envía solo los filtros presentes', () => {
    service.listar({ estado: 'ENVIADA', pagina: 2, tamano: 25, sort: 'folio', dir: 'asc' }).subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/cotizaciones`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('estado')).toBe('ENVIADA');
    expect(req.request.params.get('pagina')).toBe('2');
    expect(req.request.params.get('tamano')).toBe('25');
    expect(req.request.params.get('sort')).toBe('folio');
    expect(req.request.params.has('clienteId')).toBeFalse();
    expect(req.request.params.has('q')).toBeFalse();
    req.flush({ contenido: [], total: 0 });
  });

  it('listar envía el texto de búsqueda cuando viene', () => {
    service.listar({ q: 'ABC' }).subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/cotizaciones`);
    expect(req.request.params.get('q')).toBe('ABC');
    req.flush({ contenido: [], total: 0 });
  });

  it('enviar hace POST al endpoint de la transición', () => {
    service.enviar(7).subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/cotizaciones/7/enviar`);
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('rechazar envía el motivo en el cuerpo', () => {
    service.rechazar(7, 'Precio alto').subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/cotizaciones/7/rechazar`);
    expect(req.request.body).toEqual({ motivo: 'Precio alto' });
    req.flush({});
  });

  it('obtenerPdf pide el blob del PDF', () => {
    service.obtenerPdf(7).subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/cotizaciones/7/pdf`);
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob());
  });
});
