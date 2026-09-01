import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { UsuarioService } from './usuario.service';
import { environment } from '../../../environments/environment';

describe('UsuarioService', () => {
  let service: UsuarioService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(UsuarioService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('listar hace GET a /usuarios', () => {
    service.listar().subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/usuarios`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('crear hace POST con el body recibido', () => {
    const request = { nombre: 'Vendedor Uno', rut: '1-9', email: 'v1@demo.cl', password: 'clave123', rol: 'VENDEDOR' as const };
    service.crear(request).subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/usuarios`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({});
  });

  it('desactivar hace DELETE al id correspondiente', () => {
    service.desactivar(5).subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/usuarios/5`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
