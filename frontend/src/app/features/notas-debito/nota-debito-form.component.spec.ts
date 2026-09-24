import { fakeAsync, tick } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { NotaDebitoFormComponent } from './nota-debito-form.component';
import { NotaDebitoService } from '../../core/services/nota-debito.service';
import { ClienteService } from '../../core/services/cliente.service';
import { MatDialog } from '@angular/material/dialog';
import { DocumentoNotaCreditoAsociable, Cliente, NotaDebito } from '../../core/models/models';

const cliente: Cliente = {
  id: 5,
  nombre: 'Empresa ABC SpA',
  rut: '76.111.222-3',
  email: null,
  telefono: null,
  direccion: null,
  razonSocial: null,
  giro: null,
  comuna: null,
  ciudad: null,
  activo: true,
};

const doc: DocumentoNotaCreditoAsociable = {
  notaCreditoId: 90,
  numero: 'NC-000090',
  folio: 90,
  fecha: '2026-09-15',
  clienteId: 5,
  clienteNombre: 'Empresa ABC SpA',
  clienteRut: '76.111.222-3',
  docAsociadoTipo: 'FACTURA',
  docAsociadoFolio: 1042,
  exenta: false,
  montoTotal: 12852,
  montoDisponible: 12852,
  tieneNotasDebito: false,
};

const notaGuardada: NotaDebito = {
  id: 9,
  folio: 12,
  numero: 'ND-000012',
  estado: 'BORRADOR',
  tipoReversion: 'REVIERTE_MONTO',
  fecha: '2026-09-23',
  clienteId: 5,
  clienteNombre: 'Empresa ABC SpA',
  clienteRazonSocial: null,
  clienteRut: '76.111.222-3',
  clienteDireccion: null,
  clienteEmail: null,
  clienteTelefono: null,
  usuarioId: 1,
  usuarioNombre: 'Admin',
  documentoAsociado: {
    notaCreditoId: 90,
    numero: 'NC-000090',
    fecha: '2026-09-15',
    ncFolio: 90,
    ncDocAsociadoTipo: 'FACTURA',
    montoTotal: 12852,
    montoDisponible: 8380,
    razon: 'Devolución rechazada',
  },
  motivo: null,
  observaciones: null,
  textoCorreccion: null,
  bodegaId: 3,
  bodegaNombre: 'Bodega Central',
  exenta: false,
  moneda: 'CLP',
  descuento: 0,
  montoSubtotal: 4472,
  montoDescuento: 0,
  montoNeto: 4472,
  montoIva: 850,
  montoTotal: 5322,
  fechaEmision: null,
  fechaAnulacion: null,
  impactoInventario: 'PARCIAL',
  lineas: [
    {
      id: 1,
      productoId: 77,
      notaCreditoDetalleId: 100,
      codigo: 'A100',
      descripcion: 'Producto A',
      cantidad: 2,
      precioUnitario: 1000,
      descuento: 0,
      subtotal: 2000,
      revierteInventario: true,
    },
  ],
  movimientosInventario: [],
  historial: [],
};

function crear() {
  const notaDebitoService = {
    notasCreditoAsociables: jasmine
      .createSpy('notasCreditoAsociables')
      .and.returnValue(of([doc])),
    lineasNotaCredito: jasmine.createSpy('lineasNotaCredito').and.returnValue(
      of([
        {
          notaCreditoDetalleId: 100,
          productoId: 77,
          codigo: 'A100',
          descripcion: 'Producto A',
          cantidad: 2,
          cantidadRevertida: 0,
          cantidadDisponible: 1,
          precioUnitario: 1000,
          subtotal: 2000,
        },
      ])
    ),
    crear: jasmine.createSpy('crear').and.returnValue(of(notaGuardada)),
    actualizar: jasmine.createSpy('actualizar').and.returnValue(of(notaGuardada)),
    eliminar: jasmine.createSpy('eliminar').and.returnValue(of(void 0)),
    obtener: jasmine.createSpy('obtener').and.returnValue(of(notaGuardada)),
  } as unknown as NotaDebitoService;
  const clienteService = {
    listarPagina: jasmine
      .createSpy('listarPagina')
      .and.returnValue(of({ contenido: [cliente], total: 1 })),
  } as unknown as ClienteService;
  const route = { snapshot: { paramMap: { get: () => null } } } as never;
  const router = { navigate: jasmine.createSpy('navigate') } as unknown as Router;
  const dialog = {
    open: jasmine.createSpy('open').and.returnValue({ afterClosed: () => of(undefined) }),
  } as unknown as MatDialog;
  const component = new NotaDebitoFormComponent(notaDebitoService, clienteService, route, router, dialog);
  return { component, notaDebitoService, clienteService, router, dialog };
}

