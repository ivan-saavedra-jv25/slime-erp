import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { EmpresaService } from './empresa.service';
import { environment } from '../../../environments/environment';

describe('EmpresaService', () => {
  let service: EmpresaService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(EmpresaService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('listar hace GET a /admin/empresas', () => {
    service.listar().subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/admin/empresas`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('crear hace POST con el body recibido', () => {
    const request = { nombre: 'Empresa Nueva', rut: '76.111.222-3', adminNombre: 'Admin Uno', adminRut: '1-9', adminEmail: 'a1@demo.cl', adminPassword: 'clave123' };
    service.crear(request).subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/admin/empresas`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({});
  });

  it('activar hace PATCH a /admin/empresas/{id}/activar', () => {
    service.activar(5).subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/admin/empresas/5/activar`);
    expect(req.request.method).toBe('PATCH');
    req.flush({});
  });

  it('desactivar hace PATCH a /admin/empresas/{id}/desactivar', () => {
    service.desactivar(5).subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/admin/empresas/5/desactivar`);
    expect(req.request.method).toBe('PATCH');
    req.flush({});
  });
});
