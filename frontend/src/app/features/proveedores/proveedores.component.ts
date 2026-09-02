import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { ProveedorService, ProveedorRequest } from '../../core/services/proveedor.service';
import { AuthService } from '../../core/services/auth.service';
import { Proveedor } from '../../core/models/models';

@Component({
  selector: 'app-proveedores',
  standalone: true,
  imports: [CommonModule, FormsModule, MatTableModule, MatButtonModule, MatIconModule, MatCardModule],
  templateUrl: './proveedores.component.html',
  styleUrl: './proveedores.component.scss',
})
export class ProveedoresComponent implements OnInit {
  columnas = ['nombre', 'rut', 'email', 'telefono', 'acciones'];
  proveedores: Proveedor[] = [];
  error = '';
  guardando = false;
  editandoId: number | null = null;

  nombre = '';
  rut = '';
  email = '';
  telefono = '';
  direccion = '';

  constructor(private proveedorService: ProveedorService, public auth: AuthService) {}

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.proveedorService.listar().subscribe((proveedores) => (this.proveedores = proveedores));
  }

  editar(proveedor: Proveedor): void {
    this.editandoId = proveedor.id;
    this.nombre = proveedor.nombre;
    this.rut = proveedor.rut ?? '';
    this.email = proveedor.email ?? '';
    this.telefono = proveedor.telefono ?? '';
    this.direccion = proveedor.direccion ?? '';
  }

  cancelarEdicion(): void {
    this.editandoId = null;
    this.limpiarFormulario();
  }

  guardar(): void {
    if (!this.nombre) return;
    const request: ProveedorRequest = {
      nombre: this.nombre,
      rut: this.rut,
      email: this.email,
      telefono: this.telefono,
      direccion: this.direccion,
    };
    this.guardando = true;
    const obs = this.editandoId
      ? this.proveedorService.actualizar(this.editandoId, request)
      : this.proveedorService.crear(request);
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

  eliminar(proveedor: Proveedor): void {
    this.proveedorService.eliminar(proveedor.id).subscribe({
      next: () => {
        this.error = '';
        if (this.editandoId === proveedor.id) this.cancelarEdicion();
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
  }
}
