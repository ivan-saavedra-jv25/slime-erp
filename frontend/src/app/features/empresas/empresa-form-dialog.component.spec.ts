import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { EmpresaFormDialogComponent } from './empresa-form-dialog.component';

describe('EmpresaFormDialogComponent', () => {
  let fixture: ComponentFixture<EmpresaFormDialogComponent>;
  let component: EmpresaFormDialogComponent;
  let dialogRefSpy: jasmine.SpyObj<MatDialogRef<EmpresaFormDialogComponent>>;

  beforeEach(async () => {
    dialogRefSpy = jasmine.createSpyObj('MatDialogRef', ['close']);
    await TestBed.configureTestingModule({
      imports: [EmpresaFormDialogComponent],
      providers: [{ provide: MatDialogRef, useValue: dialogRefSpy }],
    }).compileComponents();
    fixture = TestBed.createComponent(EmpresaFormDialogComponent);
    component = fixture.componentInstance;
  });

  it('arranca con todos los campos vacíos', () => {
    expect(component.nombre).toBe('');
    expect(component.plan).toBe('');
  });

  it('guardar cierra el dialog con el request armado, sin plan si está vacío', () => {
    component.nombre = 'Empresa Nueva';
    component.rut = '76.111.222-3';
    component.plan = '';
    component.adminNombre = 'Admin Uno';
    component.adminRut = '1-9';
    component.adminEmail = 'a1@demo.cl';
    component.adminPassword = 'clave123';

    component.guardar();

    expect(dialogRefSpy.close).toHaveBeenCalledWith({
      nombre: 'Empresa Nueva',
      rut: '76.111.222-3',
      adminNombre: 'Admin Uno',
      adminRut: '1-9',
      adminEmail: 'a1@demo.cl',
      adminPassword: 'clave123',
    });
  });

  it('guardar incluye plan si fue ingresado', () => {
    component.nombre = 'Empresa Nueva';
    component.rut = '76.111.222-3';
    component.plan = 'premium';
    component.adminNombre = 'Admin Uno';
    component.adminRut = '1-9';
    component.adminEmail = 'a1@demo.cl';
    component.adminPassword = 'clave123';

    component.guardar();

    expect(dialogRefSpy.close).toHaveBeenCalledWith(jasmine.objectContaining({ plan: 'premium' }));
  });
});
