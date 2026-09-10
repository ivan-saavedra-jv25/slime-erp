import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { Cliente, EstadoTransaccion, MedioPago, TransaccionPago } from '../../core/models/models';
import { ClienteService } from '../../core/services/cliente.service';
import { FiltrosHistorialPago, TransaccionPagoService } from '../../core/services/transaccion-pago.service';
import { MonedaPipe } from '../../core/pipes/moneda.pipe';

@Component({
  selector: 'app-tesoreria-historial',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, MatButtonModule, MatIconModule, MatCardModule, MatPaginatorModule, MonedaPipe],
  templateUrl: './tesoreria-historial.component.html',
  styleUrl: './tesoreria-historial.component.scss',
})
export class TesoreriaHistorialComponent implements OnInit, OnDestroy {
  readonly opcionesTamano = [10, 25, 50];

  pagos: TransaccionPago[] = [];
  clientes: Cliente[] = [];
  cargando = true;
  total = 0;
  pagina = 0;
  tamano = 10;

  busqueda = '';
  filtroEstado: EstadoTransaccion | null = null;
  filtroMedioPago: MedioPago | null = null;
  filtroFechaDesde = '';
  filtroFechaHasta = '';

  private readonly busqueda$ = new Subject<string>();

  constructor(
    private transaccionPagoService: TransaccionPagoService,
    private clienteService: ClienteService
  ) {}

  ngOnInit(): void {
    this.clienteService.listar().subscribe((data) => (this.clientes = data));
    this.busqueda$.pipe(debounceTime(300), distinctUntilChanged()).subscribe(() => {
      this.pagina = 0;
      this.buscar();
    });
    this.buscar();
  }

  ngOnDestroy(): void {
    this.busqueda$.complete();
  }

  onBusquedaChange(): void {
    this.busqueda$.next(this.busqueda);
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
    const filtros: FiltrosHistorialPago = {
      busqueda: this.busqueda.trim() || undefined,
      estado: this.filtroEstado ?? undefined,
      medioPago: this.filtroMedioPago ?? undefined,
      fechaDesde: this.filtroFechaDesde ? `${this.filtroFechaDesde}T00:00:00` : undefined,
      fechaHasta: this.filtroFechaHasta ? `${this.filtroFechaHasta}T23:59:59` : undefined,
      pagina: this.pagina,
      tamano: this.tamano,
    };
    this.transaccionPagoService.buscar(filtros).subscribe({
      next: (resp) => {
        this.pagos = resp.contenido;
        this.total = resp.total;
        this.cargando = false;
      },
      error: () => (this.cargando = false),
    });
  }

  limpiarFiltros(): void {
    this.busqueda = '';
    this.filtroEstado = null;
    this.filtroMedioPago = null;
    this.filtroFechaDesde = '';
    this.filtroFechaHasta = '';
    this.pagina = 0;
    this.buscar();
  }

  nombreCliente(id: number): string {
    return this.clientes.find((c) => c.id === id)?.nombre ?? String(id);
  }
}
