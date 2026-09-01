import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { Cliente } from '../../core/models/models';
import { ClienteRequest } from '../../core/services/cliente.service';

@Component({
  selector: 'app-cliente-form-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  templateUrl: './cliente-form-dialog.component.html',
})
export class ClienteFormDialogComponent {
  esEdicion: boolean;

  nombre: string;
  rut: string;
  email: string;
  telefono: string;
  direccion: string;

  constructor(
    private ref: MatDialogRef<ClienteFormDialogComponent, ClienteRequest>,
    @Inject(MAT_DIALOG_DATA) public data: Cliente | null
  ) {
    this.esEdicion = !!this.data;
    this.nombre = this.data?.nombre ?? '';
    this.rut = this.data?.rut ?? '';
    this.email = this.data?.email ?? '';
    this.telefono = this.data?.telefono ?? '';
    this.direccion = this.data?.direccion ?? '';
  }

  guardar(): void {
    const request: ClienteRequest = {
      nombre: this.nombre,
      rut: this.rut,
      email: this.email,
      telefono: this.telefono,
      direccion: this.direccion,
    };
    this.ref.close(request);
  }

  cancelar(): void {
    this.ref.close();
  }
}
