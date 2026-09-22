import { BehaviorSubject, of } from 'rxjs';
import { ActivatedRoute, ParamMap, Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { CotizacionDetalleComponent } from './cotizacion-detalle.component';
import { CotizacionService } from '../../core/services/cotizacion.service';
import { NotaVentaService } from '../../core/services/nota-venta.service';
import { AuthService } from '../../core/services/auth.service';
import { Cotizacion, EstadoCotizacion } from '../../core/models/models';

describe('CotizacionDetalleComponent', () => {
  function cotizacion(estado: EstadoCotizacion): Cotizacion {
    return {
      id: 1,
      folio: 1,
      numero: 'COT-000001',
      estado,
      fechaEmision: '2026-09-22',
      fechaVencimiento: '2026-10-22',
      exenta: false,
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
      condicionesComerciales: null,
      observaciones: null,
      motivo: null,
      descuento: 0,
      montoSubtotal: 1000,
      montoDescuento: 0,
      montoNeto: 1000,
      montoIva: 190,
      montoTotal: 1190,
      lineas: [],
      eventos: [],
      documentosRelacionados: [],
    };
  }

  function paramMapDe(id: string): ParamMap {
    return { get: () => id } as unknown as ParamMap;
  }

  function crear(estado: EstadoCotizacion, puedeEditar = true) {
    const params$ = new BehaviorSubject<ParamMap>(paramMapDe('1'));
    const cotizacionService = {
      obtener: jasmine.createSpy('obtener').and.returnValue(of(cotizacion(estado))),
      enviar: jasmine.createSpy('enviar').and.returnValue(of(cotizacion('ENVIADA'))),
      aceptar: jasmine.createSpy('aceptar').and.returnValue(of(cotizacion('ACEPTADA'))),
      rechazar: jasmine.createSpy('rechazar').and.returnValue(of(cotizacion('RECHAZADA'))),
      cancelar: jasmine.createSpy('cancelar').and.returnValue(of(cotizacion('CANCELADA'))),
      duplicar: jasmine.createSpy('duplicar').and.returnValue(of({ ...cotizacion('BORRADOR'), id: 2 })),
    } as unknown as CotizacionService;
    const notaVentaService = {
      crearDesdeCotizacion: jasmine
        .createSpy('crearDesdeCotizacion')
        .and.returnValue(of({ id: 10, numero: 'NV-000001' })),
    } as unknown as NotaVentaService;
    const route = { paramMap: params$.asObservable() } as unknown as ActivatedRoute;
    const router = { navigate: jasmine.createSpy('navigate') } as unknown as Router;
    const dialog = { open: jasmine.createSpy('open') } as unknown as MatDialog;
    const auth = { tienePermiso: () => puedeEditar } as unknown as AuthService;

    const component = new CotizacionDetalleComponent(cotizacionService, notaVentaService, route, router, dialog, auth);
    component.ngOnInit();
    return { component, cotizacionService, notaVentaService, router, params$, paramMapDe };
  }

  it('un borrador permite editar, enviar y eliminar, pero no aceptar', () => {
    const { component } = crear('BORRADOR');

    expect(component.esEstado('BORRADOR')).toBeTrue();
    expect(component.esEstado('ENVIADA')).toBeFalse();
    expect(component.esEstado('BORRADOR', 'ENVIADA')).toBeTrue();
  });

  it('una cotización enviada permite aceptar y rechazar', () => {
    const { component, cotizacionService } = crear('ENVIADA');

    component.aceptar();

    expect(cotizacionService.aceptar).toHaveBeenCalledWith(1);
    expect(component.cotizacion!.estado).toBe('ACEPTADA');
  });

  it('rechazar envía el motivo escrito y cierra el formulario', () => {
    const { component, cotizacionService } = crear('ENVIADA');

    component.abrirMotivo('rechazar');
    component.motivo = '  Precio alto  ';
    component.confirmarMotivo();

    expect(cotizacionService.rechazar).toHaveBeenCalledWith(1, 'Precio alto');
    expect(component.accionConMotivo).toBeNull();
  });

  it('cancelar sin motivo envía null', () => {
    const { component, cotizacionService } = crear('ENVIADA');

    component.abrirMotivo('cancelar');
    component.confirmarMotivo();

    expect(cotizacionService.cancelar).toHaveBeenCalledWith(1, null);
  });

  it('duplicar navega a la copia recién creada', () => {
    const { component, router } = crear('RECHAZADA');

    component.duplicar();

    expect(router.navigate).toHaveBeenCalledWith(['/cotizaciones', 2]);
  });

  it('sin permiso de edición no se pueden ejecutar acciones de escritura', () => {
    const { component } = crear('BORRADOR', false);

    expect(component.puedeEditar).toBeFalse();
  });

  it('crearNotaVenta crea la nota desde la cotización y navega a su detalle', () => {
    const { component, notaVentaService, router } = crear('ACEPTADA');

    component.crearNotaVenta();

    expect(notaVentaService.crearDesdeCotizacion).toHaveBeenCalledWith(1);
    expect(router.navigate).toHaveBeenCalledWith(['/notas-venta', 10]);
  });

  it('recarga la cotización cuando cambia el id de la ruta sin recrear el componente', () => {
    const { cotizacionService, params$ } = crear('RECHAZADA');
    expect(cotizacionService.obtener).toHaveBeenCalledWith(1);

    params$.next({ get: () => '2' } as never);

    expect(cotizacionService.obtener).toHaveBeenCalledWith(2);
  });
});