function ruta(component: NotaDebitoFormComponent, id: number) {
  component['route'] = { snapshot: { paramMap: { get: () => String(id) } } } as never;
}

describe('NotaDebitoFormComponent', () => {
  it('al seleccionar cliente busca sus notas de crédito asociables', fakeAsync(() => {
    const { component, clienteService, notaDebitoService } = crear();
    component.ngOnInit();
    component.filtroCliente = 'ABC';
    component.onBusquedaClienteChange();
    tick(300);

    expect(clienteService.listarPagina).toHaveBeenCalled();
    component.seleccionarCliente(cliente);
    expect(notaDebitoService.notasCreditoAsociables).toHaveBeenCalledWith(5, null);
    component.ngOnDestroy();
  }));

  it('al seleccionar la NC carga sus líneas', () => {
    const { component, notaDebitoService } = crear();
    component.seleccionarCliente(cliente);

    component.seleccionarDocumento(doc);

    expect(notaDebitoService.lineasNotaCredito).toHaveBeenCalledWith(90, null);
    expect(component.lineas.length).toBe(1);
    expect(component.documentoSeleccionado).toBe(doc);
  });

  it('REVIERTE_TEXTO desmarca líneas, pone el descuento en cero y oculta totales', () => {
    const { component } = crear();
    component.seleccionarCliente(cliente);
    component.seleccionarDocumento(doc);
    component.lineas[0].incluida = true;
    component.descuento = 500;

    component.seleccionarTipo('REVIERTE_TEXTO');

    expect(component.muestraTexto).toBeTrue();
    expect(component.muestraDetalle).toBeFalse();
    expect(component.lineasIncluidas.length).toBe(0);
    expect(component.descuento).toBe(0);
  });

  it('REVIERTE_DOCUMENTO incluye todas las líneas y revierte inventario', () => {
    const { component } = crear();
    component.seleccionarCliente(cliente);
    component.seleccionarDocumento(doc);

    component.seleccionarTipo('REVIERTE_DOCUMENTO');

    expect(component.lineasBloqueadas).toBeTrue();
    expect(component.lineasIncluidas.length).toBe(1);
    expect(component.lineas[0].incluida).toBeTrue();
    expect(component.lineas[0].revierteInventario).toBeTrue();
  });

  it('una cantidad mayor al disponible invalida la línea', () => {
    const { component } = crear();
    component.seleccionarCliente(cliente);
    component.seleccionarDocumento(doc);
    component.seleccionarTipo('REVIERTE_MONTO');
    component.lineas[0].incluida = true;
    component.lineas[0].cantidad = 5;

    expect(component.errorDeLinea(component.lineas[0])).toContain('Solo se pueden revertir');
    expect(component.puedeGuardar).toBeFalse();
  });

  it('los totales de una factura desglosan IVA (redondeo bruto/neto)', () => {
    const { component } = crear();
    component.seleccionarCliente(cliente);
    component.seleccionarDocumento(doc);
    component.seleccionarTipo('REVIERTE_MONTO');
    component.lineas[0].incluida = true;
    component.lineas[0].cantidad = 2;

    expect(component.subtotalActual).toBe(2000);
    expect(component.netoActual).toBe(2000);
    expect(component.ivaActual).toBe(380);
    expect(component.totalActual).toBe(2380);
  });

  it('guardar manda el request sin id y navega al detalle', fakeAsync(() => {
    const { component, notaDebitoService, router } = crear();
    component.seleccionarCliente(cliente);
    component.seleccionarDocumento(doc);
    component.seleccionarTipo('REVIERTE_MONTO');
    component.lineas[0].incluida = true;
    component.ncRazon = 'Devolución rechazada';

    component.guardar();
    tick(700);

    expect(notaDebitoService.crear).toHaveBeenCalled();
    const request = (notaDebitoService.crear as jasmine.Spy).calls.mostRecent().args[0];
    expect(request.notaCreditoId).toBe(90);
    expect(request.items.length).toBe(1);
    expect(request.items[0].productoId).toBe(77);
    expect(router.navigate).toHaveBeenCalledWith(['/notas-debito', 9]);
  }));

  it('en edición carga el borrador y permite guardar la actualización', fakeAsync(() => {
    const { component, notaDebitoService, router } = crear();
    // El borrador guardó cantidad 2; la NC sigue teniendo esas 2 disponibles
    // porque el propio borrador no cuenta (excluyendoNotaDebitoId=9).
    (notaDebitoService.lineasNotaCredito as jasmine.Spy).and.returnValue(
      of([
        {
          notaCreditoDetalleId: 100,
          productoId: 77,
          codigo: 'A100',
          descripcion: 'Producto A',
          cantidad: 2,
          cantidadRevertida: 0,
          cantidadDisponible: 2,
          precioUnitario: 1000,
          subtotal: 2000,
        },
      ])
    );
    ruta(component, 9);
    component.ngOnInit();

    expect(notaDebitoService.obtener).toHaveBeenCalledWith(9);
    expect(component.numeroNota).toBe('ND-000012');
    expect(component.documentoSeleccionado?.notaCreditoId).toBe(90);
    expect(component.ncRazon).toBe('Devolución rechazada');
    expect(component.esEdicion).toBeTrue();
    expect(component.lineasIncluidas.length).toBe(1);
    expect(component.lineas[0].cantidad).toBe(2);

    component.guardar();
    tick(700);
    expect(notaDebitoService.actualizar).toHaveBeenCalledWith(9, jasmine.anything());
    expect(router.navigate).toHaveBeenCalledWith(['/notas-debito', 9]);
    component.ngOnDestroy();
  }));

  it('redirige al detalle si la nota cargada no es un borrador', () => {
    const { component, router, notaDebitoService } = crear();
    (notaDebitoService.obtener as jasmine.Spy).and.returnValue(
      of({ ...notaGuardada, id: 9, estado: 'EMITIDA' })
    );
    ruta(component, 9);
    component.ngOnInit();
    component.cargando = false;

    expect(router.navigate).toHaveBeenCalledWith(['/notas-debito', 9]);
    component.ngOnDestroy();
  });

  it('muestra el mensaje del backend cuando no se puede guardar', () => {
    const { component, notaDebitoService } = crear();
    (notaDebitoService.crear as jasmine.Spy).and.returnValue(
      throwError(() => ({ error: { error: 'Sin permisos' } }))
    );
    component.seleccionarCliente(cliente);
    component.seleccionarDocumento(doc);
    component.seleccionarTipo('REVIERTE_MONTO');
    component.lineas[0].incluida = true;
    component.ncRazon = 'Razón';

    component.guardar();

    expect(component.error).toBe('Sin permisos');
    expect(component.guardando).toBeFalse();
  });

  it('eliminar pide confirmación y el borrador se elimina solo tras confirmarlo', () => {
    const { component, notaDebitoService, dialog, router } = crear();
    ruta(component, 9);
    component.ngOnInit();

    const toast = component;
    // El diálogo confirma devolviendo un motivo (como en el detalle de NC).
    (dialog.open as jasmine.Spy).and.returnValue({ afterClosed: () => of('Sin uso') });
    toast.eliminar();

    expect(dialog.open).toHaveBeenCalled();
    expect(notaDebitoService.eliminar).toHaveBeenCalledWith(9);
    expect(router.navigate).toHaveBeenCalledWith(['/notas-debito']);
    component.ngOnDestroy();
  });

  it('si el diálogo se cancela no se elimina nada', () => {
    const { component, notaDebitoService, dialog } = crear();
    ruta(component, 9);
    component.ngOnInit();

    (dialog.open as jasmine.Spy).and.returnValue({ afterClosed: () => of(undefined) });
    component.eliminar();

    expect(notaDebitoService.eliminar).not.toHaveBeenCalled();
    component.ngOnDestroy();
  });
});