import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { environment } from '../../../environments/environment';
import { NotaCreditoRequest, NotaCreditoService } from './nota-credito.service';

describe('NotaCreditoService', () => {
  let service: NotaCreditoService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(NotaCreditoService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('listar hace GET a /notas-credito con página y tamaño por defecto', () => {
    service.listar({}).subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/notas-credito`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('pagina')).toBe('0');
    expect(req.request.params.get('tamano')).toBe('10');
    req.flush({ contenido: [], total: 0 });
  });

  it('listar envía los cinco filtros de la especificación', () => {
    service
      .listar({
        estado: 'EMITIDA',
        clienteId: 5,
        tipoCorreccion: 'CORRIGE_MONTO',
        docAsociadoTipo: 'FACTURA',
        desde: '2026-09-01',
        hasta: '2026-09-30',
      })
      .subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/notas-credito`);
    expect(req.request.params.get('estado')).toBe('EMITIDA');
    expect(req.request.params.get('clienteId')).toBe('5');
    expect(req.request.params.get('tipoCorreccion')).toBe('CORRIGE_MONTO');
    expect(req.request.params.get('docAsociadoTipo')).toBe('FACTURA');
    expect(req.request.params.get('desde')).toBe('2026-09-01');
    expect(req.request.params.get('hasta')).toBe('2026-09-30');
    req.flush({ contenido: [], total: 0 });
  });

  it('dashboard hace GET a /notas-credito/dashboard con el rango', () => {
    service.dashboard('2026-09-01', '2026-09-30').subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/notas-credito/dashboard`);
    expect(req.request.params.get('desde')).toBe('2026-09-01');
    expect(req.request.params.get('hasta')).toBe('2026-09-30');
    req.flush({});
  });

  it('documentosAsociables hace GET con el cliente y la búsqueda', () => {
    service.documentosAsociables(5, '1042').subscribe();

    const req = httpMock.expectOne(
      (r) => r.url === `${environment.apiUrl}/notas-credito/documentos-asociables`,
    );
    expect(req.request.params.get('clienteId')).toBe('5');
    expect(req.request.params.get('q')).toBe('1042');
    req.flush([]);
  });

  it('lineasDocumento excluye la propia nota al editar un borrador', () => {
    service.lineasDocumento(50, 100).subscribe();

    const req = httpMock.expectOne(
      (r) => r.url === `${environment.apiUrl}/notas-credito/ventas/50/lineas`,
    );
    expect(req.request.params.get('excluyendoNotaCreditoId')).toBe('100');
    req.flush([]);
  });

  it('lineasDocumento no manda el parámetro cuando se está creando', () => {
    service.lineasDocumento(50).subscribe();

    const req = httpMock.expectOne(
      (r) => r.url === `${environment.apiUrl}/notas-credito/ventas/50/lineas`,
    );
    expect(req.request.params.has('excluyendoNotaCreditoId')).toBeFalse();
    req.flush([]);
  });

  it('crear hace POST a /notas-credito sin enviar id', () => {
    const request: NotaCreditoRequest = {
      ventaId: 50,
      tipoCorreccion: 'CORRIGE_MONTO',
      fecha: '2026-09-23',
      docAsociadoRazon: 'Devolución parcial',
      descuento: 0,
      items: [
        {
          productoId: 10,
          ventaDetalleId: 301,
          cantidad: 2,
          precioUnitario: 1000,
          descuento: 0,
          recuperaInventario: true,
        },
      ],
    };

    service.crear(request).subscribe();

    const req = httpMock.expectOne(
      (r) => r.url === `${environment.apiUrl}/notas-credito` && r.method === 'POST',
    );
    expect(req.request.body).toEqual(request);
    expect(req.request.body.id).toBeUndefined();
    req.flush({});
  });

  it('emitir hace POST a /notas-credito/{id}/emitir', () => {
    service.emitir(9).subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/notas-credito/9/emitir`);
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('anular hace POST a /notas-credito/{id}/anular con el motivo', () => {
    service.anular(9, 'Devolución rechazada').subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/notas-credito/9/anular`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ motivo: 'Devolución rechazada' });
    req.flush({});
  });

  it('eliminar hace DELETE a /notas-credito/{id}', () => {
    service.eliminar(9).subscribe();

    const req = httpMock.expectOne(
      (r) => r.url === `${environment.apiUrl}/notas-credito/9` && r.method === 'DELETE',
    );
    req.flush(null);
  });

  it('obtenerPdf pide el blob a /notas-credito/{id}/pdf', () => {
    service.obtenerPdf(9).subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/notas-credito/9/pdf`);
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob());
  });
});
