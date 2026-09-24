import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import {
  Cliente,
  DocumentoNotaCreditoAsociable,
  LineaNotaCreditoOriginal,
  TipoDocumentoVenta,
  TipoReversion,
} from '../../core/models/models';
import { NotaDebitoRequest, NotaDebitoService } from '../../core/services/nota-debito.service';
import { ClienteService } from '../../core/services/cliente.service';
import { MonedaPipe } from '../../core/pipes/moneda.pipe';
import { cerrarCargando, mostrarCargando } from '../../core/utils/swal-loading';
import { ConfirmActionDialog } from '../../core/components/confirm-action-dialog/confirm-action-dialog.component';
import { AYUDA_TIPO_REVERSION, ETIQUETAS_TIPO_REVERSION, TIPOS_REVERSION } from './estado-nota-debito';

const TASA_IVA = 0.19;
const MAX_TEXTO_CORRECCION = 2000;

const ETIQUETAS_DOCUMENTO: Record<TipoDocumentoVenta, string> = {
  FACTURA: 'Factura',
  BOLETA: 'Boleta',
  VOUCHER: 'Voucher',
};

// Una línea editable del formulario: la de la nota de crédito original más lo
// que el usuario decide revertir. `incluida` distingue las líneas que entran en
// la nota de débito de las que solo se muestran.
interface LineaEditable {
  notaCreditoDetalleId: number | null;
  productoId: number;
  codigo: string | null;
  descripcion: string;
  cantidadDisponible: number;
  incluida: boolean;
  cantidad: number;
  precioUnitario: number;
  descuento: number;
  revierteInventario: boolean;
}

function formatoFecha(fecha: Date): string {
  const anio = fecha.getFullYear();
  const mes = String(fecha.getMonth() + 1).padStart(2, '0');
  const dia = String(fecha.getDate()).padStart(2, '0');
  return `${anio}-${mes}-${dia}`;
}

@Component({
  selector: 'app-nota-debito-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, MatButtonModule, MatIconModule, MatCardModule, MonedaPipe, MatDialogModule],
  templateUrl: './nota-debito-form.component.html',
  styleUrl: './nota-debito-form.component.scss',
})
export class NotaDebitoFormComponent implements OnInit, OnDestroy {
  private readonly LIMITE_RESULTADOS = 8;

  readonly tiposReversion = TIPOS_REVERSION;
  readonly maxTextoCorreccion = MAX_TEXTO_CORRECCION;

  notaDebitoId: number | null = null;
  numeroNota = '';

  // Sección 1: nota de crédito a revertir
  clienteSeleccionado: Cliente | null = null;
  documentoSeleccionado: DocumentoNotaCreditoAsociable | null = null;
  ncRazon = '';

  // Sección 2: tipo de reversión
  tipoReversion: TipoReversion | null = null;

  // Sección 3: detalle
  lineas: LineaEditable[] = [];

  // Sección 4: corrección de texto
  textoCorreccion = '';

  // Sección 5: información complementaria
  fecha = formatoFecha(new Date());
  motivo = '';
  observaciones = '';

  // Sección 6: totales
  descuento = 0;

  filtroCliente = '';
  clientesResultados: Cliente[] = [];
  filtroDocumento = '';
  documentosResultados: DocumentoNotaCreditoAsociable[] = [];

  cargando = false;
  guardando = false;
  error = '';
  errorLineas: string | null = null;

  private readonly busquedaCliente$ = new Subject<string>();
  private readonly busquedaDocumento$ = new Subject<string>();

