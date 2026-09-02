import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { Cliente, CuentaPorCobrar, EstadoCuentaPorCobrar, ResumenTesoreria } from '../../core/models/models';
import { CuentaPorCobrarService } from '../../core/services/cuenta-por-cobrar.service';
import { ClienteService } from '../../core/services/cliente.service';
import { AuthService } from '../../core/services/auth.service';

const ETIQUETAS: Record<EstadoCuentaPorCobrar, string> = {
  DEUDA: 'En deuda',
  PARCIAL: 'Parcial',
  PAGADO: 'Pagado',
  ANULADO: 'Anulado',
};

const TAGS: Record<EstadoCuentaPorCobrar, string> = {
  DEUDA: 'tag--error',
  PARCIAL: 'tag--warning',
  PAGADO: 'tag--success',
  ANULADO: '',
};

@Component({
  selector: 'app-tesoreria-cuentas',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, MatButtonModule, MatIconModule, MatCardModule],
  templateUrl: './tesoreria-cuentas.component.html',
  styleUrl: './tesoreria-cuentas.component.scss',
})
export class TesoreriaCuentasComponent implements OnInit {
  cuentas: CuentaPorCobrar[] = [];
  clientes: Cliente[] = [];
  resumen: ResumenTesoreria | null = null;
  cargando = true;

  filtroCliente: number | null = null;
  filtroEstado: EstadoCuentaPorCobrar | null = null;
  filtroTexto = '';

  constructor(
    private cuentaPorCobrarService: CuentaPorCobrarService,
    private clienteService: ClienteService,
    public auth: AuthService
  ) {}

  ngOnInit(): void {
    this.clienteService.listar().subscribe((data) => (this.clientes = data));
    this.cuentaPorCobrarService.resumen().subscribe((data) => (this.resumen = data));
    this.cargar();
  }

  cargar(): void {
    this.cargando = true;
    this.cuentaPorCobrarService.listar().subscribe({
      next: (data) => {
        this.cuentas = data;
        this.cargando = false;
      },
      error: () => (this.cargando = false),
    });
  }

  get cuentasFiltradas(): CuentaPorCobrar[] {
    const texto = this.filtroTexto.trim().toLowerCase();
    return this.cuentas.filter((c) => {
      if (this.filtroCliente && c.clienteId !== this.filtroCliente) return false;
      if (this.filtroEstado && c.estado !== this.filtroEstado) return false;
      if (texto) {
        const nombre = this.nombreCliente(c.clienteId).toLowerCase();
        const ventaRef = `v-${c.ventaId}`;
        if (!nombre.includes(texto) && !ventaRef.includes(texto)) return false;
      }
      return true;
    });
  }

  nombreCliente(id: number): string {
    return this.clientes.find((c) => c.id === id)?.nombre ?? String(id);
  }

  etiquetaEstado(estado: EstadoCuentaPorCobrar): string {
    return ETIQUETAS[estado];
  }

  tagEstado(estado: EstadoCuentaPorCobrar): string {
    return TAGS[estado];
  }

  puedeRegistrarPago(cuenta: CuentaPorCobrar): boolean {
    return cuenta.estado === 'DEUDA' || cuenta.estado === 'PARCIAL';
  }
}
