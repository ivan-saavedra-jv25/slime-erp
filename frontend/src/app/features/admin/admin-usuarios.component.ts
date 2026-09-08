import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { EmpresaAdminService } from '../../core/services/empresa-admin.service';
import { UsuarioPlataformaService, UsuarioAdminRequest } from '../../core/services/usuario-plataforma.service';
import { Empresa, Rol, UsuarioPlataforma } from '../../core/models/models';

const ROLES_ASIGNABLES: Rol[] = ['ADMIN', 'VENDEDOR', 'COMPRADOR', 'VISUALIZADOR'];

const ETIQUETAS_ROL: Record<Rol, string> = {
  SUPER_ADMIN: 'Super administrador',
  ADMIN: 'Administrador',
  VENDEDOR: 'Vendedor',
  COMPRADOR: 'Comprador',
  VISUALIZADOR: 'Visualizador',
};

@Component({
  selector: 'app-admin-usuarios',
  standalone: true,
  imports: [CommonModule, FormsModule, MatTableModule, MatButtonModule, MatIconModule, MatCardModule],
  templateUrl: './admin-usuarios.component.html',
  styleUrl: './admin-usuarios.component.scss',
})
export class AdminUsuariosComponent implements OnInit {
  roles = ROLES_ASIGNABLES;
  columnas = ['empresa', 'nombre', 'rut', 'email', 'rol', 'activo', 'fechaAlta', 'acciones'];
  empresas: Empresa[] = [];
  usuarios: UsuarioPlataforma[] = [];
  cargando = true;
  error = '';
  guardando = false;

  filtroEmpresa: number | null = null;
  filtroActivo: boolean | null = null;
  filtroTexto = '';

  editandoId: number | null = null;
  resetId: number | null = null;

  tenantId: number | null = null;
  nombre = '';
  rut = '';
  email = '';
  password = '';
  rol: Rol = 'VENDEDOR';

  constructor(
    private empresaService: EmpresaAdminService,
    private usuarioPlataformaService: UsuarioPlataformaService
  ) {}

  labelRol(rol: Rol): string {
    return ETIQUETAS_ROL[rol] ?? rol;
  }

  ngOnInit(): void {
    this.empresaService.listar({ limit: 100, page: 0 }).subscribe({
      next: (p) => (this.empresas = p.content),
      error: () => (this.error = 'No se pudieron cargar las empresas.'),
    });
    this.cargar();
  }

  cargar(): void {
    this.cargando = true;
    this.error = '';
    this.usuarioPlataformaService.listar().subscribe({
      next: (usuarios) => {
        this.usuarios = usuarios;
        this.cargando = false;
      },
      error: (err) => {
        this.cargando = false;
        this.error = err?.error?.error ?? 'OcurriÃ³ un error al cargar los usuarios.';
      },
    });
  }

  get usuariosFiltrados(): UsuarioPlataforma[] {
    const texto = this.filtroTexto.trim().toLowerCase();
    return this.usuarios.filter((u) => {
      if (this.filtroEmpresa && u.tenantId !== this.filtroEmpresa) return false;
      if (this.filtroActivo !== null && u.activo !== this.filtroActivo) return false;
      if (texto) {
        const hay = `${u.nombre} ${u.email} ${u.rut} ${u.tenantNombre}`.toLowerCase();
        if (!hay.includes(texto)) return false;
      }
      return true;
    });
  }

  get usuarioEnReset(): UsuarioPlataforma | null {
    return this.resetId ? this.usuarios.find((u) => u.id === this.resetId) ?? null : null;
  }

  nombreEmpresa(tenantId: number): string {
    return this.empresas.find((e) => e.id === tenantId)?.nombre ?? `Empresa #${tenantId}`;
  }

  editar(usuario: UsuarioPlataforma): void {
    this.cancelarReset();
    this.editandoId = usuario.id;
    this.tenantId = usuario.tenantId;
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
    const request: UsuarioAdminRequest = {
      tenantId: this.tenantId ?? undefined,
      nombre: this.nombre,
      rut: this.rut,
      email: this.email,
      rol: this.rol,
      ...(this.password ? { password: this.password } : {}),
    };
    this.guardando = true;
    const obs = this.editandoId
      ? this.usuarioPlataformaService.actualizar(this.editandoId, request)
      : this.usuarioPlataformaService.crear(request);
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
        this.error = err?.error?.error ?? 'OcurriÃ³ un error. Intenta nuevamente.';
      },
    });
  }

  toggleEstado(usuario: UsuarioPlataforma): void {
    const obs = usuario.activo
      ? this.usuarioPlataformaService.desactivar(usuario.id)
      : this.usuarioPlataformaService.activar(usuario.id);
    obs.subscribe({
      next: () => {
        this.error = '';
        if (this.editandoId === usuario.id) this.cancelarEdicion();
        this.cargar();
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'OcurriÃ³ un error. Intenta nuevamente.';
      },
    });
  }

  toggleReset(usuario: UsuarioPlataforma): void {
    this.resetId = this.resetId === usuario.id ? null : usuario.id;
    this.nuevaPasswordReseteo = '';
    this.errorReset = '';
  }

  cancelarReset(): void {
    this.resetId = null;
    this.nuevaPasswordReseteo = '';
    this.errorReset = '';
  }

  confirmarReset(usuario: UsuarioPlataforma | null): void {
    if (!usuario || !this.nuevaPasswordReseteo.trim()) return;
    this.usuarioPlataformaService.resetearPassword(usuario.id, this.nuevaPasswordReseteo).subscribe({
      next: () => {
        this.cancelarReset();
        this.error = '';
      },
      error: (err) => {
        this.errorReset = err?.error?.error ?? 'OcurriÃ³ un error al cambiar la contraseÃ±a.';
      },
    });
  }

  formularioCompleto(): boolean {
    if (!this.nombre || !this.rut || !this.email) return false;
    if (!this.editandoId) return !!(this.tenantId && this.password);
    return true;
  }

  private limpiarFormulario(): void {
    this.tenantId = null;
    this.nombre = '';
    this.rut = '';
    this.email = '';
    this.password = '';
    this.rol = 'VENDEDOR';
  }

  nuevaPasswordReseteo = '';
  errorReset = '';
}