import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { UsuarioService, UsuarioRequest } from '../../core/services/usuario.service';
import { AuthService } from '../../core/services/auth.service';
import { Rol, Usuario } from '../../core/models/models';

const ROLES_ASIGNABLES: Rol[] = ['ADMIN', 'VENDEDOR', 'COMPRADOR', 'VISUALIZADOR'];

@Component({
  selector: 'app-usuarios',
  standalone: true,
  imports: [CommonModule, FormsModule, MatTableModule, MatButtonModule, MatIconModule, MatCardModule],
  templateUrl: './usuarios.component.html',
  styleUrl: './usuarios.component.scss',
})
export class UsuariosComponent implements OnInit {
  roles = ROLES_ASIGNABLES;
  columnas = ['nombre', 'email', 'rol', 'activo', 'acciones'];
  usuarios: Usuario[] = [];
  error = '';
  guardando = false;
  editandoId: number | null = null;

  nombre = '';
  rut = '';
  email = '';
  password = '';
  rol: Rol = 'VENDEDOR';

  constructor(private usuarioService: UsuarioService, public auth: AuthService) {}

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.usuarioService.listar().subscribe((usuarios) => (this.usuarios = usuarios));
  }

  editar(usuario: Usuario): void {
    this.editandoId = usuario.id;
    this.nombre = usuario.nombre;
    this.rut = usuario.rut;
    this.email = usuario.email;
    this.password = '';
    this.rol = usuario.rol;
  }

  cancelarEdicion(): void {
    this.editandoId = null;
    this.limpiarFormulario();
  }

  guardar(): void {
    if (!this.nombre || !this.rut || !this.email) return;
    const request: UsuarioRequest = {
      nombre: this.nombre,
      rut: this.rut,
      email: this.email,
      rol: this.rol,
      ...(this.password ? { password: this.password } : {}),
    };
    this.guardando = true;
    const obs = this.editandoId
      ? this.usuarioService.actualizar(this.editandoId, request)
      : this.usuarioService.crear(request);
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

  desactivar(usuario: Usuario): void {
    this.usuarioService.desactivar(usuario.id).subscribe({
      next: () => {
        this.error = '';
        if (this.editandoId === usuario.id) this.cancelarEdicion();
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

  private limpiarFormulario(): void {
    this.nombre = '';
    this.rut = '';
    this.email = '';
    this.password = '';
    this.rol = 'VENDEDOR';
  }
}
