import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatTabsModule } from '@angular/material/tabs';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatTableModule } from '@angular/material/table';
import { EmpresaAdminService } from '../../../core/services/empresa-admin.service';
import { UsuarioPlataformaService } from '../../../core/services/usuario-plataforma.service';
import { PagosService } from '../../../core/services/pagos.service';
import {
  EmpresaDetalle,
  EstadoEmpresa,
  ESTADOS_EMPRESA,
  ETIQUETAS_ESTADO_EMPRESA,
  PagoPlataforma,
  Rol,
  UsuarioPlataforma,
} from '../../../core/models/models';
import { ConfirmActionDialog } from '../../../core/components/confirm-action-dialog/confirm-action-dialog.component';

const ETIQUETAS_ROL: Record<Rol, string> = {
  SUPER_ADMIN: 'Super administrador',
  ADMIN: 'Administrador',
  VENDEDOR: 'Vendedor',
  COMPRADOR: 'Comprador',
  VISUALIZADOR: 'Visualizador',
};

@Component({
  selector: 'app-empresas-detalle',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatTabsModule,
    MatButtonModule,
    MatIconModule,
    MatDialogModule,
    MatTableModule,
  ],
  templateUrl: './empresas-detalle.component.html',
  styleUrl: './empresas-detalle.component.scss',
})
export class EmpresasDetalleComponent implements OnInit {
  empresa: EmpresaDetalle | null = null;
  cargando = true;
  error = '';
  estados = ESTADOS_EMPRESA;
  nuevoEstado: EstadoEmpresa | '' = '';

  usuarios: UsuarioPlataforma[] = [];
  cargandoUsuarios = false;
  errorUsuarios = '';

  pagos: PagoPlataforma[] = [];
  cargandoPagos = false;
  errorPagos = '';

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly empresaService = inject(EmpresaAdminService);
  private readonly usuarioPlataformaService = inject(UsuarioPlataformaService);
  private readonly pagosService = inject(PagosService);

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.cargar(id);
  }

  cargar(id: number): void {
    this.cargando = true;
    this.error = '';
    this.empresaService.detalle(id).subscribe({
      next: (empresa) => {
        this.empresa = empresa;
        this.cargando = false;
        this.cargarUsuarios(id);
        this.cargarPagos(id);
      },
      error: (err) => {
        this.cargando = false;
        this.error = err?.error?.error ?? 'No se pudo cargar la empresa.';
      },
    });
  }

  cargarUsuarios(id: number): void {
    this.cargandoUsuarios = true;
    this.errorUsuarios = '';
    this.usuarioPlataformaService.listarUsuariosEmpresa(id).subscribe({
      next: (usuarios) => {
        this.usuarios = usuarios;
        this.cargandoUsuarios = false;
      },
      error: () => {
        this.cargandoUsuarios = false;
        this.errorUsuarios = 'No se pudieron cargar los usuarios de esta empresa.';
      },
    });
  }

  cargarPagos(id: number): void {
    this.cargandoPagos = true;
    this.errorPagos = '';
    this.pagosService.listar({ page: 0, limit: 30, empresaId: id }).subscribe({
      next: (resultado) => {
        this.pagos = resultado.content;
        this.cargandoPagos = false;
      },
      error: () => {
        this.cargandoPagos = false;
        this.errorPagos = 'No se pudieron cargar los pagos de esta empresa.';
      },
    });
  }

  etiquetaMetodoPago(metodo: string): string {
    switch (metodo) {
      case 'TRANSFERENCIA':
        return 'Transferencia';
      case 'TARJETA':
        return 'Tarjeta';
      case 'EFECTIVO':
        return 'Efectivo';
      case 'CHEQUE':
        return 'Cheque';
      default:
        return metodo;
    }
  }

  labelRol(rol: Rol): string {
    return ETIQUETAS_ROL[rol] ?? rol;
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

  confirmarCambioEstado(): void {
    if (!this.empresa || !this.nuevoEstado) return;
    this.dialog.open(ConfirmActionDialog, {
      width: '460px',
      data: {
        titulo: `${ETIQUETAS_ESTADO_EMPRESA[this.nuevoEstado]} empresa`,
        entidad: this.empresa.nombre,
        accion: `Cambiar el estado a "${ETIQUETAS_ESTADO_EMPRESA[this.nuevoEstado]}"`,
      },
    }).afterClosed().subscribe((motivo?: string) => {
      if (!motivo || !this.empresa) return;
      this.empresaService
        .cambiarEstado(this.empresa.id, { estado: this.nuevoEstado as EstadoEmpresa, motivo })
        .subscribe({
          next: () => {
            this.nuevoEstado = '';
            this.cargar(this.empresa!.id);
          },
          error: (err) => {
            this.error = err?.error?.error ?? 'Ocurrió un error al cambiar el estado.';
          },
        });
    });
  }

  confirmarAccionUsuario(usuario: UsuarioPlataforma, titulo: string, accion: string): void {
    this.errorUsuarios = '';
    this.dialog.open(ConfirmActionDialog, {
      width: '460px',
      data: { titulo, entidad: `${usuario.nombre} (${usuario.email})`, accion },
    }).afterClosed().subscribe((motivo?: string) => {
      if (!motivo || !this.empresa) return;
      const id = this.empresa!.id;
      const manejador = {
        next: () => this.cargarUsuarios(id),
        error: (err: unknown) => {
          this.errorUsuarios = (err as { error?: { error?: string } })?.error?.error
            ?? 'Ocurrió un error. Intenta nuevamente.';
        },
      };
      if (accion.startsWith('Cambiar el estado')) {
        this.usuarioPlataformaService.cambiarEstadoUsuario(usuario.id, usuario.activo, motivo)
          .subscribe(manejador);
      } else if (accion.includes('bloquear')) {
        this.usuarioPlataformaService.bloquear(usuario.id, motivo)
          .subscribe(manejador);
      } else {
        this.usuarioPlataformaService.revocarSesiones(usuario.id, motivo)
          .subscribe(manejador);
      }
    });
  }

  habilitarUsuario(usuario: UsuarioPlataforma): void {
    this.confirmarAccionUsuario(usuario, 'Habilitar usuario', 'Cambiar el estado a "Activo"');
  }

  deshabilitarUsuario(usuario: UsuarioPlataforma): void {
    this.confirmarAccionUsuario(usuario, 'Deshabilitar usuario', 'Cambiar el estado a "Inactivo"');
  }

  bloquearUsuario(usuario: UsuarioPlataforma): void {
    this.confirmarAccionUsuario(usuario, 'Bloquear usuario', 'Bloquear a este usuario');
  }

  revocarSesiones(usuario: UsuarioPlataforma): void {
    this.confirmarAccionUsuario(usuario, 'Revocar sesiones', 'Revocar todas las sesiones de este usuario');
  }

  volver(): void {
    this.router.navigate(['/admin/empresas']);
  }
}