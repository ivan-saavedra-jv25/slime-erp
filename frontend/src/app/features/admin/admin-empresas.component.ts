import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { Router, ActivatedRoute } from '@angular/router';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { EmpresaAdminService } from '../../core/services/empresa-admin.service';
import {
  Empresa,
  CrearEmpresaRequest,
  EstadoEmpresa,
  ESTADOS_EMPRESA,
  ETIQUETAS_ESTADO_EMPRESA,
} from '../../core/models/models';
import { ConfirmActionDialog } from '../../core/components/confirm-action-dialog/confirm-action-dialog.component';
import { MonedaPipe } from '../../core/pipes/moneda.pipe';
import { cerrarCargando, mostrarCargando } from '../../core/utils/swal-loading';

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
    MatDialogModule,
    MonedaPipe,
  ],
  templateUrl: './admin-empresas.component.html',
  styleUrl: './admin-empresas.component.scss',
})
export class AdminEmpresasComponent implements OnInit, OnDestroy {
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

  private readonly busqueda$ = new Subject<string>();
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  constructor(private empresaService: EmpresaAdminService) {}

  ngOnInit(): void {
    this.recuperarEstadoDesdeURL();
    this.busqueda$.pipe(debounceTime(400), distinctUntilChanged()).subscribe(() => this.aplicarFiltros());
    this.cargar();
  }

  ngOnDestroy(): void {
    this.busqueda$.complete();
  }

  recuperarEstadoDesdeURL(): void {
    const qp = this.route.snapshot.queryParamMap;
    this.filtroTexto = qp.get('q') ?? '';
    this.filtroEstado = (qp.get('estado') as EstadoEmpresa | '') ?? '';
    this.pagina = Number(qp.get('page') ?? 0) || 0;
    this.tamanio = Number(qp.get('limit') ?? 10) || 10;
  }

  onBusqueda(): void {
    this.busqueda$.next(this.filtroTexto);
  }

  cargar(): void {
    const texto = this.filtroTexto.trim();
    const idBusqueda = /^\d+$/.test(texto) ? Number(texto) : undefined;
    this.empresaService
      .listar({
        page: this.pagina,
        limit: this.tamanio,
        ...(idBusqueda !== undefined ? { id: idBusqueda } : {}),
        ...(this.filtroEstado ? { estado: this.filtroEstado } : {}),
        ...(texto && idBusqueda === undefined ? { razonSocial: texto } : {}),
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
    this.persistirEstado();
    this.cargar();
  }

  onPagina(evento: PageEvent): void {
    this.pagina = evento.pageIndex;
    this.tamanio = evento.pageSize;
    this.persistirEstado();
    this.cargar();
  }

  private persistirEstado(): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        q: this.filtroTexto.trim() || null,
        estado: this.filtroEstado || null,
        page: this.pagina || null,
        limit: this.tamanio !== 10 ? this.tamanio : null,
      },
      queryParamsHandling: 'merge',
    });
  }

  irDetalle(empresa: Empresa): void {
    this.router.navigate(['/admin/empresas', empresa.id]);
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
    const dialogRef = this.dialog.open(ConfirmActionDialog, {
      width: '460px',
      data: {
        titulo: `${ETIQUETAS_ESTADO_EMPRESA[this.nuevoEstado]} empresa`,
        entidad: empresa.nombre,
        accion: `Cambiar el estado a "${ETIQUETAS_ESTADO_EMPRESA[this.nuevoEstado]}"`,
      },
    });
    dialogRef.afterClosed().subscribe((motivo?: string) => {
      if (!motivo) return;
      this.empresaService.cambiarEstado(empresa.id, { estado: this.nuevoEstado as EstadoEmpresa, motivo }).subscribe({
        next: () => {
          this.cambiandoEstadoId = null;
          this.nuevoEstado = '';
          this.error = '';
          this.cargar();
        },
        error: (err) => {
          this.error = err?.error?.error ?? 'Ocurrió un error al cambiar el estado.';
        },
      });
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
    mostrarCargando('Creando empresa');
    this.empresaService.crear(request).subscribe({
      next: () => {
        cerrarCargando();
        this.error = '';
        this.guardando = false;
        this.limpiarFormulario();
        this.cargar();
      },
      error: (err) => {
        cerrarCargando();
        this.guardando = false;
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