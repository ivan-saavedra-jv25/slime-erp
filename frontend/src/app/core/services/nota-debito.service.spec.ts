import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { environment } from '../../../environments/environment';
import { NotaDebitoRequest, NotaDebitoService } from './nota-debito.service';

describe('NotaDebitoService', () => {
  let service: NotaDebitoService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(NotaDebitoService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('listar hace GET a /notas-debito con página y tamaño por defecto', () => {
    service.listar({}).subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/notas-debito`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('pagina')).toBe('0');
    expect(req.request.params.get('tamano')).toBe('10');
    req.flush({ contenido: [], total: 0 });
  });

  it('listar envía los filtros de la especificación', () => {
    service
      .listar({
        estado: 'EMITIDA',
        clienteId: 5,
        tipoReversion: 'REVIERTE_MONTO',
        notaCreditoId: 40,
        desde: '2026-09-01',
        hasta: '2026-09-30',
      })
      .subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/notas-debito`);
    expect(req.request.params.get('estado')).toBe('EMITIDA');
    expect(req.request.params.get('clienteId')).toBe('5');
    expect(req.request.params.get('tipoReversion')).toBe('REVIERTE_MONTO');
    expect(req.request.params.get('notaCreditoId')).toBe('40');
    expect(req.request.params.get('desde')).toBe('2026-09-01');
    expect(req.request.params.get('hasta')).toBe('2026-09-30');
    req.flush({ contenido: [], total: 0 });
  });

  it('dashboard hace GET a /notas-debito/dashboard con el rango', () => {
    service.dashboard('2026-09-01', '2026-09-30').subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/notas-debito/dashboard`);
    expect(req.request.params.get('desde')).toBe('2026-09-01');
    expect(req.request.params.get('hasta')).toBe('2026-09-30');
    req.flush({});
  });

  it('notasCreditoAsociables hace GET con el cliente y la búsqueda', () => {
    service.notasCreditoAsociables(5, 'NC-000012').subscribe();

    const req = httpMock.expectOne(
      (r) => r.url === `${environment.apiUrl}/notas-debito/notas-credito-asociables`,
    );
    expect(req.request.params.get('clienteId')).toBe('5');
    expect(req.request.params.get('q')).toBe('NC-000012');
    req.flush([]);
  });

  it('lineasNotaCredito excluye la propia nota al editar un borrador', () => {
    service.lineasNotaCredito(50, 100).subscribe();

    const req = httpMock.expectOne(
      (r) => r.url === `${environment.apiUrl}/notas-debito/notas-credito/50/lineas`,
    );
    expect(req.request.params.get('excluyendoNotaDebitoId')).toBe('100');
    req.flush([]);
  });

  it('lineasNotaCredito no manda el parámetro cuando se está creando', () => {
    service.lineasNotaCredito(50).subscribe();

    const req = httpMock.expectOne(
      (r) => r.url === `${environment.apiUrl}/notas-debito/notas-credito/50/lineas`,
    );
    expect(req.request.params.has('excluyendoNotaDebitoId')).toBeFalse();
    req.flush([]);
  });

  it('crear hace POST a /notas-debito sin enviar id', () => {
    const request: NotaDebitoRequest = {
      notaCreditoId: 50,
      tipoReversion: 'REVIERTE_MONTO',
      fecha: '2026-09-23',
      ncRazon: 'Moneda extra al cobrar',
      descuento: 0,
      items: [
        {
          productoId: 10,
          notaCreditoDetalleId: 301,
          cantidad: 2,
          precioUnitario: 1000,
          descuento: 0,
          revierteInventario: true,
        },
      ],
    };

    service.crear(request).subscribe();

    const req = httpMock.expectOne(
      (r) => r.url === `${environment.apiUrl}/notas-debito` && r.method === 'POST',
    );
    expect(req.request.body).toEqual(request);
    expect(req.request.body.id).toBeUndefined();
    req.flush({});
  });

  it('emitir hace POST a /notas-debito/{id}/emitir', () => {
    service.emitir(9).subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/notas-debito/9/emitir`);
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('anular hace POST a /notas-debito/{id}/anular con el motivo', () => {
    service.anular(9, 'Reversión rechazada').subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/notas-debito/9/anular`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ motivo: 'Reversión rechazada' });
    req.flush({});
  });

  it('eliminar hace DELETE a /notas-debito/{id}', () => {
    service.eliminar(9).subscribe();

    const req = httpMock.expectOne(
      (r) => r.url === `${environment.apiUrl}/notas-debito/9` && r.method === 'DELETE',
    );
    req.flush(null);
  });

  it('cadenaDocumentos hace GET a /notas-debito/{id}/cadena', () => {
    service.cadenaDocumentos(9).subscribe((cadena) => {
      expect(cadena).toEqual([
        { tipo: 'NOTA_CREDITO', documentoId: 90, numero: 'NC-000090', fecha: '2026-09-15', montoTotal: 12852 },
      ]);
    });

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/notas-debito/9/cadena`);
    expect(req.request.method).toBe('GET');
    req.flush([{ tipo: 'NOTA_CREDITO', documentoId: 90, numero: 'NC-000090', fecha: '2026-09-15', montoTotal: 12852 }]);
  });

  it('obtenerPdf pide el blob a /notas-debito/{id}/pdf', () => {
    service.obtenerPdf(9).subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/notas-debito/9/pdf`);
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob());
  });
});