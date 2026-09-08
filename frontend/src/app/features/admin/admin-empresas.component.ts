import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { EmpresaService } from '../../core/services/empresa.service';
import {
  Empresa,
  CrearEmpresaRequest,
  EstadoEmpresa,
  ESTADOS_EMPRESA,
  ETIQUETAS_ESTADO_EMPRESA,
} from '../../core/models/models';
import { MonedaPipe } from '../../core/pipes/moneda.pipe';

@Component({
  selector: 'app-admin-empresas',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatPaginatorModule,
    MonedaPipe,
  ],
  templateUrl: './admin-empresas.component.html',
  styleUrl: './admin-empresas.component.scss',
})
export class AdminEmpresasComponent implements OnInit {
  columnas = ['nombre', 'rut', 'plan', 'usuariosActivos', 'saldoPendiente', 'status', 'fechaAlta', 'acciones'];
  empresas: Empresa[] = [];
  error = '';
  guardando = false;

  totalElementos = 0;
  pagina = 0;
  tamanio = 10;

  filtroTexto = '';
  filtroEstado: EstadoEmpresa | '' = '';
  estados = ESTADOS_EMPRESA;

  cambiandoEstadoId: number | null = null;
  nuevoEstado: EstadoEmpresa | '' = '';

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
    const texto = this.filtroTexto.trim();
    this.empresaService
      .listar({
        page: this.pagina,
        limit: this.tamanio,
        ...(texto ? { razonSocial: texto } : {}),
        ...(this.filtroEstado ? { estado: this.filtroEstado } : {}),
      })
      .subscribe({
        next: (p) => {
          this.empresas = p.content;
          this.totalElementos = p.totalElements;
          this.error = '';
        },
        error: (err) => (this.error = err?.error?.error ?? 'Ocurrió un error al cargar las empresas.'),
      });
  }

  aplicarFiltros(): void {
    this.pagina = 0;
    this.cargar();
  }

  onPagina(evento: PageEvent): void {
    this.pagina = evento.pageIndex;
    this.tamanio = evento.pageSize;
    this.cargar();
  }

  etiquetaEstado(estado: EstadoEmpresa): string {
    return ETIQUETAS_ESTADO_EMPRESA[estado] ?? estado;
  }

  claseEstado(estado: EstadoEmpresa): string {
    switch (estado) {
      case 'ACTIVE':
        return 'tag--success';
      case 'TRIAL':
        return 'tag--info';
      case 'EXPIRED':
        return 'tag--warning';
      case 'SUSPENDED':
      case 'BLOCKED':
      case 'CANCELLED':
        return 'tag--error';
      default:
        return 'tag--neutral';
    }
  }

  abrirCambioEstado(empresa: Empresa): void {
    this.cambiandoEstadoId = this.cambiandoEstadoId === empresa.id ? null : empresa.id;
    this.nuevoEstado = '';
    this.error = '';
  }

  confirmarCambioEstado(empresa: Empresa): void {
    if (!this.nuevoEstado) return;
    this.empresaService.cambiarEstado(empresa.id, { estado: this.nuevoEstado, motivo: 'Cambio desde consola admin' }).subscribe({
      next: () => {
        this.cambiandoEstadoId = null;
        this.nuevoEstado = '';
        this.cargar();
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'Ocurrió un error al cambiar el estado.';
      },
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