import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';
import { ClienteService, ClienteRequest } from '../../core/services/cliente.service';
import { AuthService } from '../../core/services/auth.service';
import { Cliente } from '../../core/models/models';
import { ClienteFormDialogComponent } from './cliente-form-dialog.component';

@Component({
  selector: 'app-clientes',
  standalone: true,
  imports: [CommonModule, MatTableModule, MatButtonModule, MatIconModule],
  templateUrl: './clientes.component.html',
  styleUrl: './clientes.component.scss',
})
export class ClientesComponent implements OnInit {
  columnas = ['nombre', 'rut', 'email', 'telefono', 'acciones'];
  clientes: Cliente[] = [];
  error = '';

  constructor(private clienteService: ClienteService, public auth: AuthService, private dialog: MatDialog) {}

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.clienteService.listar().subscribe((clientes) => (this.clientes = clientes));
  }

  abrirCrear(): void {
    const ref = this.dialog.open(ClienteFormDialogComponent, { width: '420px' });
    ref.afterClosed().subscribe((request: ClienteRequest | undefined) => {
      if (request) {
        this.clienteService.crear(request).subscribe({
          next: () => {
            this.error = '';
            this.cargar();
          },
          error: (err) => {
            this.error = err?.error?.error ?? 'Ocurrió un error. Intenta nuevamente.';
          },
        });
      }
    });
  }

  abrirEditar(cliente: Cliente): void {
    const ref = this.dialog.open(ClienteFormDialogComponent, { width: '420px', data: cliente });
    ref.afterClosed().subscribe((request: ClienteRequest | undefined) => {
      if (request) {
        this.clienteService.actualizar(cliente.id, request).subscribe({
          next: () => {
            this.error = '';
            this.cargar();
          },
          error: (err) => {
            this.error = err?.error?.error ?? 'Ocurrió un error. Intenta nuevamente.';
          },
        });
      }
    });
  }

  eliminar(cliente: Cliente): void {
    this.clienteService.eliminar(cliente.id).subscribe({
      next: () => {
        this.error = '';
        this.cargar();
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'Ocurrió un error. Intenta nuevamente.';
      },
    });
  }
}
