import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { CobranzaService } from '../../core/services/cobranza.service';
import { EmpresaService } from '../../core/services/empresa.service';
import { CobranzaEmpresa, EstadoCobranza, Empresa, ResumenCobranza } from '../../core/models/models';
import { MonedaPipe } from '../../core/pipes/moneda.pipe';

const ETIQUETAS: Record<EstadoCobranza, string> = {
  DEUDA: 'En deuda',
  PARCIAL: 'Parcial',
  PAGADO: 'Pagado',
  ANULADO: 'Anulado',
};

const TAGS: Record<EstadoCobranza, string> = {
  DEUDA: 'tag--error',
  PARCIAL: 'tag--warning',
  PAGADO: 'tag--success',
  ANULADO: '',
};

@Component({
  selector: 'app-admin-cobranza',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, MatButtonModule, MatIconModule, MatCardModule, MonedaPipe],
  templateUrl: './admin-cobranza.component.html',
  styleUrl: './admin-cobranza.component.scss',
})
export class AdminCobranzaComponent implements OnInit {
  cargos: CobranzaEmpresa[] = [];
  empresas: Empresa[] = [];
  resumen: ResumenCobranza | null = null;
  cargando = true;
  error = '';
  guardando = false;

  filtroEmpresa: number | null = null;
  filtroEstado: EstadoCobranza | null = null;

  nuevaTenantId: number | null = null;
  nuevoConcepto = '';
  nuevoPeriodo = '';
  nuevoMonto: number | null = null;
  nuevaVencimiento = '';
  nuevasObservaciones = '';

  constructor(
    private cobranzaService: CobranzaService,
    private empresaService: EmpresaService
  ) {}

  ngOnInit(): void {
    this.empresaService.listar().subscribe({
      next: (empresas) => (this.empresas = empresas),
      error: () => (this.error = 'No se pudieron cargar las empresas.'),
    });
    this.cobranzaService.resumen().subscribe((data) => (this.resumen = data));
    this.cargar();
  }

  cargar(): void {
    this.cargando = true;
    this.error = '';
    this.cobranzaService.listar().subscribe({
      next: (cargos) => {
        this.cargos = cargos;
        this.cargando = false;
      },
      error: (err) => {
        this.cargando = false;
        this.error = err?.error?.error ?? 'OcurriÃ³ un error al cargar la cobranza.';
      },
    });
  }

  get cargosFiltrados(): CobranzaEmpresa[] {
    return this.cargos.filter((c) => {
      if (this.filtroEmpresa && c.tenantId !== this.filtroEmpresa) return false;
      if (this.filtroEstado && c.estado !== this.filtroEstado) return false;
      return true;
    });
  }

  nombreEmpresa(tenantId: number): string {
    return this.empresas.find((e) => e.id === tenantId)?.nombre ?? `Empresa #${tenantId}`;
  }

  etiquetaEstado(estado: EstadoCobranza): string {
    return ETIQUETAS[estado];
  }

  tagEstado(estado: EstadoCobranza): string {
    return TAGS[estado];
  }

  emitir(): void {
    if (
      !this.nuevaTenantId ||
      !this.nuevoConcepto ||
      !this.nuevoPeriodo ||
      !this.nuevoMonto ||
      this.nuevoMonto <= 0
    ) {
      return;
    }
    this.guardando = true;
    this.error = '';
    this.cobranzaService
      .emitir({
        tenantId: this.nuevaTenantId,
        concepto: this.nuevoConcepto,
        periodo: this.nuevoPeriodo,
        montoTotal: this.nuevoMonto,
        fechaVencimiento: this.nuevaVencimiento ? this.nuevaVencimiento : null,
        observaciones: this.nuevasObservaciones || null,
      })
      .subscribe({
        next: () => {
          this.guardando = false;
          this.limpiarFormulario();
          this.cobranzaService.resumen().subscribe((data) => (this.resumen = data));
          this.cargar();
        },
        error: (err) => {
          this.guardando = false;
          this.error = err?.error?.error ?? 'OcurriÃ³ un error al emitir la cobranza.';
        },
      });
  }

  private limpiarFormulario(): void {
    this.nuevaTenantId = null;
    this.nuevoConcepto = '';
    this.nuevoPeriodo = '';
    this.nuevoMonto = null;
    this.nuevaVencimiento = '';
    this.nuevasObservaciones = '';
  }

  formularioCompleto(): boolean {
    return !!(this.nuevaTenantId && this.nuevoConcepto && this.nuevoPeriodo && this.nuevoMonto && this.nuevoMonto > 0);
  }
}