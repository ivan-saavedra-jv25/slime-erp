import { Component, Inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { CategoriaService } from '../../core/services/categoria.service';
import { ProductoService, ProductoRequest } from '../../core/services/producto.service';
import { Categoria, Producto } from '../../core/models/models';

export interface ProductoRapidoDialogData {
  textoBusqueda: string;
}

// Un código de barra real (EAN-8/EAN-13/UPC) es siempre numérico y de 6+ dígitos;
// si el texto buscado calza con ese patrón, se precarga como código de barra.
function pareceCodigoBarra(texto: string): boolean {
  return /^\d{6,}$/.test(texto.trim());
}

@Component({
  selector: 'app-producto-rapido-dialog',
  standalone: true,
  imports: [FormsModule, MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Crear producto nuevo</h2>
    <mat-dialog-content class="rapido-dialog-content">
      <form class="form-grid">
        <div class="form-group span-2">
          <label for="rapidoNombre">Nombre<span class="required-mark">*</span></label>
          <input id="rapidoNombre" [(ngModel)]="nombre" name="nombre" required />
        </div>

        <div class="form-group">
          <label for="rapidoSku">SKU <span class="hint">(opcional)</span></label>
          <input id="rapidoSku" [(ngModel)]="sku" name="sku" />
        </div>

        <div class="form-group">
          <label for="rapidoCodigoBarra">Código de barra <span class="hint">(opcional)</span></label>
          <input id="rapidoCodigoBarra" [(ngModel)]="codigoBarra" name="codigoBarra" />
        </div>

        <div class="form-group">
          <label for="rapidoCategoria">Categoría <span class="hint">(opcional)</span></label>
          <select id="rapidoCategoria" [(ngModel)]="categoriaId" name="categoriaId">
            <option [ngValue]="null">Sin categoría</option>
            @for (c of categorias(); track c.id) {
              <option [ngValue]="c.id">{{ c.nombre }}</option>
            }
          </select>
        </div>

        <div class="form-group">
          <label for="rapidoPrecioVenta">Precio venta<span class="required-mark">*</span></label>
          <input id="rapidoPrecioVenta" type="number" min="0" [(ngModel)]="precioVenta" name="precioVenta" required />
        </div>

        <div class="form-group">
          <label for="rapidoPrecioCompra">Precio compra <span class="hint">(opcional)</span></label>
          <input id="rapidoPrecioCompra" type="number" min="0" [(ngModel)]="precioCompra" name="precioCompra" />
        </div>

        <div class="form-group">
          <label for="rapidoStockMinimo">Stock mínimo <span class="hint">(opcional)</span></label>
          <input id="rapidoStockMinimo" type="number" min="0" [(ngModel)]="stockMinimo" name="stockMinimo" />
        </div>
      </form>
      @if (error()) {
        <p class="field-error">{{ error() }}</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button type="button" mat-button mat-dialog-close [disabled]="guardando()">Cancelar</button>
      <button type="button" mat-flat-button color="primary" [disabled]="!nombre || !precioVenta || guardando()" (click)="guardar()">
        {{ guardando() ? 'Creando...' : 'Crear y agregar' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .rapido-dialog-content {
        min-width: min(520px, 80vw);
      }
    `,
  ],
})
export class ProductoRapidoDialogComponent implements OnInit {
  nombre = '';
  sku = '';
  codigoBarra = '';
  categoriaId: number | null = null;
  precioVenta: number | null = null;
  precioCompra: number | null = null;
  stockMinimo: number | null = null;

  categorias = signal<Categoria[]>([]);
  error = signal<string | null>(null);
  guardando = signal(false);

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: ProductoRapidoDialogData,
    public dialogRef: MatDialogRef<ProductoRapidoDialogComponent, Producto | undefined>,
    private categoriaService: CategoriaService,
    private productoService: ProductoService
  ) {
    this.nombre = data.textoBusqueda && !pareceCodigoBarra(data.textoBusqueda) ? data.textoBusqueda : '';
    this.codigoBarra = pareceCodigoBarra(data.textoBusqueda ?? '') ? data.textoBusqueda.trim() : '';
  }

  ngOnInit(): void {
    this.categoriaService.listar().subscribe((data) => this.categorias.set(data));
  }

  guardar(): void {
    if (!this.nombre || !this.precioVenta) return;
    this.guardando.set(true);
    this.error.set(null);
    const request: ProductoRequest = {
      nombre: this.nombre,
      sku: this.sku || null,
      codigoBarra: this.codigoBarra || null,
      categoriaId: this.categoriaId,
      precioVenta: this.precioVenta,
      precioCompra: this.precioCompra ?? 0,
      stockMinimo: this.stockMinimo ?? 0,
    };
    this.productoService.crear(request).subscribe({
      next: (producto) => {
        this.guardando.set(false);
        this.dialogRef.close(producto);
      },
      error: (err) => {
        this.guardando.set(false);
        this.error.set(err?.error?.error ?? 'No se pudo crear el producto.');
      },
    });
  }
}
