import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ClienteService } from './cliente.service';
import { environment } from '../../../environments/environment';

describe('ClienteService', () => {
  let service: ClienteService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ClienteService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('listar hace GET a /clientes', () => {
    service.listar().subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/clientes`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('crear hace POST con el body recibido', () => {
    const request = { nombre: 'Cliente Uno', rut: '1-9', email: 'c1@demo.cl' };
    service.crear(request).subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/clientes`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({});
  });

  it('eliminar hace DELETE al id correspondiente', () => {
    service.eliminar(3).subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/clientes/3`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
