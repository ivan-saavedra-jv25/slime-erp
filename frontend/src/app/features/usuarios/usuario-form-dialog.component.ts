import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { Rol, Usuario } from '../../core/models/models';
import { UsuarioRequest } from '../../core/services/usuario.service';

const ROLES_ASIGNABLES: Rol[] = ['ADMIN', 'VENDEDOR', 'COMPRADOR', 'VISUALIZADOR'];

@Component({
  selector: 'app-usuario-form-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule],
  templateUrl: './usuario-form-dialog.component.html',
})
export class UsuarioFormDialogComponent {
  roles = ROLES_ASIGNABLES;
  esEdicion: boolean;

  nombre: string;
  rut: string;
  email: string;
  password = '';
  rol: Rol;

  constructor(
    private ref: MatDialogRef<UsuarioFormDialogComponent, UsuarioRequest>,
    @Inject(MAT_DIALOG_DATA) public data: Usuario | null
  ) {
    this.esEdicion = !!this.data;
    this.nombre = this.data?.nombre ?? '';
    this.rut = this.data?.rut ?? '';
    this.email = this.data?.email ?? '';
    this.rol = this.data?.rol ?? 'VENDEDOR';
  }

  guardar(): void {
    const request: UsuarioRequest = {
      nombre: this.nombre,
      rut: this.rut,
      email: this.email,
      rol: this.rol,
      ...(this.password ? { password: this.password } : {}),
    };
    this.ref.close(request);
  }

  cancelar(): void {
    this.ref.close();
  }
}