  constructor(
    private notaDebitoService: NotaDebitoService,
    private clienteService: ClienteService,
    private route: ActivatedRoute,
    private router: Router,
    private dialog: MatDialog
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
      this.notaDebitoId = Number(id);
      this.cargarNotaDebito(this.notaDebitoId);
    }
  }

  ngOnDestroy(): void {
    this.busquedaCliente$.complete();
    this.busquedaDocumento$.complete();
  }

  get esEdicion(): boolean {
    return this.notaDebitoId !== null;
  }

  // --- Sección 1: nota de crédito a revertir --------------------------------

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

  seleccionarDocumento(documento: DocumentoNotaCreditoAsociable): void {
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

  // --- Sección 2: tipo de reversión -----------------------------------------

  seleccionarTipo(tipo: TipoReversion): void {
    if (this.tipoReversion === tipo) return;
    this.tipoReversion = tipo;
    this.errorLineas = null;

    if (tipo === 'REVIERTE_TEXTO') {
      // No lleva líneas ni montos: se desmarcan todas para no enviarlas.
      this.lineas.forEach((l) => (l.incluida = false));
      this.descuento = 0;
    } else if (tipo === 'REVIERTE_DOCUMENTO') {
      // Revierte la nota de crédito completa: entran todas las líneas y todas
      // revierten inventario (lo recuperado por la NC vuelve a salir).
      this.lineas.forEach((l) => {
        l.incluida = true;
        l.cantidad = l.cantidadDisponible;
        l.revierteInventario = true;
      });
    }
  }

  etiquetaTipo(tipo: TipoReversion): string {
    return ETIQUETAS_TIPO_REVERSION[tipo];
  }

  ayudaTipo(tipo: TipoReversion): string {
    return AYUDA_TIPO_REVERSION[tipo];
  }

  get muestraDetalle(): boolean {
    return this.tipoReversion === 'REVIERTE_DOCUMENTO' || this.tipoReversion === 'REVIERTE_MONTO';
  }

  get muestraTexto(): boolean {
    return this.tipoReversion === 'REVIERTE_TEXTO';
  }

  // En REVIERTE_DOCUMENTO el usuario no elige qué líneas entran: entran todas.
  get lineasBloqueadas(): boolean {
    return this.tipoReversion === 'REVIERTE_DOCUMENTO';
  }

  // --- Sección 3: detalle ---------------------------------------------------

  get lineasIncluidas(): LineaEditable[] {
    return this.lineas.filter((l) => l.incluida);
  }

  subtotalLinea(linea: LineaEditable): number {
    return Math.max(linea.cantidad * linea.precioUnitario - (linea.descuento || 0), 0);
  }

  // Una línea sin cantidad disponible no puede revertir inventario: ya se
  // revirtió todo lo que la nota de crédito había recuperado de ella.
  puedeRevertir(linea: LineaEditable): boolean {
    return linea.cantidadDisponible > 0;
  }

  tituloReversion(linea: LineaEditable): string {
    return this.puedeRevertir(linea)
      ? 'Revierte esta cantidad de inventario al emitir la nota'
      : 'Ya se revirtió toda la cantidad disponible de este producto';
  }

  errorDeLinea(linea: LineaEditable): string | null {
    if (!linea.incluida) return null;
    if (!linea.cantidad || linea.cantidad <= 0) return 'La cantidad debe ser mayor que 0.';
    if (linea.cantidad > linea.cantidadDisponible) {
      return `Solo se pueden revertir ${linea.cantidadDisponible} (disponibles en la nota de crédito).`;
    }
    if (linea.precioUnitario < 0) return 'El precio no puede ser negativo.';
    if (linea.descuento < 0) return 'El descuento no puede ser negativo.';
    if (linea.descuento > linea.cantidad * linea.precioUnitario) {
      return 'El descuento no puede superar el subtotal de la línea.';
    }
    return null;
  }

  // --- Sección 6: totales ---------------------------------------------------
  // Previsualización: la cifra que manda es la que calcula el backend con
  // CalculadoraMontosVenta, usando el tipo de documento de la venta original.

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

  // Sin IVA si la venta original (vía la NC) fue exenta o un voucher. En una
  // boleta la base es bruta y el neto se desglosa; en una factura es neta.
  private get sinIva(): boolean {
    const doc = this.documentoSeleccionado;
    return !doc || doc.exenta || doc.docAsociadoTipo === 'VOUCHER';
  }

  private get esBoleta(): boolean {
    return this.documentoSeleccionado?.docAsociadoTipo === 'BOLETA';
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
    if (!this.documentoSeleccionado || !this.ncRazon.trim() || !this.tipoReversion) return false;
    if (this.tipoReversion === 'REVIERTE_TEXTO') return !!this.textoCorreccion.trim();
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
    mostrarCargando(this.esEdicion ? 'Guardando nota de débito' : 'Creando nota de débito');

    // El id nunca se envía: lo genera la base de datos.
    const request: NotaDebitoRequest = {
      notaCreditoId: this.documentoSeleccionado!.notaCreditoId,
      tipoReversion: this.tipoReversion!,
      fecha: this.fecha,
      ncRazon: this.ncRazon.trim(),
      motivo: this.motivo || null,
      observaciones: this.observaciones || null,
      textoCorreccion: this.muestraTexto ? this.textoCorreccion : null,
      descuento: this.muestraTexto ? 0 : this.descuento || 0,
      items: this.muestraTexto
        ? []
        : this.lineasIncluidas.map((l) => ({
            productoId: l.productoId,
            notaCreditoDetalleId: l.notaCreditoDetalleId,
            cantidad: l.cantidad,
            precioUnitario: l.precioUnitario,
            descuento: l.descuento || 0,
            revierteInventario: l.revierteInventario,
          })),
    };

    const peticion = this.esEdicion
      ? this.notaDebitoService.actualizar(this.notaDebitoId!, request)
      : this.notaDebitoService.crear(request);

    peticion.subscribe({
      next: (nota) => {
        cerrarCargando().then(() => this.router.navigate(['/notas-debito', nota.id]));
        this.guardando = false;
      },
      error: (err) => {
        cerrarCargando();
        this.error = err?.error?.error ?? 'No se pudo guardar la nota de débito.';
        this.guardando = false;
      },
    });
  }

  eliminar(): void {
    if (!this.esEdicion) return;
    const dialogRef = this.dialog.open(ConfirmActionDialog, {
      data: {
        titulo: 'Eliminar borrador',
        entidad: this.numeroNota || 'el borrador',
        accion: 'eliminar',
      },
      width: '480px',
    });
    dialogRef.afterClosed().subscribe((_motivo?: string) => {
      if (!_motivo || !this.esEdicion) return;
      this.guardando = true;
      this.error = '';
      this.notaDebitoService.eliminar(this.notaDebitoId!).subscribe({
        next: () => {
          this.guardando = false;
          this.router.navigate(['/notas-debito']);
        },
        error: (err) => {
          this.guardando = false;
          this.error = err?.error?.error ?? 'No se pudo eliminar la nota de débito.';
        },
      });
    });
  }

  // --- Carga ----------------------------------------------------------------

  private buscarDocumentos(texto: string): void {
    if (!this.clienteSeleccionado) {
      this.documentosResultados = [];
      return;
    }
    this.notaDebitoService
      .notasCreditoAsociables(this.clienteSeleccionado.id, texto.trim() || null)
      .subscribe({
        next: (documentos) => (this.documentosResultados = documentos),
        error: (err) => {
          this.error = err?.error?.error ?? 'No se pudieron cargar las notas de crédito del cliente.';
        },
      });
  }

  private cargarLineasDocumento(): void {
    if (!this.documentoSeleccionado) return;
    this.cargando = true;
    this.notaDebitoService
      .lineasNotaCredito(this.documentoSeleccionado.notaCreditoId, this.notaDebitoId)
      .subscribe({
        next: (lineas) => {
          this.lineas = lineas.map((l) => this.aLineaEditable(l));
          if (this.tipoReversion) {
            // Reaplica las reglas del tipo ya elegido sobre las líneas nuevas.
            const tipo = this.tipoReversion;
            this.tipoReversion = null;
            this.seleccionarTipo(tipo);
          }
          this.cargando = false;
        },
        error: (err) => {
          this.error = err?.error?.error ?? 'No se pudieron cargar las líneas de la nota de crédito.';
          this.cargando = false;
        },
      });
  }

  private aLineaEditable(l: LineaNotaCreditoOriginal): LineaEditable {
    return {
      notaCreditoDetalleId: l.notaCreditoDetalleId,
      productoId: l.productoId,
      codigo: l.codigo,
      descripcion: l.descripcion,
      cantidadDisponible: l.cantidadDisponible,
      incluida: false,
      cantidad: l.cantidadDisponible,
      precioUnitario: l.precioUnitario,
      descuento: 0,
      revierteInventario: l.cantidadDisponible > 0,
    };
  }

  private cargarNotaDebito(id: number): void {
    this.cargando = true;
    this.notaDebitoService.obtener(id).subscribe({
      next: (nota) => {
        if (nota.estado !== 'BORRADOR') {
          this.cargando = false;
          this.router.navigate(['/notas-debito', nota.id]);
          return;
        }

        this.numeroNota = nota.numero;
        this.clienteSeleccionado = {
          id: nota.clienteId,
          nombre: nota.clienteNombre,
          rut: nota.clienteRut,
          email: nota.clienteEmail,
          telefono: nota.clienteTelefono,
          direccion: nota.clienteDireccion,
        } as Cliente;
        const da = nota.documentoAsociado;
        this.documentoSeleccionado = {
          notaCreditoId: da.notaCreditoId,
          numero: da.numero,
          folio: da.ncFolio,
          fecha: da.fecha,
          clienteId: nota.clienteId,
          clienteNombre: nota.clienteNombre,
          clienteRut: nota.clienteRut,
          docAsociadoTipo: da.ncDocAsociadoTipo,
          docAsociadoFolio: da.ncFolio,
          exenta: nota.exenta,
          montoTotal: da.montoTotal ?? 0,
          montoDisponible: da.montoDisponible ?? 0,
          tieneNotasDebito: true,
        };
        this.ncRazon = da.razon ?? '';
        this.tipoReversion = nota.tipoReversion;
        this.fecha = nota.fecha;
        this.motivo = nota.motivo ?? '';
        this.observaciones = nota.observaciones ?? '';
        this.textoCorreccion = nota.textoCorreccion ?? '';
        this.descuento = nota.descuento;

        // Se pide el propio id para que las líneas de esta nota no cuenten como
        // cantidad ya revertida.
        this.notaDebitoService.lineasNotaCredito(da.notaCreditoId, id).subscribe({
          next: (lineas) => {
            this.lineas = lineas.map((l) => {
              const editable = this.aLineaEditable(l);
              const guardada = nota.lineas.find((x) => x.notaCreditoDetalleId === l.notaCreditoDetalleId);
              if (guardada) {
                editable.incluida = true;
                editable.cantidad = guardada.cantidad;
                editable.precioUnitario = guardada.precioUnitario;
                editable.descuento = guardada.descuento;
                editable.revierteInventario = guardada.revierteInventario;
              }
              return editable;
            });
            this.cargando = false;
          },
          error: (err) => {
            this.error = err?.error?.error ?? 'No se pudieron cargar las líneas de la nota de crédito.';
            this.cargando = false;
          },
        });
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'No se pudo cargar la nota de débito.';
        this.cargando = false;
      },
    });
  }
}