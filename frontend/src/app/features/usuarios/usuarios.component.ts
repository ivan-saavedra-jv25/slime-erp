import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';
import { UsuarioService, UsuarioRequest } from '../../core/services/usuario.service';
import { AuthService } from '../../core/services/auth.service';
import { Usuario } from '../../core/models/models';
import { UsuarioFormDialogComponent } from './usuario-form-dialog.component';

@Component({
  selector: 'app-usuarios',
  standalone: true,
  imports: [CommonModule, MatTableModule, MatButtonModule, MatIconModule],
  templateUrl: './usuarios.component.html',
  styleUrl: './usuarios.component.scss',
})
export class UsuariosComponent implements OnInit {
  columnas = ['nombre', 'email', 'rol', 'activo', 'acciones'];
  usuarios: Usuario[] = [];
  error = '';

  constructor(private usuarioService: UsuarioService, public auth: AuthService, private dialog: MatDialog) {}

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.usuarioService.listar().subscribe((usuarios) => (this.usuarios = usuarios));
  }

  abrirCrear(): void {
    const ref = this.dialog.open(UsuarioFormDialogComponent, { width: '420px' });
    ref.afterClosed().subscribe((request: UsuarioRequest | undefined) => {
      if (request) {
        this.usuarioService.crear(request).subscribe({
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

  abrirEditar(usuario: Usuario): void {
    const ref = this.dialog.open(UsuarioFormDialogComponent, { width: '420px', data: usuario });
    ref.afterClosed().subscribe((request: UsuarioRequest | undefined) => {
      if (request) {
        this.usuarioService.actualizar(usuario.id, request).subscribe({
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

  desactivar(usuario: Usuario): void {
    this.usuarioService.desactivar(usuario.id).subscribe({
      next: () => {
        this.error = '';
        this.cargar();
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'Ocurrió un error. Intenta nuevamente.';
      },
    });
  }

  reactivar(usuario: Usuario): void {
    this.usuarioService
      .actualizar(usuario.id, {
        nombre: usuario.nombre,
        rut: usuario.rut,
        email: usuario.email,
        rol: usuario.rol,
        activo: true,
      })
      .subscribe({
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
