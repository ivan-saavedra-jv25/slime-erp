import { BehaviorSubject, of } from 'rxjs';
import { ActivatedRoute, ParamMap, Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { NotaVentaDetalleComponent } from './nota-venta-detalle.component';
import { NotaVentaService } from '../../core/services/nota-venta.service';
import { AuthService } from '../../core/services/auth.service';
import { EstadoNotaVenta, NotaVenta } from '../../core/models/models';

describe('NotaVentaDetalleComponent', () => {
  function linea(extra?: Partial<NotaVenta['lineas'][number]>) {
    return {
      id: 1,
      productoId: 3,
      codigo: 'P-01',
      descripcion: 'Producto Demo',
      cantidad: 10,
      cantidadEntregada: 0,
      precioUnitario: 1000,
      descuento: 0,
      subtotal: 10000,
      ...extra,
    };
  }

  function nota(estado: EstadoNotaVenta, lineas: NotaVenta['lineas'] = [linea()]): NotaVenta {
    return {
      id: 1,
      folio: 1,
      numero: 'NV-000001',
      estado,
      exenta: false,
      origen: 'COTIZACION',
      cotizacionId: 5,
      cotizacionNumero: 'COT-000005',
      moneda: 'CLP',
      fechaEmision: '2026-09-22',
      fechaEntregaEstimada: '2026-09-30',
      clienteId: 5,
      clienteNombre: 'Empresa ABC SpA',
      clienteRazonSocial: null,
      clienteRut: '76.111.222-3',
      clienteDireccion: null,
      clienteEmail: null,
      clienteTelefono: null,
      vendedorId: 7,
      vendedorNombre: 'Vendedor Demo',
      formaPagoId: null,
      formaPagoNombre: null,
      direccionEntrega: null,
      condicionesVenta: null,
      observaciones: null,
      motivo: null,
      descuento: 0,
      montoSubtotal: 10000,
      montoDescuento: 0,
      montoNeto: 10000,
      montoIva: 1900,
      montoTotal: 11900,
      lineas,
      eventos: [],
      entregas: [],
      documentosRelacionados: [],
    };
  }

  function paramMapDe(id: string): ParamMap {
    return { get: () => id } as unknown as ParamMap;
  }

  function crear(estado: EstadoNotaVenta, notaService?: Partial<Record<string, unknown>>, puedeEditar = true) {
    const params$ = new BehaviorSubject<ParamMap>(paramMapDe('1'));
    const notaVentaService = {
      obtener: jasmine.createSpy('obtener').and.returnValue(of(nota(estado))),
      confirmar: jasmine.createSpy('confirmar').and.returnValue(of(nota('CONFIRMADA'))),
      preparar: jasmine.createSpy('preparar').and.returnValue(of(nota('EN_PREPARACION'))),
      cancelar: jasmine.createSpy('cancelar').and.returnValue(of(nota('CANCELADA'))),
      registrarEntrega: jasmine.createSpy('registrarEntrega').and.returnValue(of(nota('PARCIALMENTE_ENTREGADA'))),
      duplicar: jasmine.createSpy('duplicar').and.returnValue(of({ ...nota('BORRADOR'), id: 2 })),
      eliminar: jasmine.createSpy('eliminar').and.returnValue(of(null)),
      obtenerPdf: jasmine.createSpy('obtenerPdf').and.returnValue(of(new Blob())),
      ...notaService,
    } as unknown as NotaVentaService;
    const route = { paramMap: params$.asObservable() } as unknown as ActivatedRoute;
    const router = { navigate: jasmine.createSpy('navigate') } as unknown as Router;
    const dialog = {
      open: jasmine.createSpy('open').and.returnValue({ afterClosed: () => of(undefined) }),
    } as unknown as MatDialog;
    const auth = { tienePermiso: () => puedeEditar } as unknown as AuthService;

    const component = new NotaVentaDetalleComponent(notaVentaService, route, router, dialog, auth);
    component.ngOnInit();
    return { component, notaVentaService, router, params$, dialog };
  }

  it('reconoce los estados permitidos con esEstado', () => {
    const { component } = crear('BORRADOR');

    expect(component.esEstado('BORRADOR')).toBeTrue();
    expect(component.esEstado('CONFIRMADA')).toBeFalse();
    expect(component.esEstado('BORRADOR', 'CONFIRMADA')).toBeTrue();
  });

  it('confirmar envía la confirma y actualiza la nota', () => {
    const { component, notaVentaService } = crear('BORRADOR');

    component.confirmar();

    expect(notaVentaService.confirmar).toHaveBeenCalledWith(1);
    expect(component.nota!.estado).toBe('CONFIRMADA');
  });

  it('preparar envía la preparación', () => {
    const { component, notaVentaService } = crear('CONFIRMADA');

    component.preparar();

    expect(notaVentaService.preparar).toHaveBeenCalledWith(1);
    expect(component.nota!.estado).toBe('EN_PREPARACION');
  });

  it('cancelar envía el motivo recortado y cierra el formulario', () => {
    const { component, notaVentaService } = crear('CONFIRMADA');

    component.abrirMotivo();
    component.motivo = '  Cliente anuló  ';
    component.confirmarMotivo();

    expect(notaVentaService.cancelar).toHaveBeenCalledWith(1, 'Cliente anuló');
    expect(component.accionConMotivo).toBeFalse();
  });

  it('cancelar sin motivo envía null', () => {
    const { component, notaVentaService } = crear('BORRADOR');

    component.abrirMotivo();
    component.confirmarMotivo();

    expect(notaVentaService.cancelar).toHaveBeenCalledWith(1, null);
  });

  it('abrirEntrega sólo incluye las líneas aún entregables', () => {
    const { component } = crear('EN_PREPARACION', undefined, true);
    component.nota = nota('EN_PREPARACION', [linea({ cantidadEntregada: 0 }), linea({ cantidadEntregada: 10 })]);

    component.abrirEntrega();

    expect(component.lineasEntrega.length).toBe(1);
    expect(component.lineasEntrega[0].solicitada).toBe(10);
  });

  it('rechaza una entrega que supera lo solicitado', () => {
    const { component, notaVentaService } = crear('CONFIRMADA');
    component.abrirEntrega();
    component.lineasEntrega[0].cantidad = 999;

    component.confirmarEntrega();

    expect(notaVentaService.registrarEntrega).not.toHaveBeenCalled();
    expect(component.entregaError).toContain('supera lo solicitado');
  });

  it('rechaza cantidades negativas', () => {
    const { component, notaVentaService } = crear('CONFIRMADA');
    component.nota = nota('EN_PREPARACION', [linea({ productoId: 3, descripcion: 'A' }), linea({ productoId: 4, descripcion: 'B' })]);
    component.abrirEntrega();
    component.lineasEntrega[0].cantidad = 5;
    component.lineasEntrega[1].cantidad = -1;

    component.confirmarEntrega();

    expect(notaVentaService.registrarEntrega).not.toHaveBeenCalled();
    expect(component.entregaError).toContain('no pueden ser negativas');
  });

  it('pide al menos una cantidad mayor a cero', () => {
    const { component, notaVentaService } = crear('CONFIRMADA');
    component.abrirEntrega();

    component.confirmarEntrega();

    expect(notaVentaService.registrarEntrega).not.toHaveBeenCalled();
    expect(component.entregaError).toContain('al menos una cantidad');
  });

  it('registra la entrega enviando sólo las líneas con cantidad y cierra el formulario', () => {
    const { component, notaVentaService } = crear('CONFIRMADA');
    component.abrirEntrega();
    component.lineasEntrega[0].cantidad = 4;
    component.entregaObservacion = '  Primera parte  ';

    component.confirmarEntrega();

    expect(notaVentaService.registrarEntrega).toHaveBeenCalledWith(1, {
      observacion: 'Primera parte',
      lineas: [{ productoId: 3, cantidad: 4 }],
    });
    expect(component.entregaAbierta).toBeFalse();
  });

  it('duplicar navega a la copia recién creada', () => {
    const { component, router } = crear('CONFIRMADA');

    component.duplicar();

    expect(router.navigate).toHaveBeenCalledWith(['/notas-venta', 2]);
  });

  it('eliminar vuelve al listado', () => {
    const { component, router } = crear('BORRADOR');

    component.eliminar();

    expect(router.navigate).toHaveBeenCalledWith(['/notas-venta']);
  });

  it('verPdf abre el diálogo con el blob generado', () => {
    const { component, dialog } = crear('CONFIRMADA');

    component.verPdf();

    expect(dialog.open).toHaveBeenCalledWith(
      jasmine.anything(),
      jasmine.objectContaining({ data: jasmine.objectContaining({ titulo: 'Nota de Venta NV-000001' }) })
    );
  });

  it('sin permiso de edición no se pueden ejecutar acciones de escritura', () => {
    const { component } = crear('BORRADOR', {}, false);

    expect(component.puedeEditar).toBeFalse();
  });

  it('recarga la nota cuando cambia el id de la ruta sin recrear el componente', () => {
    const { notaVentaService, params$ } = crear('CANCELADA');
    expect(notaVentaService.obtener).toHaveBeenCalledWith(1);

    params$.next({ get: () => '2' } as never);

    expect(notaVentaService.obtener).toHaveBeenCalledWith(2);
  });
});