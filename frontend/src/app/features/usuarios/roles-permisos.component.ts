import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { UsuarioService } from '../../core/services/usuario.service';
import { AuthService } from '../../core/services/auth.service';
import { Permiso, Rol, Usuario } from '../../core/models/models';

interface GrupoPermiso {
  titulo: string;
  permisos: { valor: Permiso; label: string }[];
}

const GRUPOS_PERMISOS: GrupoPermiso[] = [
  { titulo: 'Ventas', permisos: [
    { valor: 'VENTAS_VER', label: 'Ver ventas' },
    { valor: 'VENTAS_EDITAR', label: 'Registrar y editar ventas' },
  ] },
  { titulo: 'Compras', permisos: [
    { valor: 'COMPRAS_VER', label: 'Ver compras' },
    { valor: 'COMPRAS_EDITAR', label: 'Registrar y editar compras' },
  ] },
  { titulo: 'Clientes', permisos: [
    { valor: 'CLIENTES_VER', label: 'Ver clientes' },
    { valor: 'CLIENTES_EDITAR', label: 'Editar clientes' },
  ] },
  { titulo: 'Proveedores', permisos: [
    { valor: 'PROVEEDORES_VER', label: 'Ver proveedores' },
    { valor: 'PROVEEDORES_EDITAR', label: 'Editar proveedores' },
  ] },
  { titulo: 'Productos', permisos: [
    { valor: 'PRODUCTOS_VER', label: 'Ver productos' },
    { valor: 'PRODUCTOS_EDITAR', label: 'Editar productos' },
  ] },
  { titulo: 'Categorías', permisos: [
    { valor: 'CATEGORIAS_VER', label: 'Ver categorías' },
    { valor: 'CATEGORIAS_EDITAR', label: 'Editar categorías' },
  ] },
  { titulo: 'Bodegas', permisos: [
    { valor: 'BODEGAS_VER', label: 'Ver bodegas' },
    { valor: 'BODEGAS_EDITAR', label: 'Editar bodegas' },
  ] },
  { titulo: 'Formas de pago', permisos: [
    { valor: 'FORMAS_PAGO_VER', label: 'Ver formas de pago' },
    { valor: 'FORMAS_PAGO_EDITAR', label: 'Editar formas de pago' },
  ] },
  { titulo: 'Movimientos de inventario', permisos: [
    { valor: 'MOVIMIENTOS_VER', label: 'Ver movimientos' },
    { valor: 'MOVIMIENTOS_EDITAR', label: 'Registrar movimientos' },
  ] },
  { titulo: 'Tesorería', permisos: [
    { valor: 'TESORERIA_VER', label: 'Ver cuentas por cobrar' },
    { valor: 'TESORERIA_EDITAR', label: 'Registrar pagos' },
    { valor: 'TESORERIA_ANULAR', label: 'Anular cuentas y pagos' },
  ] },
  { titulo: 'Usuarios', permisos: [
    { valor: 'USUARIOS_VER', label: 'Ver usuarios' },
    { valor: 'USUARIOS_EDITAR', label: 'Editar usuarios' },
  ] },
  { titulo: 'Empresas', permisos: [
    { valor: 'EMPRESAS_ADMINISTRAR', label: 'Administrar empresas' },
  ] },
];

@Component({
  selector: 'app-roles-permisos',
  standalone: true,
  imports: [CommonModule, FormsModule, MatButtonModule, MatIconModule, MatCardModule],
  templateUrl: './roles-permisos.component.html',
  styleUrl: './roles-permisos.component.scss',
})
export class RolesPermisosComponent implements OnInit {
  grupos = GRUPOS_PERMISOS;
  usuarios: Usuario[] = [];
  usuarioSeleccionadoId: number | null = null;
  rolUsuario: Rol | null = null;
  permisosRol = new Set<Permiso>();
  permisosMarcados = new Set<Permiso>();
  cargando = false;
  guardando = false;
  error = '';
  mensajeExito = '';

  constructor(private usuarioService: UsuarioService, public auth: AuthService) {}

  ngOnInit(): void {
    this.usuarioService.listar().subscribe((data) => (this.usuarios = data));
  }

  seleccionarUsuario(): void {
    this.mensajeExito = '';
    this.error = '';
    if (!this.usuarioSeleccionadoId) {
      this.rolUsuario = null;
      this.permisosRol = new Set();
      this.permisosMarcados = new Set();
      return;
    }
    this.cargando = true;
    this.usuarioService.obtenerPermisos(this.usuarioSeleccionadoId).subscribe({
      next: (data) => {
        this.rolUsuario = data.rol;
        this.permisosRol = new Set(data.permisosRol);
        this.permisosMarcados = new Set([...data.permisosRol, ...data.permisosExtra]);
        this.cargando = false;
      },
      error: () => {
        this.error = 'No se pudo cargar los permisos del usuario.';
        this.cargando = false;
      },
    });
  }

  esDelRol(permiso: Permiso): boolean {
    return this.permisosRol.has(permiso);
  }

  estaMarcado(permiso: Permiso): boolean {
    return this.permisosMarcados.has(permiso);
  }

  alternar(permiso: Permiso): void {
    if (this.esDelRol(permiso)) return;
    if (this.permisosMarcados.has(permiso)) {
      this.permisosMarcados.delete(permiso);
    } else {
      this.permisosMarcados.add(permiso);
    }
  }

  guardar(): void {
    if (!this.usuarioSeleccionadoId) return;
    const permisosExtra = [...this.permisosMarcados].filter((p) => !this.permisosRol.has(p));
    this.guardando = true;
    this.error = '';
    this.mensajeExito = '';
    this.usuarioService.guardarPermisosExtra(this.usuarioSeleccionadoId, permisosExtra).subscribe({
      next: () => {
        this.guardando = false;
        this.mensajeExito = 'Permisos actualizados.';
      },
      error: () => {
        this.guardando = false;
        this.error = 'Ocurrió un error al guardar los permisos.';
      },
    });
  }
}
