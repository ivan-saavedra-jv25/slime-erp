import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { Cliente, CuentaPorCobrar, EstadoCuentaPorCobrar } from '../../core/models/models';
import { CuentaPorCobrarService } from '../../core/services/cuenta-por-cobrar.service';
import { ClienteService } from '../../core/services/cliente.service';
import { AuthService } from '../../core/services/auth.service';
import { MonedaPipe } from '../../core/pipes/moneda.pipe';

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
  selector: 'app-tesoreria-cliente-detalle',
  standalone: true,
  imports: [CommonModule, RouterLink, MatButtonModule, MatIconModule, MatCardModule, MonedaPipe],
  templateUrl: './tesoreria-cliente-detalle.component.html',
  styleUrl: './tesoreria-cliente-detalle.component.scss',
})
export class TesoreriaClienteDetalleComponent implements OnInit {
  cliente: Cliente | null = null;
  cuentas: CuentaPorCobrar[] = [];
  cargando = true;

  constructor(
    private route: ActivatedRoute,
    private cuentaPorCobrarService: CuentaPorCobrarService,
    private clienteService: ClienteService,
    public auth: AuthService
  ) {}

  ngOnInit(): void {
    const clienteId = Number(this.route.snapshot.paramMap.get('clienteId'));
    this.cargando = true;
    this.clienteService.obtener(clienteId).subscribe({
      next: (cliente) => (this.cliente = cliente),
      error: () => (this.cliente = null),
    });
    this.cuentaPorCobrarService.listar(clienteId).subscribe({
      next: (data) => {
        this.cuentas = data;
        this.cargando = false;
      },
      error: () => (this.cargando = false),
    });
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

  get totalFacturado(): number {
    return this.cuentas.filter((c) => c.estado !== 'ANULADO').reduce((acc, c) => acc + c.montoTotal, 0);
  }

  get totalPagado(): number {
    return this.cuentas.filter((c) => c.estado !== 'ANULADO').reduce((acc, c) => acc + c.montoPagado, 0);
  }

  get saldoPendiente(): number {
    return this.cuentas.filter((c) => c.estado !== 'ANULADO').reduce((acc, c) => acc + c.saldoPendiente, 0);
  }

  get cuentasEnDeuda(): number {
    return this.cuentas.filter((c) => this.puedeRegistrarPago(c)).length;
  }
}
