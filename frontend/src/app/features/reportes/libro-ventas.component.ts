import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { LibroVentasResponse } from '../../core/models/models';
import { ReporteService } from '../../core/services/reporte.service';
import { MonedaPipe } from '../../core/pipes/moneda.pipe';

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

function descargarBlob(blob: Blob, nombreArchivo: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = nombreArchivo;
  anchor.click();
  URL.revokeObjectURL(url);
}

@Component({
  selector: 'app-libro-ventas',
  standalone: true,
  imports: [CommonModule, FormsModule, MatButtonModule, MatIconModule, MatCardModule, MonedaPipe],
  templateUrl: './libro-ventas.component.html',
  styleUrl: './libro-ventas.component.scss',
})
export class LibroVentasComponent implements OnInit {
  desde = primerDiaDelMes();
  hasta = formatoFecha(new Date());
  libro: LibroVentasResponse | null = null;
  cargando = false;
  exportando = false;
  error = '';

  constructor(private reporteService: ReporteService) {}

  ngOnInit(): void {
    this.consultar();
  }

  consultar(): void {
    if (this.desde > this.hasta) {
      this.error = 'La fecha "desde" no puede ser posterior a "hasta".';
      return;
    }
    this.error = '';
    this.cargando = true;
    this.reporteService.libroVentas(this.desde, this.hasta).subscribe({
      next: (libro) => {
        this.libro = libro;
        this.cargando = false;
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'No se pudo generar el libro de ventas.';
        this.cargando = false;
      },
    });
  }

  exportarExcel(): void {
    if (!this.libro) return;
    const { desde, hasta } = this.libro;
    this.exportando = true;
    this.reporteService.libroVentasExcel(desde, hasta).subscribe({
      next: (blob) => {
        descargarBlob(blob, `libro-ventas-${desde}-a-${hasta}.xlsx`);
        this.exportando = false;
      },
      error: () => {
        this.error = 'No se pudo exportar el Excel.';
        this.exportando = false;
      },
    });
  }
}
