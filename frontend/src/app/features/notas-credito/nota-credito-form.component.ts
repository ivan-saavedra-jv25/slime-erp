import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import {
  Cliente,
  DocumentoAsociable,
  LineaDocumentoOriginal,
  TipoCorreccion,
  TipoDocumentoVenta,
} from '../../core/models/models';
import { NotaCreditoRequest, NotaCreditoService } from '../../core/services/nota-credito.service';
import { ClienteService } from '../../core/services/cliente.service';
import { MonedaPipe } from '../../core/pipes/moneda.pipe';
import { cerrarCargando, mostrarCargando } from '../../core/utils/swal-loading';
import {
  AYUDA_TIPO_CORRECCION,
  ETIQUETAS_TIPO_CORRECCION,
  TIPOS_CORRECCION,
} from './estado-nota-credito';

const TASA_IVA = 0.19;
const MAX_TEXTO_CORRECCION = 2000;

const ETIQUETAS_DOCUMENTO: Record<TipoDocumentoVenta, string> = {
  FACTURA: 'Factura',
  BOLETA: 'Boleta',
  VOUCHER: 'Voucher',
};

// Una línea del formulario: la del documento original más lo que el usuario
// decide corregir de ella. `incluida` distingue las líneas que entran en la nota
// de las que solo se muestran para elegir.
interface LineaEditable {
  ventaDetalleId: number;
  productoId: number;
  codigo: string | null;
  descripcion: string;
  cantidadVendida: number;
  cantidadDisponible: number;
  incluida: boolean;
  cantidad: number;
  precioUnitario: number;
  descuento: number;
  recuperaInventario: boolean;
}

function formatoFecha(fecha: Date): string {
  const anio = fecha.getFullYear();
  const mes = String(fecha.getMonth() + 1).padStart(2, '0');
  const dia = String(fecha.getDate()).padStart(2, '0');
  return `${anio}-${mes}-${dia}`;
}

@Component({
  selector: 'app-nota-credito-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, MatButtonModule, MatIconModule, MatCardModule, MonedaPipe],
  templateUrl: './nota-credito-form.component.html',
  styleUrl: './nota-credito-form.component.scss',
})
export class NotaCreditoFormComponent implements OnInit, OnDestroy {
  private readonly LIMITE_RESULTADOS = 8;

  readonly tiposCorreccion = TIPOS_CORRECCION;
  readonly maxTextoCorreccion = MAX_TEXTO_CORRECCION;

  notaCreditoId: number | null = null;

  // Sección 2: documento asociado
  clienteSeleccionado: Cliente | null = null;
  documentoSeleccionado: DocumentoAsociable | null = null;
  razonAsociacion = '';

  // Sección 3: tipo de corrección
  tipoCorreccion: TipoCorreccion | null = null;

  // Sección 4: detalle
  lineas: LineaEditable[] = [];

  // Sección 5: corrección de texto
  textoCorreccion = '';

  // Sección 6: información complementaria
  fecha = formatoFecha(new Date());
  motivo = '';
  observaciones = '';

  // Sección 7: totales
  descuento = 0;

  filtroCliente = '';
  clientesResultados: Cliente[] = [];
  filtroDocumento = '';
  documentosResultados: DocumentoAsociable[] = [];

  cargando = false;
  guardando = false;
  error = '';
  errorLineas: string | null = null;

  private readonly busquedaCliente$ = new Subject<string>();
  private readonly busquedaDocumento$ = new Subject<string>();

