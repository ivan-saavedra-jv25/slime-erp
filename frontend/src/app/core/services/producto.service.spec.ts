import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ProductoService } from './producto.service';
import { environment } from '../../../environments/environment';

describe('ProductoService', () => {
  let service: ProductoService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ProductoService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('listar hace GET a /productos', () => {
    service.listar().subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/productos`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('crear hace POST con el body recibido', () => {
    const request = { sku: 'SKU-1', nombre: 'Producto Uno', precioVenta: 1000 };
    service.crear(request).subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/productos`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({});
  });

  it('eliminar hace DELETE al id correspondiente', () => {
    service.eliminar(9).subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/productos/9`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
