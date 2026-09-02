import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { BodegaService, BodegaRequest } from '../../core/services/bodega.service';
import { StockService } from '../../core/services/stock.service';
import { AuthService } from '../../core/services/auth.service';
import { Bodega, InventarioItem } from '../../core/models/models';

@Component({
  selector: 'app-bodegas',
  standalone: true,
  imports: [CommonModule, FormsModule, MatTableModule, MatButtonModule, MatIconModule, MatCardModule],
  templateUrl: './bodegas.component.html',
  styleUrl: './bodegas.component.scss',
})
export class BodegasComponent implements OnInit {
  columnasBodegas = ['nombre', 'acciones'];
  columnasInventario = ['sku', 'nombre', 'cantidad'];

  bodegas: Bodega[] = [];
  inventario: InventarioItem[] = [];
  bodegaSeleccionada: Bodega | null = null;
  filtroProducto = '';
  error = '';
  guardando = false;
  editandoId: number | null = null;
  nombre = '';

  constructor(
    private bodegaService: BodegaService,
    private stockService: StockService,
    public auth: AuthService
  ) {}

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.bodegaService.listar().subscribe((data) => (this.bodegas = data));
  }

  get inventarioFiltrado(): InventarioItem[] {
    const q = this.filtroProducto.trim().toLowerCase();
    if (!q) return this.inventario;
    return this.inventario.filter(
      (i) => i.nombre.toLowerCase().includes(q) || (i.sku ?? '').toLowerCase().includes(q)
    );
  }

  seleccionar(bodega: Bodega): void {
    this.bodegaSeleccionada = bodega;
    this.filtroProducto = '';
    this.stockService.inventarioPorBodega(bodega.id).subscribe((data) => (this.inventario = data));
  }

  editar(bodega: Bodega): void {
    this.editandoId = bodega.id;
    this.nombre = bodega.nombre;
  }

  cancelarEdicion(): void {
    this.editandoId = null;
    this.nombre = '';
  }

  guardar(): void {
    if (!this.nombre.trim()) return;
    const request: BodegaRequest = { nombre: this.nombre.trim() };
    this.guardando = true;
    const obs = this.editandoId
      ? this.bodegaService.actualizar(this.editandoId, request)
      : this.bodegaService.crear(request);
    obs.subscribe({
      next: () => {
        this.error = '';
        this.guardando = false;
        this.editandoId = null;
        this.nombre = '';
        this.cargar();
      },
      error: (err) => {
        this.guardando = false;
        this.error = err?.error?.error ?? 'Ocurrió un error. Intenta nuevamente.';
      },
    });
  }

  eliminar(bodega: Bodega): void {
    this.bodegaService.eliminar(bodega.id).subscribe({
      next: () => {
        this.error = '';
        if (this.editandoId === bodega.id) this.cancelarEdicion();
        if (this.bodegaSeleccionada?.id === bodega.id) {
          this.bodegaSeleccionada = null;
          this.inventario = [];
        }
        this.cargar();
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'Ocurrió un error. Intenta nuevamente.';
      },
    });
  }
}
