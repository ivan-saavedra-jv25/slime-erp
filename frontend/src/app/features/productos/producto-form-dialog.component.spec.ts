import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { ProductoFormDialogComponent } from './producto-form-dialog.component';

describe('ProductoFormDialogComponent', () => {
  let fixture: ComponentFixture<ProductoFormDialogComponent>;
  let component: ProductoFormDialogComponent;
  let dialogRefSpy: jasmine.SpyObj<MatDialogRef<ProductoFormDialogComponent>>;

  async function crear(data: any) {
    dialogRefSpy = jasmine.createSpyObj('MatDialogRef', ['close']);
    await TestBed.configureTestingModule({
      imports: [ProductoFormDialogComponent],
      providers: [
        { provide: MatDialogRef, useValue: dialogRefSpy },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ProductoFormDialogComponent);
    component = fixture.componentInstance;
  }

  it('en modo creación arranca con controlaStock en true', async () => {
    await crear(null);
    expect(component.esEdicion).toBeFalse();
    expect(component.controlaStock).toBeTrue();
  });

  it('en modo edición precarga los datos del producto', async () => {
    await crear({
      id: 8, sku: 'SKU-1', nombre: 'Producto Uno', descripcion: 'desc',
      precioVenta: 1000, precioCompra: 500, stock: 10, controlaStock: true, activo: true,
    });
    expect(component.esEdicion).toBeTrue();
    expect(component.nombre).toBe('Producto Uno');
    expect(component.precioVenta).toBe(1000);
  });

  it('guardar cierra el dialog con el request armado', async () => {
    await crear(null);
    component.sku = 'SKU-2';
    component.nombre = 'Producto Dos';
    component.descripcion = 'otra desc';
    component.precioVenta = 2000;
    component.precioCompra = 1000;
    component.stock = 5;
    component.controlaStock = false;

    component.guardar();

    expect(dialogRefSpy.close).toHaveBeenCalledWith({
      sku: 'SKU-2',
      nombre: 'Producto Dos',
      descripcion: 'otra desc',
      precioVenta: 2000,
      precioCompra: 1000,
      stock: 5,
      controlaStock: false,
    });
  });

  it('guardar envía sku null cuando el campo queda en blanco', async () => {
    await crear(null);
    component.sku = '';
    component.nombre = 'Producto Sin Sku';
    component.precioVenta = 100;

    component.guardar();

    expect(dialogRefSpy.close).toHaveBeenCalledWith(jasmine.objectContaining({ sku: null }));
  });
});
