import { of, throwError } from 'rxjs';
import { InventarioComponent } from './inventario.component';
import { InventarioService } from '../../core/services/inventario.service';
import { BodegaService } from '../../core/services/bodega.service';
import { CategoriaService } from '../../core/services/categoria.service';
import { SubcategoriaService } from '../../core/services/subcategoria.service';
import { InventarioConsultaItem, PaginaResponse } from '../../core/models/models';

function paginaDeEjemplo(overrides: Partial<PaginaResponse<InventarioConsultaItem>> = {}): PaginaResponse<InventarioConsultaItem> {
  return { contenido: [], total: 0, ...overrides };
}

describe('InventarioComponent', () => {
  function crear() {
    const inventarioServiceStub = {
      listar: jasmine.createSpy('listar').and.returnValue(of(paginaDeEjemplo())),
      exportarCsv: jasmine.createSpy('exportarCsv').and.returnValue(of(new Blob())),
      exportarXlsx: jasmine.createSpy('exportarXlsx').and.returnValue(of(new Blob())),
    } as unknown as InventarioService;
    const bodegaServiceStub = {
      listar: jasmine.createSpy('listar').and.returnValue(of([])),
    } as unknown as BodegaService;
    const categoriaServiceStub = {
      listar: jasmine.createSpy('listar').and.returnValue(of([])),
    } as unknown as CategoriaService;
    const subcategoriaServiceStub = {
      listar: jasmine.createSpy('listar').and.returnValue(of([])),
    } as unknown as SubcategoriaService;

    const component = new InventarioComponent(
      inventarioServiceStub,
      bodegaServiceStub,
      categoriaServiceStub,
      subcategoriaServiceStub
    );
    return { component, inventarioServiceStub, subcategoriaServiceStub };
  }

  it('al iniciar consulta con los filtros por defecto', () => {
    const { component, inventarioServiceStub } = crear();

    component.ngOnInit();

    expect(inventarioServiceStub.listar).toHaveBeenCalledWith(
      { bodegaId: null, familiaId: null, subfamiliaId: null, verDeshabilitados: false, tipoBusqueda: 'NOMBRE', busqueda: '' },
      'nombre',
      'asc',
      0,
      10
    );
  });

  it('cambiar un filtro reinicia la pagina a 0', () => {
    const { component } = crear();
    component.ngOnInit();
    component.pagina = 3;

    component.bodegaId = 5;
    component.onFiltroChange();

    expect(component.pagina).toBe(0);
  });

  it('cambiar la familia limpia la subfamilia seleccionada y recarga las opciones', () => {
    const { component, subcategoriaServiceStub } = crear();
    component.ngOnInit();
    component.subfamiliaId = 9;

    component.familiaId = 4;
    component.onFamiliaChange();

    expect(component.subfamiliaId).toBeNull();
    expect(subcategoriaServiceStub.listar).toHaveBeenCalledWith(4);
  });

  it('un error de backend muestra un mensaje generico, no el detalle tecnico', () => {
    const { component, inventarioServiceStub } = crear();
    (inventarioServiceStub.listar as jasmine.Spy).and.returnValue(
      throwError(() => ({ error: { error: 'duplicate key value violates unique constraint' } }))
    );

    component.ngOnInit();

    expect(component.error).toBe('No fue posible cargar el inventario. Intente nuevamente.');
    expect(component.error).not.toContain('constraint');
  });

  it('sin resultados no hay error y la lista queda vacia', () => {
    const { component } = crear();

    component.ngOnInit();

    expect(component.error).toBe('');
    expect(component.items).toEqual([]);
    expect(component.total).toBe(0);
  });
});
