import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';
import { EmpresaService } from '../../core/services/empresa.service';
import { AuthService } from '../../core/services/auth.service';
import { Empresa, CrearEmpresaRequest } from '../../core/models/models';
import { EmpresaFormDialogComponent } from './empresa-form-dialog.component';

@Component({
  selector: 'app-empresas',
  standalone: true,
  imports: [CommonModule, MatTableModule, MatButtonModule, MatIconModule],
  templateUrl: './empresas.component.html',
  styleUrl: './empresas.component.scss',
})
export class EmpresasComponent implements OnInit {
  columnas = ['nombre', 'rut', 'plan', 'activo', 'acciones'];
  empresas: Empresa[] = [];
  error = '';

  constructor(private empresaService: EmpresaService, public auth: AuthService, private dialog: MatDialog) {}

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.empresaService.listar().subscribe((empresas) => (this.empresas = empresas));
  }

  abrirCrear(): void {
    const ref = this.dialog.open(EmpresaFormDialogComponent, { width: '480px' });
    ref.afterClosed().subscribe((request: CrearEmpresaRequest | undefined) => {
      if (request) {
        this.empresaService.crear(request).subscribe({
          next: () => {
            this.error = '';
            this.cargar();
          },
          error: (err) => {
            this.error = err?.error?.error ?? 'Ocurrió un error. Intenta nuevamente.';
          },
        });
      }
    });
  }

  toggleEstado(empresa: Empresa): void {
    const accion = empresa.activo ? this.empresaService.desactivar(empresa.id) : this.empresaService.activar(empresa.id);
    accion.subscribe({
      next: () => {
        this.error = '';
        this.cargar();
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'Ocurrió un error. Intenta nuevamente.';
      },
    });
  }
}
