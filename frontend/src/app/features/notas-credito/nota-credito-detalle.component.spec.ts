import { ActivatedRoute, Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { NotaCreditoDetalleComponent } from './nota-credito-detalle.component';
import { NotaCreditoService } from '../../core/services/nota-credito.service';
import { AuthService } from '../../core/services/auth.service';
import { MovimientoService } from '../../core/services/movimiento.service';
import { MovimientoDetalleDialogComponent } from '../movimientos/movimiento-detalle-dialog.component';
import { DocumentoAsociadoDialogComponent } from './documento-asociado-dialog.component';
import { EstadoNotaCredito, NotaCredito } from '../../core/models/models';

function notaEn(estado: EstadoNotaCredito): NotaCredito {
  return {
    id: 9,
    folio: 12,
    numero: 'NC-000012',
    estado,
    tipoCorreccion: 'CORRIGE_MONTO',
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
      ventaId: 50,
      tipo: 'FACTURA',
      folio: 1042,
      numero: 'FACTURA N.º 1042',
      fecha: '2026-09-12',
      razon: 'Devolución parcial',
      montoTotal: 12852,
    },
    motivo: 'Mercadería en mal estado',
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
    recuperacionInventario: 'TOTAL',
    lineas: [],
    movimientosInventario: [],
    historial: [],
  };
}

function crear(estado: EstadoNotaCredito, dialogResult: unknown = 'Motivo de prueba') {
  const notaCreditoService = {
    obtener: jasmine.createSpy('obtener').and.returnValue(of(notaEn(estado))),
    emitir: jasmine.createSpy('emitir').and.returnValue(of(notaEn('EMITIDA'))),
    anular: jasmine.createSpy('anular').and.returnValue(of(notaEn('ANULADA'))),
    eliminar: jasmine.createSpy('eliminar').and.returnValue(of(void 0)),
    obtenerPdf: jasmine.createSpy('obtenerPdf').and.returnValue(of(new Blob())),
  } as unknown as NotaCreditoService;
  const route = { paramMap: of({ get: () => '9' }) } as unknown as ActivatedRoute;
  const router = { navigate: jasmine.createSpy('navigate') } as unknown as Router;
  const dialog = {
    open: jasmine.createSpy('open').and.returnValue({ afterClosed: () => of(dialogResult) }),
  } as unknown as MatDialog;
  const auth = { tienePermiso: () => true } as unknown as AuthService;

  const movimientoService = {
    detalle: jasmine.createSpy('detalle').and.returnValue(of({ id: 900, tipo: 'ENTRADA', items: [] })),
  } as unknown as MovimientoService;

  const component = new NotaCreditoDetalleComponent(
    notaCreditoService, route, router, movimientoService, dialog, auth);
  component.ngOnInit();
  return { component, notaCreditoService, router, dialog, movimientoService };
}

describe('NotaCreditoDetalleComponent', () => {
  it('carga la nota al iniciar', () => {
    const { component, notaCreditoService } = crear('BORRADOR');

    expect(notaCreditoService.obtener).toHaveBeenCalledWith(9);
    expect(component.nota?.numero).toBe('NC-000012');
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
    const { component, notaCreditoService } = crear('BORRADOR');

    component.abrirConfirmacionEmision();
    expect(component.confirmandoEmision).toBeTrue();
    expect(notaCreditoService.emitir).not.toHaveBeenCalled();

    component.emitir();
    expect(notaCreditoService.emitir).toHaveBeenCalledWith(9);
    expect(component.nota?.estado).toBe('EMITIDA');
    expect(component.confirmandoEmision).toBeFalse();
  });

  it('anular pasa el motivo devuelto por el diálogo', () => {
    const { component, notaCreditoService } = crear('EMITIDA', 'Devolución rechazada');

    component.anular();

    expect(notaCreditoService.anular).toHaveBeenCalledWith(9, 'Devolución rechazada');
    expect(component.nota?.estado).toBe('ANULADA');
  });

  it('anular no llama al servicio si el diálogo se cierra sin motivo', () => {
    const { component, notaCreditoService } = crear('EMITIDA', null);

    component.anular();

    expect(notaCreditoService.anular).not.toHaveBeenCalled();
  });

  it('eliminar vuelve al listado', () => {
    const { component, notaCreditoService, router } = crear('BORRADOR');

    component.eliminar();

    expect(notaCreditoService.eliminar).toHaveBeenCalledWith(9);
    expect(router.navigate).toHaveBeenCalledWith(['/notas-credito']);
  });

  it('el nodo del documento abre su detalle con los datos de la venta', () => {
    const { component, dialog } = crear('EMITIDA');

    component.verDocumentoAsociado();

    expect(dialog.open).toHaveBeenCalledWith(
      DocumentoAsociadoDialogComponent,
      jasmine.objectContaining({
        data: jasmine.objectContaining({ ventaId: 50, tipo: 'FACTURA', folio: 1042 }),
      }),
    );
  });

  it('el nodo de inventario abre el movimiento del modulo de Inventario', () => {
    const { component, dialog, movimientoService } = crear('EMITIDA');
    component.nota!.movimientosInventario = [
      {
        movimientoInventarioId: 105,
        headerId: 900,
        tipo: 'RECUPERACION',
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
    const notaCreditoService = {
      obtener: jasmine.createSpy('obtener').and.returnValue(of(notaEn('BORRADOR'))),
      emitir: jasmine.createSpy('emitir').and.returnValue(
        throwError(() => ({
          error: { error: 'No se puede recuperar 5 de "Bidón 20L": solo hay 2 disponibles' },
        })),
      ),
    } as unknown as NotaCreditoService;
    const route = { paramMap: of({ get: () => '9' }) } as unknown as ActivatedRoute;
    const router = { navigate: jasmine.createSpy('navigate') } as unknown as Router;
    const dialog = { open: jasmine.createSpy('open') } as unknown as MatDialog;
    const auth = { tienePermiso: () => true } as unknown as AuthService;
    const movimientoService = { detalle: jasmine.createSpy("detalle") } as unknown as MovimientoService;
    const component = new NotaCreditoDetalleComponent(
      notaCreditoService, route, router, movimientoService, dialog, auth);
    component.ngOnInit();

    component.emitir();

    expect(component.error).toContain('Bidón 20L');
    expect(component.procesando).toBeFalse();
  });

  it('oculta el detalle en una corrección de texto', () => {
    const { component } = crear('EMITIDA');
    component.nota!.tipoCorreccion = 'CORRIGE_TEXTO';

    expect(component.muestraDetalle).toBeFalse();
  });
});
