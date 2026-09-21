import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { CategoriaGasto, CuentaPorPagar, EstadoCuentaPorPagar, Proveedor, ResumenCuentasPorPagar } from '../../core/models/models';
import { CuentaPorPagarService } from '../../core/services/cuenta-por-pagar.service';
import { ProveedorService } from '../../core/services/proveedor.service';
import { CategoriaGastoService } from '../../core/services/categoria-gasto.service';
import { AuthService } from '../../core/services/auth.service';
import { MonedaPipe } from '../../core/pipes/moneda.pipe';

type Origen = 'COMPRA' | 'GASTO';

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

export interface DeudaProveedor {
  proveedorId: number;
  nombre: string;
  cantidadCuentas: number;
  saldoPendiente: number;
}

@Component({
  selector: 'app-cuentas-por-pagar',
  standalone: true,
  imports: [FormsModule, RouterLink, MatButtonModule, MatIconModule, MatCardModule, MonedaPipe],
  templateUrl: './cuentas-por-pagar.component.html',
  styleUrl: './cuentas-por-pagar.component.scss',
})
export class CuentasPorPagarComponent implements OnInit {
  cuentas: CuentaPorPagar[] = [];
  proveedores: Proveedor[] = [];
  categorias: CategoriaGasto[] = [];
  resumen: ResumenCuentasPorPagar | null = null;
  cargando = true;

  filtroOrigen: Origen | null = null;
  filtroProveedor: number | null = null;
  filtroCategoriaGasto: number | null = null;
  filtroEstado: EstadoCuentaPorPagar | null = null;
  filtroTexto = '';
  mostrarTodas = false;

  constructor(
    private cuentaPorPagarService: CuentaPorPagarService,
    private proveedorService: ProveedorService,
    private categoriaGastoService: CategoriaGastoService,
    public auth: AuthService
  ) {}

  ngOnInit(): void {
    this.proveedorService.listar().subscribe((data) => (this.proveedores = data));
    this.categoriaGastoService.listar().subscribe((data) => (this.categorias = data));
    this.cuentaPorPagarService.resumen().subscribe((data) => (this.resumen = data));
    this.cargar();
  }

  cargar(): void {
    this.cargando = true;
    this.cuentaPorPagarService.listar().subscribe({
      next: (data) => {
        this.cuentas = data;
        this.cargando = false;
      },
      error: () => (this.cargando = false),
    });
  }

  get deudaPorProveedor(): DeudaProveedor[] {
    const porProveedor = new Map<number, DeudaProveedor>();
    for (const c of this.cuentas) {
      if (c.compraId === null || c.proveedorId === null) continue;
      if (c.estado !== 'DEUDA' && c.estado !== 'PARCIAL') continue;
      const entrada = porProveedor.get(c.proveedorId) ?? {
        proveedorId: c.proveedorId,
        nombre: this.nombreProveedor(c.proveedorId),
        cantidadCuentas: 0,
        saldoPendiente: 0,
      };
      entrada.cantidadCuentas += 1;
      entrada.saldoPendiente += c.saldoPendiente;
      porProveedor.set(c.proveedorId, entrada);
    }
    return Array.from(porProveedor.values()).sort((a, b) => b.saldoPendiente - a.saldoPendiente);
  }

  get cuentasFiltradas(): CuentaPorPagar[] {
    const texto = this.filtroTexto.trim().toLowerCase();
    return this.cuentas.filter((c) => {
      if (this.filtroOrigen === 'COMPRA' && c.compraId === null) return false;
      if (this.filtroOrigen === 'GASTO' && c.gastoId === null) return false;
      if (this.filtroProveedor && c.proveedorId !== this.filtroProveedor) return false;
      if (this.filtroCategoriaGasto && c.categoriaGastoId !== this.filtroCategoriaGasto) return false;
      if (this.filtroEstado && c.estado !== this.filtroEstado) return false;
      if (texto && !c.descripcion.toLowerCase().includes(texto)) return false;
      return true;
    });
  }

  origenDe(c: CuentaPorPagar): Origen {
    return c.compraId !== null ? 'COMPRA' : 'GASTO';
  }

  nombreProveedor(id: number): string {
    return this.proveedores.find((p) => p.id === id)?.nombre ?? `Proveedor #${id}`;
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
}
