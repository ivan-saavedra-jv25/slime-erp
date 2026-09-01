import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { UsuarioFormDialogComponent } from './usuario-form-dialog.component';

describe('UsuarioFormDialogComponent', () => {
  let fixture: ComponentFixture<UsuarioFormDialogComponent>;
  let component: UsuarioFormDialogComponent;
  let dialogRefSpy: jasmine.SpyObj<MatDialogRef<UsuarioFormDialogComponent>>;

  async function crear(data: any) {
    dialogRefSpy = jasmine.createSpyObj('MatDialogRef', ['close']);
    await TestBed.configureTestingModule({
      imports: [UsuarioFormDialogComponent],
      providers: [
        { provide: MatDialogRef, useValue: dialogRefSpy },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(UsuarioFormDialogComponent);
    component = fixture.componentInstance;
  }

  it('en modo creación arranca con campos vacíos y rol VENDEDOR', async () => {
    await crear(null);
    expect(component.esEdicion).toBeFalse();
    expect(component.nombre).toBe('');
    expect(component.rol).toBe('VENDEDOR');
  });

  it('en modo edición precarga los datos del usuario', async () => {
    await crear({ id: 5, nombre: 'Vendedor Uno', rut: '1-9', email: 'v1@demo.cl', rol: 'VENDEDOR', activo: true });
    expect(component.esEdicion).toBeTrue();
    expect(component.nombre).toBe('Vendedor Uno');
  });

  it('guardar cierra el dialog con el request armado, sin password si está vacío', async () => {
    await crear(null);
    component.nombre = 'Nuevo';
    component.rut = '2-9';
    component.email = 'nuevo@demo.cl';
    component.password = '';
    component.rol = 'ADMIN';

    component.guardar();

    expect(dialogRefSpy.close).toHaveBeenCalledWith({
      nombre: 'Nuevo',
      rut: '2-9',
      email: 'nuevo@demo.cl',
      rol: 'ADMIN',
    });
  });
});
