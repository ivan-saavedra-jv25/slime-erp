import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { ClienteService, ClienteRequest } from '../../core/services/cliente.service';
import { AuthService } from '../../core/services/auth.service';
import { Cliente } from '../../core/models/models';

@Component({
  selector: 'app-clientes',
  standalone: true,
  imports: [CommonModule, FormsModule, MatTableModule, MatButtonModule, MatIconModule, MatCardModule],
  templateUrl: './clientes.component.html',
  styleUrl: './clientes.component.scss',
})
export class ClientesComponent implements OnInit {
  columnas = ['nombre', 'rut', 'email', 'telefono', 'acciones'];
  clientes: Cliente[] = [];
  error = '';
  guardando = false;
  editandoId: number | null = null;

  nombre = '';
  rut = '';
  email = '';
  telefono = '';
  direccion = '';
  razonSocial = '';
  giro = '';
  comuna = '';
  ciudad = '';

  constructor(private clienteService: ClienteService, public auth: AuthService) {}

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.clienteService.listar().subscribe((clientes) => (this.clientes = clientes));
  }

  editar(cliente: Cliente): void {
    this.editandoId = cliente.id;
    this.nombre = cliente.nombre;
    this.rut = cliente.rut ?? '';
    this.email = cliente.email ?? '';
    this.telefono = cliente.telefono ?? '';
    this.direccion = cliente.direccion ?? '';
    this.razonSocial = cliente.razonSocial ?? '';
    this.giro = cliente.giro ?? '';
    this.comuna = cliente.comuna ?? '';
    this.ciudad = cliente.ciudad ?? '';
  }

  cancelarEdicion(): void {
    this.editandoId = null;
    this.limpiarFormulario();
  }

  guardar(): void {
    if (!this.nombre) return;
    const request: ClienteRequest = {
      nombre: this.nombre,
      rut: this.rut,
      email: this.email,
      telefono: this.telefono,
      direccion: this.direccion,
      razonSocial: this.razonSocial,
      giro: this.giro,
      comuna: this.comuna,
      ciudad: this.ciudad,
    };
    this.guardando = true;
    const obs = this.editandoId
      ? this.clienteService.actualizar(this.editandoId, request)
      : this.clienteService.crear(request);
    obs.subscribe({
      next: () => {
        this.error = '';
        this.guardando = false;
        this.editandoId = null;
        this.limpiarFormulario();
        this.cargar();
      },
      error: (err) => {
        this.guardando = false;
        this.error = err?.error?.error ?? 'Ocurrió un error. Intenta nuevamente.';
      },
    });
  }

  eliminar(cliente: Cliente): void {
    this.clienteService.eliminar(cliente.id).subscribe({
      next: () => {
        this.error = '';
        if (this.editandoId === cliente.id) this.cancelarEdicion();
        this.cargar();
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'Ocurrió un error. Intenta nuevamente.';
      },
    });
  }

  private limpiarFormulario(): void {
    this.nombre = '';
    this.rut = '';
    this.email = '';
    this.telefono = '';
    this.direccion = '';
    this.razonSocial = '';
    this.giro = '';
    this.comuna = '';
    this.ciudad = '';
  }
}
