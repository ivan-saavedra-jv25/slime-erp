import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { EmpresaAdminService } from './empresa-admin.service';
import { environment } from '../../../environments/environment';

describe('EmpresaAdminService', () => {
  let service: EmpresaAdminService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(EmpresaAdminService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('listar hace GET con parámetros de filtro a /admin/empresas', () => {
    service.listar({ page: 0, limit: 10, id: 7, estado: 'ACTIVE', razonSocial: 'Demo' }).subscribe();
    const req = httpMock.expectOne(
      `${environment.adminApiUrl}/admin/empresas?page=0&limit=10&id=7&razonSocial=Demo&estado=ACTIVE`
    );
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 });
  });

  it('detalle hace GET a /admin/empresas/{id}', () => {
    service.detalle(5).subscribe();
    const req = httpMock.expectOne(`${environment.adminApiUrl}/admin/empresas/5`);
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('crear hace POST con el body recibido', () => {
    const request = { nombre: 'Empresa Nueva', rut: '76.111.222-3', adminNombre: 'Admin Uno', adminRut: '1-9', adminEmail: 'a1@demo.cl', adminPassword: 'clave123' };
    service.crear(request).subscribe();
    const req = httpMock.expectOne(`${environment.adminApiUrl}/admin/empresas`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({});
  });

  it('activar hace PATCH a /admin/empresas/{id}/activar', () => {
    service.activar(5).subscribe();
    const req = httpMock.expectOne(`${environment.adminApiUrl}/admin/empresas/5/activar`);
    expect(req.request.method).toBe('PATCH');
    req.flush({});
  });

  it('desactivar hace PATCH a /admin/empresas/{id}/desactivar', () => {
    service.desactivar(5).subscribe();
    const req = httpMock.expectOne(`${environment.adminApiUrl}/admin/empresas/5/desactivar`);
    expect(req.request.method).toBe('PATCH');
    req.flush({});
  });

  it('cambiarEstado hace PATCH a /admin/empresas/{id}/estado', () => {
    service.cambiarEstado(5, { estado: 'SUSPENDED', motivo: 'Impago' }).subscribe();
    const req = httpMock.expectOne(`${environment.adminApiUrl}/admin/empresas/5/estado`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ estado: 'SUSPENDED', motivo: 'Impago' });
    req.flush({});
  });
});