import { Component, inject, LOCALE_ID, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { EmpresaService } from '../../core/services/empresa.service';
import { Empresa, CrearEmpresaRequest } from '../../core/models/models';
import { MonedaPipe } from '../../core/pipes/moneda.pipe';

@Component({
  selector: 'app-admin-empresas',
  standalone: true,
  imports: [CommonModule, FormsModule, MatTableModule, MatButtonModule, MatIconModule, MatCardModule, MonedaPipe],
  templateUrl: './admin-empresas.component.html',
  styleUrl: './admin-empresas.component.scss',
})
export class AdminEmpresasComponent implements OnInit {
  columnas = ['nombre', 'rut', 'plan', 'usuariosActivos', 'saldoPendiente', 'activo', 'fechaAlta', 'acciones'];
  empresas: Empresa[] = [];
  error = '';
  guardando = false;

  nombre = '';
  rut = '';
  plan = '';
  adminNombre = '';
  adminRut = '';
  adminEmail = '';
  adminPassword = '';

  constructor(private empresaService: EmpresaService) {}

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.empresaService.listar().subscribe({
      next: (empresas) => (this.empresas = empresas),
      error: (err) => (this.error = err?.error?.error ?? 'Ocurrió un error al cargar las empresas.'),
    });
  }

  guardar(): void {
    if (!this.nombre || !this.rut || !this.adminNombre || !this.adminRut || !this.adminEmail || !this.adminPassword) return;
    const request: CrearEmpresaRequest = {
      nombre: this.nombre,
      rut: this.rut,
      ...(this.plan ? { plan: this.plan } : {}),
      adminNombre: this.adminNombre,
      adminRut: this.adminRut,
      adminEmail: this.adminEmail,
      adminPassword: this.adminPassword,
    };
    this.guardando = true;
    this.empresaService.crear(request).subscribe({
      next: () => {
        this.error = '';
        this.guardando = false;
        this.limpiarFormulario();
        this.cargar();
      },
      error: (err) => {
        this.guardando = false;
        this.error = err?.error?.error ?? 'Ocurrió un error. Intenta nuevamente.';
      },
    });
  }

  toggleEstado(empresa: Empresa): void {
    const accion = empresa.activo ? this.empresaService.desactivar(empresa.id) : this.empresaService.activar(empresa.id);
    accion.subscribe({
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
    this.plan = '';
    this.adminNombre = '';
    this.adminRut = '';
    this.adminEmail = '';
    this.adminPassword = '';
  }

  formularioCompleto(): boolean {
    return !!(this.nombre && this.rut && this.adminNombre && this.adminRut && this.adminEmail && this.adminPassword);
  }
}