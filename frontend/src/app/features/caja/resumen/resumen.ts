import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { RouterLink } from '@angular/router';
import { CashRegisterStatus, CashRegisterType } from '../core/models';
import { nombreSucursal, nombreUsuario } from '../core/seed/mock-data';
import { BusinessRuleError } from '../core/services/business-rule-error';
import { CajaFacade } from '../core/services/caja-facade.service';
import { CashRegisterService } from '../core/services/cash-register.service';
import { SampleService } from '../core/seed/sample.service';
import { ClpPipe } from '../shared/pipes/clp.pipe';

/**
 * Resumen: administración de cajas + dashboard de la caja abierta (spec §4).
 */
@Component({
  selector: 'app-resumen',
  standalone: true,
  imports: [FormsModule, RouterLink, MatButtonModule, MatCardModule, ClpPipe, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './resumen.html',
  styleUrl: './resumen.css',
})
export class ResumenComponent {
  private readonly facade = inject(CajaFacade);
  private readonly registers = inject(CashRegisterService);
  private readonly sample = inject(SampleService);

  readonly cajas = this.facade.cajas;
  readonly caja = this.facade.cajaActual;
  readonly sesion = this.facade.sesionActiva;
  readonly abierta = this.facade.hayCajaAbierta;
  readonly totalEntradas = this.facade.totalEntradas;
  readonly totalSalidas = this.facade.totalSalidas;
  readonly saldoEsperado = this.facade.saldoEsperado;
  readonly movimientos = this.facade.movimientosSesion;
  readonly ultimaEntrada = this.facade.ultimaEntrada;
  readonly ultimaSalida = this.facade.ultimaSalida;

  readonly tipos = Object.values(CashRegisterType);
  readonly Estado = CashRegisterStatus;

  readonly formAbierto = signal(false);
  readonly editandoId = signal<string | null>(null);
  readonly error = signal<string | null>(null);

  readonly nombre = signal('');
  readonly tipo = signal<CashRegisterType>(CashRegisterType.SUCURSAL);

  readonly responsable = computed(() => {
    const c = this.caja();
    return c ? nombreUsuario(c.responsableId) : '—';
  });

  readonly sucursal = computed(() => {
    const c = this.caja();
    return c ? nombreSucursal(c.sucursalId) : '—';
  });

  readonly tituloForm = computed(() => (this.editandoId() ? 'Editar caja' : 'Nueva caja'));

  readonly confirmandoEjemplo = signal(false);
  readonly confirmandoBorrado = signal(false);
  readonly mensajeDatos = signal<string | null>(null);

  seleccionar(id: string): void {
    this.facade.seleccionarCaja(id);
  }

  nuevaCaja(): void {
    this.editandoId.set(null);
    this.nombre.set('');
    this.tipo.set(CashRegisterType.SUCURSAL);
    this.error.set(null);
    this.formAbierto.set(true);
  }

  editarCaja(id: string): void {
    const caja = this.registers.byId(id);
    if (!caja) return;
    this.editandoId.set(id);
    this.nombre.set(caja.nombre);
    this.tipo.set(caja.tipo);
    this.error.set(null);
    this.formAbierto.set(true);
  }

  cancelar(): void {
    this.formAbierto.set(false);
    this.error.set(null);
  }

  guardar(): void {
    const nombre = this.nombre().trim();
    if (!nombre) {
      this.error.set('El nombre de la caja es obligatorio.');
      return;
    }
    try {
      const id = this.editandoId();
      if (id) {
        this.registers.update(id, { nombre, tipo: this.tipo() });
      } else {
        const creada = this.registers.create({ nombre, tipo: this.tipo() });
        this.facade.seleccionarCaja(creada.id);
      }
      this.formAbierto.set(false);
      this.error.set(null);
    } catch (e) {
      this.error.set(e instanceof BusinessRuleError ? e.message : 'No se pudo guardar la caja.');
    }
  }

  cargarEjemplo(): void {
    this.sample.loadSample();
    this.confirmandoEjemplo.set(false);
    this.mensajeDatos.set('Datos de ejemplo cargados: una sesión cerrada de ayer y una abierta hoy.');
  }

  borrarDatos(): void {
    this.sample.resetAll();
    this.confirmandoBorrado.set(false);
    this.mensajeDatos.set('Datos borrados. Quedó una caja cerrada, lista para abrir.');
  }

  nombreUsuario = nombreUsuario;
  nombreSucursal = nombreSucursal;
}
