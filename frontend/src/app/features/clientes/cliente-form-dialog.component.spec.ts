import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { ClienteFormDialogComponent } from './cliente-form-dialog.component';

describe('ClienteFormDialogComponent', () => {
  let fixture: ComponentFixture<ClienteFormDialogComponent>;
  let component: ClienteFormDialogComponent;
  let dialogRefSpy: jasmine.SpyObj<MatDialogRef<ClienteFormDialogComponent>>;

  async function crear(data: any) {
    dialogRefSpy = jasmine.createSpyObj('MatDialogRef', ['close']);
    await TestBed.configureTestingModule({
      imports: [ClienteFormDialogComponent],
      providers: [
        { provide: MatDialogRef, useValue: dialogRefSpy },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ClienteFormDialogComponent);
    component = fixture.componentInstance;
  }

  it('en modo creación arranca con campos vacíos', async () => {
    await crear(null);
    expect(component.esEdicion).toBeFalse();
    expect(component.nombre).toBe('');
  });

  it('en modo edición precarga los datos del cliente', async () => {
    await crear({ id: 2, nombre: 'Cliente Dos', rut: '2-9', email: 'c2@demo.cl', telefono: null, direccion: null, activo: true });
    expect(component.esEdicion).toBeTrue();
    expect(component.nombre).toBe('Cliente Dos');
  });

  it('guardar cierra el dialog con el request armado', async () => {
    await crear(null);
    component.nombre = 'Nuevo Cliente';
    component.rut = '3-9';
    component.email = 'nuevo@demo.cl';
    component.telefono = '+56911111111';
    component.direccion = 'Calle 1';

    component.guardar();

    expect(dialogRefSpy.close).toHaveBeenCalledWith({
      nombre: 'Nuevo Cliente',
      rut: '3-9',
      email: 'nuevo@demo.cl',
      telefono: '+56911111111',
      direccion: 'Calle 1',
    });
  });
});
