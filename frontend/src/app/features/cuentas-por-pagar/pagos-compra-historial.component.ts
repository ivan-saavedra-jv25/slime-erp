import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { EstadoTransaccion, MedioPago, TransaccionPagoCompra } from '../../core/models/models';
import { FiltrosHistorialPagoCompra, TransaccionPagoCompraService } from '../../core/services/transaccion-pago-compra.service';
import { MonedaPipe } from '../../core/pipes/moneda.pipe';

@Component({
  selector: 'app-pagos-compra-historial',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, MatButtonModule, MatIconModule, MatCardModule, MatPaginatorModule, MonedaPipe],
  templateUrl: './pagos-compra-historial.component.html',
  styleUrl: './pagos-compra-historial.component.scss',
})
export class PagosCompraHistorialComponent implements OnInit {
  readonly opcionesTamano = [10, 25, 50];

  pagos: TransaccionPagoCompra[] = [];
  cargando = true;
  total = 0;
  pagina = 0;
  tamano = 10;

  filtroEstado: EstadoTransaccion | null = null;
  filtroMedioPago: MedioPago | null = null;
  filtroFechaDesde = '';
  filtroFechaHasta = '';

  constructor(private transaccionPagoCompraService: TransaccionPagoCompraService) {}

  ngOnInit(): void {
    this.buscar();
  }

  onFiltroChange(): void {
    this.pagina = 0;
    this.buscar();
  }

  onPageChange(event: PageEvent): void {
    this.pagina = event.pageIndex;
    this.tamano = event.pageSize;
    this.buscar();
  }

  buscar(): void {
    this.cargando = true;
    const filtros: FiltrosHistorialPagoCompra = {
      estado: this.filtroEstado ?? undefined,
      medioPago: this.filtroMedioPago ?? undefined,
      fechaDesde: this.filtroFechaDesde ? `${this.filtroFechaDesde}T00:00:00` : undefined,
      fechaHasta: this.filtroFechaHasta ? `${this.filtroFechaHasta}T23:59:59` : undefined,
      pagina: this.pagina,
      tamano: this.tamano,
    };
    this.transaccionPagoCompraService.buscar(filtros).subscribe({
      next: (resp) => {
        this.pagos = resp.contenido;
        this.total = resp.total;
        this.cargando = false;
      },
      error: () => (this.cargando = false),
    });
  }

  limpiarFiltros(): void {
    this.filtroEstado = null;
    this.filtroMedioPago = null;
    this.filtroFechaDesde = '';
    this.filtroFechaHasta = '';
    this.pagina = 0;
    this.buscar();
  }

  origenDe(p: TransaccionPagoCompra): string {
    return p.compraId !== null ? `Compra C-${p.compraId}` : `Gasto #${p.gastoId}`;
  }
}
