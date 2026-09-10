import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { Bodega, MovimientoHistorial, UsuarioBasico } from '../../core/models/models';
import { MovimientoService } from '../../core/services/movimiento.service';
import { BodegaService } from '../../core/services/bodega.service';
import { UsuarioService } from '../../core/services/usuario.service';
import { MovimientoDetalleDialogComponent } from './movimiento-detalle-dialog.component';

function formatoFecha(fecha: Date): string {
  const anio = fecha.getFullYear();
  const mes = String(fecha.getMonth() + 1).padStart(2, '0');
  const dia = String(fecha.getDate()).padStart(2, '0');
  return `${anio}-${mes}-${dia}`;
}

function primerDiaDelMes(): string {
  const hoy = new Date();
  return formatoFecha(new Date(hoy.getFullYear(), hoy.getMonth(), 1));
}

function ultimoDiaDelMes(): string {
  const hoy = new Date();
  return formatoFecha(new Date(hoy.getFullYear(), hoy.getMonth() + 1, 0));
}

@Component({
  selector: 'app-movimientos-historial',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, MatButtonModule, MatIconModule, MatCardModule],
  templateUrl: './movimientos-historial.component.html',
  styleUrl: './movimientos-historial.component.scss',
})
export class MovimientosHistorialComponent implements OnInit {
  movimientos: MovimientoHistorial[] = [];
  cargando = true;

  bodegas: Bodega[] = [];
  usuarios: UsuarioBasico[] = [];

  fechaDesde: string | null = primerDiaDelMes();
  fechaHasta: string | null = ultimoDiaDelMes();
  usuarioId: number | null = null;
  bodegaId: number | null = null;

  constructor(
    private movimientoService: MovimientoService,
    private bodegaService: BodegaService,
    private usuarioService: UsuarioService,
    private dialog: MatDialog
  ) {}

  ngOnInit(): void {
    this.bodegaService.listar().subscribe((data) => (this.bodegas = data));
    this.usuarioService.listarBasico().subscribe((data) => (this.usuarios = data));
    this.cargar();
  }

  cargar(): void {
    this.cargando = true;
    this.movimientoService
      .historial({
        fechaDesde: this.fechaDesde,
        fechaHasta: this.fechaHasta,
        usuarioId: this.usuarioId,
        bodegaId: this.bodegaId,
      })
      .subscribe({
        next: (data) => {
          this.movimientos = data;
          this.cargando = false;
        },
        error: () => (this.cargando = false),
      });
  }

  limpiarFiltros(): void {
    this.fechaDesde = primerDiaDelMes();
    this.fechaHasta = ultimoDiaDelMes();
    this.usuarioId = null;
    this.bodegaId = null;
    this.cargar();
  }

  verDetalle(movimiento: MovimientoHistorial): void {
    this.dialog.open(MovimientoDetalleDialogComponent, { data: movimiento });
  }

  claseTag(tipo: string): string {
    switch (tipo) {
      case 'ENTRADA':
      case 'ENTRADA_COMPRA':
        return 'tag tag--success';
      case 'SALIDA':
      case 'SALIDA_VENTA':
        return 'tag tag--error';
      case 'TRASLADO':
        return 'tag tag--info';
      case 'AJUSTE':
        return 'tag tag--warning';
      default:
        return 'tag';
    }
  }

  iconoTipo(tipo: string): string {
    switch (tipo) {
      case 'ENTRADA':
      case 'ENTRADA_COMPRA':
        return 'input';
      case 'SALIDA':
      case 'SALIDA_VENTA':
        return 'output';
      case 'TRASLADO':
        return 'swap_horiz';
      case 'AJUSTE':
        return 'tune';
      default:
        return 'help';
    }
  }
}
