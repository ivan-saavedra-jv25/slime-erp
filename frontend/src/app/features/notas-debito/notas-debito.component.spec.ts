import { fakeAsync, tick } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { NotasDebitoComponent } from './notas-debito.component';
import { NotaDebitoService } from '../../core/services/nota-debito.service';
import { AuthService } from '../../core/services/auth.service';
import { DashboardNotasDebito, NotaDebitoResumen } from '../../core/models/models';

const dashboardVacio: DashboardNotasDebito = {
  desde: '2026-09-01',
  hasta: '2026-09-30',
  cantidad: 0,
  borradores: 0,
  emitidas: 0,
  anuladas: 0,
  montoTotalEmitido: 0,
  conReversionInventario: 0,
  notasCreditoRevertidas: 0,
  porEstado: [],
  porTipoReversion: [],
};

const nota: NotaDebitoResumen = {
  id: 9,
  folio: 12,
  numero: 'ND-000012',
  estado: 'BORRADOR',
  tipoReversion: 'REVIERTE_MONTO',
  fecha: '2026-09-23',
  clienteId: 5,
  clienteNombre: 'Empresa ABC SpA',
  clienteRut: '76.111.222-3',
  ncNumero: 'NC-000012',
  ncFolio: 12,
  ncDocAsociadoTipo: 'FACTURA',
  motivo: 'Devolución rechazada',
  montoTotal: 2380,
  impactoInventario: 'PARCIAL',
};

function crear() {
  const notaDebitoService = {
    listar: jasmine.createSpy('listar').and.returnValue(of({ contenido: [nota], total: 1 })),
    dashboard: jasmine.createSpy('dashboard').and.returnValue(of(dashboardVacio)),
  } as unknown as NotaDebitoService;
  const router = { navigate: jasmine.createSpy('navigate') } as unknown as Router;
  const auth = { tienePermiso: () => true } as unknown as AuthService;
  const component = new NotasDebitoComponent(notaDebitoService, router, auth);
  return { component, notaDebitoService, router };
}

describe('NotasDebitoComponent', () => {
  it('carga listado y dashboard al iniciar, con el período del mes en curso', () => {
    const { component, notaDebitoService } = crear();

    component.ngOnInit();

    expect(notaDebitoService.dashboard).toHaveBeenCalled();
    expect(notaDebitoService.listar).toHaveBeenCalled();
    expect(component.notas.length).toBe(1);
    expect(component.total).toBe(1);
    expect(component.periodoActivo).toBe('mes');
    component.ngOnDestroy();
  });

  it('filtrar por estado vuelve a la primera página', () => {
    const { component, notaDebitoService } = crear();
    component.ngOnInit();
    component.paginaActual = 3;

    component.estado = 'EMITIDA';
    component.consultar();

    expect(component.paginaActual).toBe(0);
    const ultima = (notaDebitoService.listar as jasmine.Spy).calls.mostRecent().args[0];
    expect(ultima.estado).toBe('EMITIDA');
    component.ngOnDestroy();
  });

  it('manda el tipo de reversión como filtro', () => {
    const { component, notaDebitoService } = crear();
    component.ngOnInit();

    component.tipoReversion = 'REVIERTE_TEXTO';
    component.consultar();

    const ultima = (notaDebitoService.listar as jasmine.Spy).calls.mostRecent().args[0];
    expect(ultima.tipoReversion).toBe('REVIERTE_TEXTO');
    component.ngOnDestroy();
  });

  it('la búsqueda espera el debounce antes de consultar', fakeAsync(() => {
    const { component, notaDebitoService } = crear();
    component.ngOnInit();
    const llamadasIniciales = (notaDebitoService.listar as jasmine.Spy).calls.count();

    component.busqueda = 'ND-000012';
    component.onBusquedaChange();
    expect((notaDebitoService.listar as jasmine.Spy).calls.count()).toBe(llamadasIniciales);

    tick(300);
    expect((notaDebitoService.listar as jasmine.Spy).calls.count()).toBe(llamadasIniciales + 1);
    component.ngOnDestroy();
  }));

  it('rechaza un rango de fechas invertido sin llamar al servicio', () => {
    const { component, notaDebitoService } = crear();
    component.ngOnInit();
    const llamadasIniciales = (notaDebitoService.listar as jasmine.Spy).calls.count();

    component.desde = '2026-09-30';
    component.hasta = '2026-09-01';
    component.consultar();

    expect(component.error).toContain('no puede ser posterior');
    expect((notaDebitoService.listar as jasmine.Spy).calls.count()).toBe(llamadasIniciales);
    component.ngOnDestroy();
  });

  it('cambiar de página recarga con el índice y tamaño nuevos', () => {
    const { component, notaDebitoService } = crear();
    component.ngOnInit();

    component.onPageChange({ pageIndex: 2, pageSize: 25, length: 100 });

    const ultima = (notaDebitoService.listar as jasmine.Spy).calls.mostRecent().args[0];
    expect(ultima.pagina).toBe(2);
    expect(ultima.tamano).toBe(25);
    component.ngOnDestroy();
  });

  it('limpiar filtros los deja todos vacíos', () => {
    const { component } = crear();
    component.ngOnInit();
    component.estado = 'EMITIDA';
    component.tipoReversion = 'REVIERTE_MONTO';
    component.busqueda = 'algo';

    expect(component.hayFiltrosActivos).toBeTrue();
    component.limpiarFiltros();

    expect(component.hayFiltrosActivos).toBeFalse();
    expect(component.estado).toBeNull();
    component.ngOnDestroy();
  });

  it('abrir navega al detalle y editar al formulario sin propagar el click', () => {
    const { component, router } = crear();
    const evento = { stopPropagation: jasmine.createSpy('stopPropagation') } as unknown as MouseEvent;

    component.abrir(nota);
    expect(router.navigate).toHaveBeenCalledWith(['/notas-debito', 9]);

    component.editar(nota, evento);
    expect(evento.stopPropagation).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/notas-debito', 9, 'editar']);
  });

  it('muestra el mensaje del backend cuando el listado falla', () => {
    const notaDebitoService = {
      listar: jasmine
        .createSpy('listar')
        .and.returnValue(throwError(() => ({ error: { error: 'Sin permisos' } }))),
      dashboard: jasmine.createSpy('dashboard').and.returnValue(of(dashboardVacio)),
    } as unknown as NotaDebitoService;
    const router = { navigate: jasmine.createSpy('navigate') } as unknown as Router;
    const auth = { tienePermiso: () => true } as unknown as AuthService;
    const component = new NotasDebitoComponent(notaDebitoService, router, auth);

    component.ngOnInit();

    expect(component.error).toBe('Sin permisos');
    expect(component.cargando).toBeFalse();
    component.ngOnDestroy();
  });
});