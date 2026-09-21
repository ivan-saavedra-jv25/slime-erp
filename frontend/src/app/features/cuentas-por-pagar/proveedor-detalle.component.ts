import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { CuentaPorPagar, EstadoCuentaPorPagar, Proveedor } from '../../core/models/models';
import { CuentaPorPagarService } from '../../core/services/cuenta-por-pagar.service';
import { ProveedorService } from '../../core/services/proveedor.service';
import { AuthService } from '../../core/services/auth.service';
import { MonedaPipe } from '../../core/pipes/moneda.pipe';

const ETIQUETAS: Record<EstadoCuentaPorPagar, string> = {
  DEUDA: 'En deuda',
  PARCIAL: 'Parcial',
  PAGADO: 'Pagado',
  ANULADO: 'Anulado',
};

const TAGS: Record<EstadoCuentaPorPagar, string> = {
  DEUDA: 'tag--error',
  PARCIAL: 'tag--warning',
  PAGADO: 'tag--success',
  ANULADO: '',
};

@Component({
  selector: 'app-proveedor-detalle',
  standalone: true,
  imports: [CommonModule, RouterLink, MatButtonModule, MatIconModule, MatCardModule, MonedaPipe],
  templateUrl: './proveedor-detalle.component.html',
  styleUrl: './proveedor-detalle.component.scss',
})
export class ProveedorDetalleComponent implements OnInit {
  proveedor: Proveedor | null = null;
  cuentas: CuentaPorPagar[] = [];
  cargando = true;

  constructor(
    private route: ActivatedRoute,
    private cuentaPorPagarService: CuentaPorPagarService,
    private proveedorService: ProveedorService,
    public auth: AuthService
  ) {}

  ngOnInit(): void {
    const proveedorId = Number(this.route.snapshot.paramMap.get('proveedorId'));
    this.cargando = true;
    this.proveedorService.listar().subscribe({
      next: (data) => (this.proveedor = data.find((p) => p.id === proveedorId) ?? null),
      error: () => (this.proveedor = null),
    });
    this.cuentaPorPagarService.listar(proveedorId).subscribe({
      next: (data) => {
        this.cuentas = data;
        this.cargando = false;
      },
      error: () => (this.cargando = false),
    });
  }

  etiquetaEstado(estado: EstadoCuentaPorPagar): string {
    return ETIQUETAS[estado];
  }

  tagEstado(estado: EstadoCuentaPorPagar): string {
    return TAGS[estado];
  }

  puedeRegistrarPago(cuenta: CuentaPorPagar): boolean {
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
