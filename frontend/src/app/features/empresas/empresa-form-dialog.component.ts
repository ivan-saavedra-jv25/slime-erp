import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { CrearEmpresaRequest } from '../../core/models/models';

@Component({
  selector: 'app-empresa-form-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  templateUrl: './empresa-form-dialog.component.html',
})
export class EmpresaFormDialogComponent {
  nombre = '';
  rut = '';
  plan = '';
  adminNombre = '';
  adminRut = '';
  adminEmail = '';
  adminPassword = '';

  constructor(private ref: MatDialogRef<EmpresaFormDialogComponent, CrearEmpresaRequest>) {}

  guardar(): void {
    const request: CrearEmpresaRequest = {
      nombre: this.nombre,
      rut: this.rut,
      ...(this.plan ? { plan: this.plan } : {}),
      adminNombre: this.adminNombre,
      adminRut: this.adminRut,
      adminEmail: this.adminEmail,
      adminPassword: this.adminPassword,
    };
    this.ref.close(request);
  }

  cancelar(): void {
    this.ref.close();
  }
}