  constructor(
    private notaCreditoService: NotaCreditoService,
    private clienteService: ClienteService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.busquedaCliente$.pipe(debounceTime(300), distinctUntilChanged()).subscribe((texto) => {
      if (!texto.trim()) {
        this.clientesResultados = [];
        return;
      }
      this.clienteService.listarPagina(texto, 0, this.LIMITE_RESULTADOS).subscribe((resp) => {
        this.clientesResultados = resp.contenido;
      });
    });

    this.busquedaDocumento$.pipe(debounceTime(300), distinctUntilChanged()).subscribe((texto) => {
      this.buscarDocumentos(texto);
    });

    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.notaCreditoId = Number(id);
      this.cargarNotaCredito(this.notaCreditoId);
    }
  }

  ngOnDestroy(): void {
    this.busquedaCliente$.complete();
    this.busquedaDocumento$.complete();
  }

  get esEdicion(): boolean {
    return this.notaCreditoId !== null;
  }

  // --- Sección 2: documento asociado ---------------------------------------

  onBusquedaClienteChange(): void {
    this.busquedaCliente$.next(this.filtroCliente);
  }

  seleccionarCliente(cliente: Cliente): void {
    this.clienteSeleccionado = cliente;
    this.filtroCliente = '';
    this.clientesResultados = [];
    this.documentoSeleccionado = null;
    this.lineas = [];
    this.buscarDocumentos('');
  }

  cambiarCliente(): void {
    this.clienteSeleccionado = null;
    this.documentoSeleccionado = null;
    this.documentosResultados = [];
    this.lineas = [];
  }

  onBusquedaDocumentoChange(): void {
    this.busquedaDocumento$.next(this.filtroDocumento);
  }

  seleccionarDocumento(documento: DocumentoAsociable): void {
    this.documentoSeleccionado = documento;
    this.filtroDocumento = '';
    this.documentosResultados = [];
    this.cargarLineasDocumento();
  }

  cambiarDocumento(): void {
    this.documentoSeleccionado = null;
    this.lineas = [];
    this.buscarDocumentos('');
  }

  etiquetaDocumento(tipo: TipoDocumentoVenta): string {
    return ETIQUETAS_DOCUMENTO[tipo];
  }

  // --- Sección 3: tipo de corrección ---------------------------------------

  seleccionarTipo(tipo: TipoCorreccion): void {
    if (this.tipoCorreccion === tipo) return;
    this.tipoCorreccion = tipo;
    this.errorLineas = null;

    if (tipo === 'CORRIGE_TEXTO') {
      // No lleva líneas ni montos: se desmarcan todas para no enviarlas.
      this.lineas.forEach((l) => (l.incluida = false));
      this.descuento = 0;
    } else if (tipo === 'CORRIGE_DOCUMENTO') {
      // Anula el documento completo: entran todas las líneas y todas recuperan.
      this.lineas.forEach((l) => {
        l.incluida = true;
        l.cantidad = l.cantidadVendida;
        l.recuperaInventario = l.cantidadDisponible > 0;
      });
    }
  }

  etiquetaTipo(tipo: TipoCorreccion): string {
    return ETIQUETAS_TIPO_CORRECCION[tipo];
  }

  ayudaTipo(tipo: TipoCorreccion): string {
    return AYUDA_TIPO_CORRECCION[tipo];
  }

  get muestraDetalle(): boolean {
    return this.tipoCorreccion === 'CORRIGE_DOCUMENTO' || this.tipoCorreccion === 'CORRIGE_MONTO';
  }

  get muestraTexto(): boolean {
    return this.tipoCorreccion === 'CORRIGE_TEXTO';
  }

  // En CORRIGE_DOCUMENTO el usuario no elige qué líneas entran: entran todas.
  get lineasBloqueadas(): boolean {
    return this.tipoCorreccion === 'CORRIGE_DOCUMENTO';
  }

  // --- Sección 4: detalle ---------------------------------------------------

  get lineasIncluidas(): LineaEditable[] {
    return this.lineas.filter((l) => l.incluida);
  }

  subtotalLinea(linea: LineaEditable): number {
    return Math.max(linea.cantidad * linea.precioUnitario - (linea.descuento || 0), 0);
  }

  // Una línea sin cantidad disponible no puede recuperar inventario: ya se
  // devolvió todo lo que se había vendido de ella.
  puedeRecuperar(linea: LineaEditable): boolean {
    return linea.cantidadDisponible > 0;
  }

  tituloRecuperacion(linea: LineaEditable): string {
    return this.puedeRecuperar(linea)
      ? 'Devuelve esta cantidad a bodega al emitir la nota'
      : 'Ya se recuperó toda la cantidad vendida de este producto';
  }

  errorDeLinea(linea: LineaEditable): string | null {
    if (!linea.incluida) return null;
    if (!linea.cantidad || linea.cantidad <= 0) return 'La cantidad debe ser mayor que 0.';
    if (linea.precioUnitario < 0) return 'El precio no puede ser negativo.';
    if (linea.descuento < 0) return 'El descuento no puede ser negativo.';
    if (linea.descuento > linea.cantidad * linea.precioUnitario) {
      return 'El descuento no puede superar el subtotal de la línea.';
    }
    if (linea.recuperaInventario && linea.cantidad > linea.cantidadDisponible) {
      return `Solo hay ${linea.cantidadDisponible} disponible(s) para recuperar.`;
    }
    return null;
  }

  // --- Sección 7: totales ---------------------------------------------------
  // Previsualización: la cifra que manda es la que calcula el backend con
  // CalculadoraMontosVenta, usando el tipo de documento del original.

  get subtotalActual(): number {
    return this.lineasIncluidas.reduce((a, l) => a + l.cantidad * l.precioUnitario, 0);
  }

  get descuentoLineas(): number {
    return this.lineasIncluidas.reduce((a, l) => a + (l.descuento || 0), 0);
  }

  get descuentoTotal(): number {
    return this.descuentoLineas + (this.descuento || 0);
  }

  get baseActual(): number {
    return Math.max(this.subtotalActual - this.descuentoTotal, 0);
  }

  // Sin IVA si el documento original es exento o un voucher. En una boleta la
  // base es bruta y el neto se desglosa; en una factura la base es neta.
  private get sinIva(): boolean {
    const doc = this.documentoSeleccionado;
    return !doc || doc.exento || doc.tipoDocumento === 'VOUCHER';
  }

  private get esBoleta(): boolean {
    return this.documentoSeleccionado?.tipoDocumento === 'BOLETA';
  }

  get netoActual(): number {
    if (this.sinIva) return this.baseActual;
    return this.esBoleta ? Math.round(this.baseActual / (1 + TASA_IVA)) : this.baseActual;
  }

  get ivaActual(): number {
    if (this.sinIva) return 0;
    return this.esBoleta ? this.baseActual - this.netoActual : Math.round(this.baseActual * TASA_IVA);
  }

  get totalActual(): number {
    return this.netoActual + this.ivaActual;
  }

  // --- Guardado -------------------------------------------------------------

  get puedeGuardar(): boolean {
    if (this.guardando || this.cargando) return false;
    if (!this.documentoSeleccionado || !this.razonAsociacion.trim() || !this.tipoCorreccion) return false;
    if (this.tipoCorreccion === 'CORRIGE_TEXTO') return !!this.textoCorreccion.trim();
    return this.lineasIncluidas.length > 0 && this.lineasIncluidas.every((l) => !this.errorDeLinea(l));
  }

  guardar(): void {
    if (!this.puedeGuardar) return;

    const conError = this.lineasIncluidas.find((l) => this.errorDeLinea(l));
    if (conError) {
      this.errorLineas = `Revise la línea "${conError.descripcion}": ${this.errorDeLinea(conError)}`;
      return;
    }
    this.errorLineas = null;
    this.guardando = true;
    this.error = '';
    mostrarCargando(this.esEdicion ? 'Guardando nota de crédito' : 'Creando nota de crédito');

    // El id nunca se envía: lo genera la base de datos.
    const request: NotaCreditoRequest = {
      ventaId: this.documentoSeleccionado!.ventaId,
      tipoCorreccion: this.tipoCorreccion!,
      fecha: this.fecha,
      docAsociadoRazon: this.razonAsociacion.trim(),
      motivo: this.motivo || null,
      observaciones: this.observaciones || null,
      textoCorreccion: this.muestraTexto ? this.textoCorreccion : null,
      descuento: this.muestraTexto ? 0 : this.descuento || 0,
      items: this.muestraTexto
        ? []
        : this.lineasIncluidas.map((l) => ({
            productoId: l.productoId,
            ventaDetalleId: l.ventaDetalleId,
            cantidad: l.cantidad,
            precioUnitario: l.precioUnitario,
            descuento: l.descuento || 0,
            recuperaInventario: l.recuperaInventario,
          })),
    };

    const peticion = this.esEdicion
      ? this.notaCreditoService.actualizar(this.notaCreditoId!, request)
      : this.notaCreditoService.crear(request);

    peticion.subscribe({
      next: (nota) => {
        cerrarCargando().then(() => this.router.navigate(['/notas-credito', nota.id]));
        this.guardando = false;
      },
      error: (err) => {
        cerrarCargando();
        this.error = err?.error?.error ?? 'No se pudo guardar la nota de crédito.';
        this.guardando = false;
      },
    });
  }

  // --- Carga ----------------------------------------------------------------

  private buscarDocumentos(texto: string): void {
    if (!this.clienteSeleccionado) {
      this.documentosResultados = [];
      return;
    }
    this.notaCreditoService
      .documentosAsociables(this.clienteSeleccionado.id, texto.trim() || null)
      .subscribe({
        next: (documentos) => (this.documentosResultados = documentos),
        error: (err) => {
          this.error = err?.error?.error ?? 'No se pudieron cargar los documentos del cliente.';
        },
      });
  }

  private cargarLineasDocumento(): void {
    if (!this.documentoSeleccionado) return;
    this.cargando = true;
    this.notaCreditoService
      .lineasDocumento(this.documentoSeleccionado.ventaId, this.notaCreditoId)
      .subscribe({
        next: (lineas) => {
          this.lineas = lineas.map((l) => this.aLineaEditable(l));
          if (this.tipoCorreccion) {
            // Reaplica las reglas del tipo ya elegido sobre las líneas nuevas.
            const tipo = this.tipoCorreccion;
            this.tipoCorreccion = null;
            this.seleccionarTipo(tipo);
          }
          this.cargando = false;
        },
        error: (err) => {
          this.error = err?.error?.error ?? 'No se pudieron cargar las líneas del documento.';
          this.cargando = false;
        },
      });
  }

  private aLineaEditable(l: LineaDocumentoOriginal): LineaEditable {
    return {
      ventaDetalleId: l.ventaDetalleId,
      productoId: l.productoId,
      codigo: l.codigo,
      descripcion: l.descripcion,
      cantidadVendida: l.cantidad,
      cantidadDisponible: l.cantidadDisponible,
      incluida: false,
      cantidad: l.cantidadDisponible > 0 ? l.cantidadDisponible : l.cantidad,
      precioUnitario: l.precioUnitario,
      descuento: 0,
      recuperaInventario: false,
    };
  }

  private cargarNotaCredito(id: number): void {
    this.cargando = true;
    this.notaCreditoService.obtener(id).subscribe({
      next: (nota) => {
        if (nota.estado !== 'BORRADOR') {
          this.cargando = false;
          this.router.navigate(['/notas-credito', nota.id]);
          return;
        }

        this.clienteSeleccionado = {
          id: nota.clienteId,
          nombre: nota.clienteNombre,
          rut: nota.clienteRut,
          email: nota.clienteEmail,
          telefono: nota.clienteTelefono,
          direccion: nota.clienteDireccion,
        } as Cliente;
        this.documentoSeleccionado = {
          ventaId: nota.documentoAsociado.ventaId,
          tipoDocumento: nota.documentoAsociado.tipo,
          folio: nota.documentoAsociado.folio,
          numero: nota.documentoAsociado.numero,
          fecha: nota.documentoAsociado.fecha,
          montoTotal: nota.documentoAsociado.montoTotal ?? 0,
          exento: nota.exenta,
          bodegaId: nota.bodegaId,
          tieneNotasCredito: true,
        };
        this.razonAsociacion = nota.documentoAsociado.razon ?? '';
        this.tipoCorreccion = nota.tipoCorreccion;
        this.fecha = nota.fecha;
        this.motivo = nota.motivo ?? '';
        this.observaciones = nota.observaciones ?? '';
        this.textoCorreccion = nota.textoCorreccion ?? '';
        this.descuento = nota.descuento;

        // Se pide el propio id para que las líneas de esta nota no cuenten como
        // cantidad ya recuperada.
        this.notaCreditoService.lineasDocumento(nota.documentoAsociado.ventaId, id).subscribe({
          next: (lineas) => {
            this.lineas = lineas.map((l) => {
              const editable = this.aLineaEditable(l);
              const guardada = nota.lineas.find((x) => x.ventaDetalleId === l.ventaDetalleId);
              if (guardada) {
                editable.incluida = true;
                editable.cantidad = guardada.cantidad;
                editable.precioUnitario = guardada.precioUnitario;
                editable.descuento = guardada.descuento;
                editable.recuperaInventario = guardada.recuperaInventario;
              }
              return editable;
            });
            this.cargando = false;
          },
          error: (err) => {
            this.error = err?.error?.error ?? 'No se pudieron cargar las líneas del documento.';
            this.cargando = false;
          },
        });
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'No se pudo cargar la nota de crédito.';
        this.cargando = false;
      },
    });
  }
}
