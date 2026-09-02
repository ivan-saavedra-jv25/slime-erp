import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MovimientoHistorial } from '../../core/models/models';
import { MovimientoService } from '../../core/services/movimiento.service';

@Component({
  selector: 'app-movimientos-historial',
  standalone: true,
  imports: [CommonModule, RouterLink, MatButtonModule, MatIconModule, MatCardModule],
  templateUrl: './movimientos-historial.component.html',
  styleUrl: './movimientos-historial.component.scss',
})
export class MovimientosHistorialComponent implements OnInit {
  movimientos: MovimientoHistorial[] = [];
  cargando = true;
  detalleAbierto: number | null = null;

  constructor(private movimientoService: MovimientoService) {}

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cargando = true;
    this.movimientoService.historial().subscribe({
      next: (data) => {
        this.movimientos = data;
        this.cargando = false;
      },
      error: () => (this.cargando = false),
    });
  }

  toggleDetalle(id: number): void {
    this.detalleAbierto = this.detalleAbierto === id ? null : id;
  }

  claseTag(tipo: string): string {
    switch (tipo) {
      case 'ENTRADA':
        return 'tag tag--success';
      case 'SALIDA':
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
        return 'input';
      case 'SALIDA':
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
