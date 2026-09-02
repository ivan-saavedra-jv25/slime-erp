import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { Cliente, EstadoTransaccion, MedioPago, TransaccionPago } from '../../core/models/models';
import { ClienteService } from '../../core/services/cliente.service';
import { FiltrosHistorialPago, TransaccionPagoService } from '../../core/services/transaccion-pago.service';

@Component({
  selector: 'app-tesoreria-historial',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, MatButtonModule, MatIconModule, MatCardModule],
  templateUrl: './tesoreria-historial.component.html',
  styleUrl: './tesoreria-historial.component.scss',
})
export class TesoreriaHistorialComponent implements OnInit {
  pagos: TransaccionPago[] = [];
  clientes: Cliente[] = [];
  cargando = true;

  filtroClienteId: number | null = null;
  filtroEstado: EstadoTransaccion | null = null;
  filtroMedioPago: MedioPago | null = null;
  filtroFechaDesde = '';
  filtroFechaHasta = '';

  constructor(
    private transaccionPagoService: TransaccionPagoService,
    private clienteService: ClienteService
  ) {}

  ngOnInit(): void {
    this.clienteService.listar().subscribe((data) => (this.clientes = data));
    this.buscar();
  }

  buscar(): void {
    this.cargando = true;
    const filtros: FiltrosHistorialPago = {
      clienteId: this.filtroClienteId ?? undefined,
      estado: this.filtroEstado ?? undefined,
      medioPago: this.filtroMedioPago ?? undefined,
      fechaDesde: this.filtroFechaDesde ? `${this.filtroFechaDesde}T00:00:00` : undefined,
      fechaHasta: this.filtroFechaHasta ? `${this.filtroFechaHasta}T23:59:59` : undefined,
    };
    this.transaccionPagoService.buscar(filtros).subscribe({
      next: (data) => {
        this.pagos = data;
        this.cargando = false;
      },
      error: () => (this.cargando = false),
    });
  }

  limpiarFiltros(): void {
    this.filtroClienteId = null;
    this.filtroEstado = null;
    this.filtroMedioPago = null;
    this.filtroFechaDesde = '';
    this.filtroFechaHasta = '';
    this.buscar();
  }

  nombreCliente(id: number): string {
    return this.clientes.find((c) => c.id === id)?.nombre ?? String(id);
  }
}
