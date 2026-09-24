import { ActivatedRoute, Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { NotaDebitoDetalleComponent } from './nota-debito-detalle.component';
import { NotaDebitoService } from '../../core/services/nota-debito.service';
import { AuthService } from '../../core/services/auth.service';
import { MovimientoService } from '../../core/services/movimiento.service';
import { MovimientoDetalleDialogComponent } from '../movimientos/movimiento-detalle-dialog.component';
import { NotaCreditoAsociadaDialogComponent } from './nota-credito-asociada-dialog.component';
import { DocumentoAsociadoDialogComponent } from '../notas-credito/documento-asociado-dialog.component';
import { EslabonCadenaDocumento, EstadoNotaDebito, NotaDebito } from '../../core/models/models';

function notaEn(estado: EstadoNotaDebito): NotaDebito {
  return {
    id: 9,
    folio: 12,
    numero: 'ND-000012',
    estado,
    tipoReversion: 'REVIERTE_MONTO',
    fecha: '2026-09-23',
    clienteId: 5,
    clienteNombre: 'Empresa ABC SpA',
    clienteRazonSocial: null,
    clienteRut: '76.111.222-3',
    clienteDireccion: null,
    clienteEmail: null,
    clienteTelefono: null,
    usuarioId: 7,
    usuarioNombre: 'Vendedor Demo',
    documentoAsociado: {
      notaCreditoId: 90,
      numero: 'NC-000090',
      fecha: '2026-09-15',
      ncFolio: 90,
      ncDocAsociadoTipo: 'FACTURA',
      montoTotal: 12852,
      montoDisponible: 12340,
      razon: 'Devolución rechazada',
    },
    motivo: 'Devolución no autorizada',
    observaciones: null,
    textoCorreccion: null,
    bodegaId: 3,
    bodegaNombre: 'Bodega Central',
    exenta: false,
    moneda: 'CLP',
    descuento: 0,
    montoSubtotal: 2000,
    montoDescuento: 0,
    montoNeto: 2000,
    montoIva: 380,
    montoTotal: 2380,
    fechaEmision: estado === 'BORRADOR' ? null : '2026-09-23T10:00:00',
    fechaAnulacion: estado === 'ANULADA' ? '2026-09-24T09:30:00' : null,
    impactoInventario: 'PARCIAL',
    lineas: [],
    movimientosInventario: [],
    historial: [],
  };
}

function crear(estado: EstadoNotaDebito, dialogResult: unknown = 'Motivo de prueba') {
  const notaDebitoService = {
    obtener: jasmine.createSpy('obtener').and.returnValue(of(notaEn(estado))),
    cadenaDocumentos: jasmine.createSpy('cadenaDocumentos').and.returnValue(of([])),
    emitir: jasmine.createSpy('emitir').and.returnValue(of(notaEn('EMITIDA'))),
    anular: jasmine.createSpy('anular').and.returnValue(of(notaEn('ANULADA'))),
    eliminar: jasmine.createSpy('eliminar').and.returnValue(of(void 0)),
    obtenerPdf: jasmine.createSpy('obtenerPdf').and.returnValue(of(new Blob())),
  } as unknown as NotaDebitoService;
  const route = { paramMap: of({ get: () => '9' }) } as unknown as ActivatedRoute;
  const router = { navigate: jasmine.createSpy('navigate') } as unknown as Router;
  const dialog = {
    open: jasmine.createSpy('open').and.returnValue({ afterClosed: () => of(dialogResult) }),
  } as unknown as MatDialog;
  const auth = { tienePermiso: () => true } as unknown as AuthService;

  const movimientoService = {
    detalle: jasmine.createSpy('detalle').and.returnValue(of({ id: 900, tipo: 'ENTRADA', items: [] })),
  } as unknown as MovimientoService;

  const component = new NotaDebitoDetalleComponent(
    notaDebitoService, route, router, movimientoService, dialog, auth);
  component.ngOnInit();
  return { component, notaDebitoService, router, dialog, movimientoService };
}

describe('NotaDebitoDetalleComponent', () => {
  it('carga la nota al iniciar', () => {
    const { component, notaDebitoService } = crear('BORRADOR');

    expect(notaDebitoService.obtener).toHaveBeenCalledWith(9);
    expect(component.nota?.numero).toBe('ND-000012');
    expect(component.cargando).toBeFalse();
  });

  it('en BORRADOR ofrece editar, emitir y eliminar, pero no anular', () => {
    const { component } = crear('BORRADOR');

    expect(component.puedeEditar).toBeTrue();
    expect(component.puedeEmitir).toBeTrue();
    expect(component.puedeAnular).toBeFalse();
  });

  it('en EMITIDA solo ofrece anular', () => {
    const { component } = crear('EMITIDA');

    expect(component.puedeEditar).toBeFalse();
    expect(component.puedeEmitir).toBeFalse();
    expect(component.puedeAnular).toBeTrue();
  });

  it('en ANULADA no ofrece ninguna acción de transición', () => {
    const { component } = crear('ANULADA');

    expect(component.puedeEditar).toBeFalse();
    expect(component.puedeEmitir).toBeFalse();
    expect(component.puedeAnular).toBeFalse();
  });

  it('emitir pide confirmación antes de llamar al servicio', () => {
    const { component, notaDebitoService } = crear('BORRADOR');

    component.abrirConfirmacionEmision();
    expect(component.confirmandoEmision).toBeTrue();
    expect(notaDebitoService.emitir).not.toHaveBeenCalled();

    component.emitir();
    expect(notaDebitoService.emitir).toHaveBeenCalledWith(9);
    expect(component.nota?.estado).toBe('EMITIDA');
    expect(component.confirmandoEmision).toBeFalse();
  });

  it('anular pasa el motivo devuelto por el diálogo', () => {
    const { component, notaDebitoService } = crear('EMITIDA', 'Reversión cancelada');

    component.anular();

    expect(notaDebitoService.anular).toHaveBeenCalledWith(9, 'Reversión cancelada');
    expect(component.nota?.estado).toBe('ANULADA');
  });

  it('anular no llama al servicio si el diálogo se cierra sin motivo', () => {
    const { component, notaDebitoService } = crear('EMITIDA', null);

    component.anular();

    expect(notaDebitoService.anular).not.toHaveBeenCalled();
  });

  it('eliminar vuelve al listado', () => {
    const { component, notaDebitoService, router } = crear('BORRADOR');

    component.eliminar();

    expect(notaDebitoService.eliminar).toHaveBeenCalledWith(9);
    expect(router.navigate).toHaveBeenCalledWith(['/notas-debito']);
  });

  it('el nodo de la NC abre su detalle con los datos de la nota de crédito', () => {
    const { component, dialog } = crear('EMITIDA');

    component.verNotaCreditoAsociada();

    expect(dialog.open).toHaveBeenCalledWith(
      NotaCreditoAsociadaDialogComponent,
      jasmine.objectContaining({
        data: jasmine.objectContaining({ notaCreditoId: 90, numero: 'NC-000090', folio: 90 }),
      }),
    );
  });

  it('el nodo de inventario abre el movimiento del modulo de Inventario', () => {
    const { component, dialog, movimientoService } = crear('EMITIDA');
    component.nota!.movimientosInventario = [
      {
        movimientoInventarioId: 105,
        headerId: 900,
        tipo: 'REVERSION',
        fecha: '2026-09-23T10:00:00',
        productoId: 10,
        producto: 'Bidon 20L',
        cantidad: 3,
        bodegaId: 2,
        bodega: 'Bodega Central',
      },
    ];

    expect(component.headerMovimientos).toBe(900);
    component.verMovimiento(component.headerMovimientos);

    expect(movimientoService.detalle).toHaveBeenCalledWith(900);
    expect(dialog.open).toHaveBeenCalledWith(
      MovimientoDetalleDialogComponent,
      jasmine.objectContaining({ data: jasmine.objectContaining({ id: 900 }) }),
    );
  });

  it('un movimiento sin cabecera no abre nada', () => {
    const { component, movimientoService } = crear('EMITIDA');

    component.verMovimiento(null);

    expect(movimientoService.detalle).not.toHaveBeenCalled();
  });

  it('muestra el mensaje del backend cuando una acción falla', () => {
    const notaDebitoService = {
      obtener: jasmine.createSpy('obtener').and.returnValue(of(notaEn('BORRADOR'))),
      cadenaDocumentos: jasmine.createSpy('cadenaDocumentos').and.returnValue(of([])),
      emitir: jasmine.createSpy('emitir').and.returnValue(
        throwError(() => ({
          error: { error: 'No se puede revertir 5 de "Bidón 20L": solo hay 2 disponibles' },
        })),
      ),
    } as unknown as NotaDebitoService;
    const route = { paramMap: of({ get: () => '9' }) } as unknown as ActivatedRoute;
    const router = { navigate: jasmine.createSpy('navigate') } as unknown as Router;
    const dialog = { open: jasmine.createSpy('open') } as unknown as MatDialog;
    const auth = { tienePermiso: () => true } as unknown as AuthService;
    const movimientoService = { detalle: jasmine.createSpy('detalle') } as unknown as MovimientoService;
    const component = new NotaDebitoDetalleComponent(
      notaDebitoService, route, router, movimientoService, dialog, auth);
    component.ngOnInit();

    component.emitir();

    expect(component.error).toContain('Bidón 20L');
    expect(component.procesando).toBeFalse();
  });

  it('oculta el detalle en una reversión de texto', () => {
    const { component } = crear('EMITIDA');
    component.nota!.tipoReversion = 'REVIERTE_TEXTO';

    expect(component.muestraDetalle).toBeFalse();
  });

  it('carga la cadena de documentos asociados al iniciar', () => {
    const notaDebitoService = {
      obtener: jasmine.createSpy('obtener').and.returnValue(of(notaEn('EMITIDA'))),
      cadenaDocumentos: jasmine.createSpy('cadenaDocumentos').and.returnValue(
        of([
          { tipo: 'COTIZACION', documentoId: 70, numero: 'COT-000006', fecha: '2026-09-08', montoTotal: 12852 },
          { tipo: 'NOTA_VENTA', documentoId: 60, numero: 'NV-000006', fecha: '2026-09-10', montoTotal: 12852 },
          { tipo: 'VENTA', documentoId: 50, numero: 'FACTURA N.º 1042', fecha: '2026-09-12', montoTotal: 12852 },
          { tipo: 'NOTA_CREDITO', documentoId: 90, numero: 'NC-000090', fecha: '2026-09-15', montoTotal: 12852 },
        ] satisfies EslabonCadenaDocumento[],
      )),
    } as unknown as NotaDebitoService;
    const route = { paramMap: of({ get: () => '9' }) } as unknown as ActivatedRoute;
    const router = { navigate: jasmine.createSpy('navigate') } as unknown as Router;
    const dialog = { open: jasmine.createSpy('open') } as unknown as MatDialog;
    const auth = { tienePermiso: () => true } as unknown as AuthService;
    const movimientoService = { detalle: jasmine.createSpy('detalle') } as unknown as MovimientoService;
    const component = new NotaDebitoDetalleComponent(
      notaDebitoService, route, router, movimientoService, dialog, auth);
    component.ngOnInit();

    expect(notaDebitoService.cadenaDocumentos).toHaveBeenCalledWith(9);
    expect(component.cadena).toHaveSize(4);
    expect(component.cadena[0].tipo).toBe('COTIZACION');
  });

  it('el eslabón de la cotización navega a su detalle', () => {
    const { component, router } = crear('EMITIDA');

    component.verEslabon({ tipo: 'COTIZACION', documentoId: 70, numero: 'COT-000006', fecha: null, montoTotal: null });

    expect(router.navigate).toHaveBeenCalledWith(['/cotizaciones', 70]);
  });

  it('el eslabón de la nota de venta navega a su detalle', () => {
    const { component, router } = crear('EMITIDA');

    component.verEslabon({ tipo: 'NOTA_VENTA', documentoId: 60, numero: 'NV-000006', fecha: null, montoTotal: null });

    expect(router.navigate).toHaveBeenCalledWith(['/notas-venta', 60]);
  });

  it('el eslabón de la venta abre el documento asociado con el snapshot de la ND', () => {
    const { component, dialog } = crear('EMITIDA');

    component.verEslabon({ tipo: 'VENTA', documentoId: 50, numero: 'FACTURA N.º 1042', fecha: null, montoTotal: null });

    expect(dialog.open).toHaveBeenCalledWith(
      DocumentoAsociadoDialogComponent,
      jasmine.objectContaining({
        data: jasmine.objectContaining({
          ventaId: 50,
          tipo: 'FACTURA',
          folio: 90,
          clienteNombre: 'Empresa ABC SpA',
          razon: 'Devolución rechazada',
        }),
      }),
    );
  });

  it('el eslabón de la nota de crédito abre su detalle', () => {
    const { component, dialog } = crear('EMITIDA');

    component.verEslabon({ tipo: 'NOTA_CREDITO', documentoId: 90, numero: 'NC-000090', fecha: null, montoTotal: null });

    expect(dialog.open).toHaveBeenCalledWith(
      NotaCreditoAsociadaDialogComponent,
      jasmine.objectContaining({
        data: jasmine.objectContaining({ notaCreditoId: 90 }),
      }),
    );
  });

  it('la cadena vacía no rompe el nodo de la nota de crédito', () => {
    const { component } = crear('EMITIDA');
    component.cadena = [];

    expect(component.cadena).toEqual([]);
    expect(component.nota?.documentoAsociado.numero).toBe('NC-000090');
  });
});