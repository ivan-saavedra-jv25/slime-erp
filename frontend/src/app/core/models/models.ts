export type Rol = 'SUPER_ADMIN' | 'ADMIN' | 'VENDEDOR' | 'COMPRADOR' | 'VISUALIZADOR';

export type Permiso =
  | 'CLIENTES_VER'
  | 'CLIENTES_EDITAR'
  | 'PROVEEDORES_VER'
  | 'PROVEEDORES_EDITAR'
  | 'PRODUCTOS_VER'
  | 'PRODUCTOS_EDITAR'
  | 'CATEGORIAS_VER'
  | 'CATEGORIAS_EDITAR'
  | 'BODEGAS_VER'
  | 'BODEGAS_EDITAR'
  | 'FORMAS_PAGO_VER'
  | 'FORMAS_PAGO_EDITAR'
  | 'MOVIMIENTOS_VER'
  | 'MOVIMIENTOS_EDITAR'
  | 'VENTAS_VER'
  | 'VENTAS_EDITAR'
  | 'COMPRAS_VER'
  | 'COMPRAS_EDITAR'
  | 'TESORERIA_VER'
  | 'TESORERIA_EDITAR'
  | 'TESORERIA_ANULAR'
  | 'USUARIOS_VER'
  | 'USUARIOS_EDITAR'
  | 'EMPRESAS_ADMINISTRAR';

export interface LoginResponse {
  token: string;
  usuarioId: number;
  tenantId: number;
  tenantNombre: string;
  nombre: string;
  email: string;
  rut: string;
  rol: Rol;
  permisos: Permiso[];
}

export interface Usuario {
  id: number;
  nombre: string;
  rut: string;
  email: string;
  rol: Rol;
  activo: boolean;
  fechaCreacion: string;
}

export interface PaginaResponse<T> {
  contenido: T[];
  total: number;
}

export interface Cliente {
  id: number;
  nombre: string;
  rut: string | null;
  email: string | null;
  telefono: string | null;
  direccion: string | null;
  razonSocial: string | null;
  giro: string | null;
  comuna: string | null;
  ciudad: string | null;
  activo: boolean;
}

export interface Proveedor {
  id: number;
  nombre: string;
  rut: string | null;
  email: string | null;
  telefono: string | null;
  direccion: string | null;
  activo: boolean;
}

export interface Producto {
  id: number;
  sku: string | null;
  nombre: string;
  descripcion: string | null;
  categoriaId: number | null;
  subcategoriaId: number | null;
  precioVenta: number;
  precioCompra: number;
  stockMinimo: number;
  activo: boolean;
}

export interface Categoria {
  id: number;
  nombre: string;
  activo: boolean;
  fechaCreacion: string;
}

export interface Subcategoria {
  id: number;
  categoriaId: number;
  nombre: string;
  activo: boolean;
  fechaCreacion: string;
}

export interface Bodega {
  id: number;
  nombre: string;
  principal: boolean;
  activo: boolean;
  fechaCreacion: string;
}

export interface StockPorBodega {
  bodegaId: number;
  bodegaNombre: string;
  cantidad: number;
}

export interface InventarioItem {
  productoId: number;
  sku: string | null;
  nombre: string;
  cantidad: number;
}

export type CategoriaFormaPago = 'GRATIS' | 'CREDITO' | 'CONTADO';

export interface FormaPago {
  id: number;
  nombre: string;
  categoria: CategoriaFormaPago;
  activo: boolean;
  fechaCreacion: string;
}

export type TipoMovimiento = 'ENTRADA' | 'SALIDA' | 'TRASLADO' | 'AJUSTE';

export interface MovimientoItem {
  productoId: number;
  cantidad: number;
}

export interface MovimientoDetalleItem {
  productoId: number;
  productoSku: string | null;
  productoNombre: string;
  cantidad: number;
}

export interface MovimientoHistorial {
  id: number;
  tipo: string;
  bodegaOrigenNombre: string;
  bodegaDestinoNombre: string;
  usuarioNombre: string;
  observacion: string | null;
  fecha: string;
  items: MovimientoDetalleItem[];
}

export type TipoDocumentoVenta = 'BOLETA' | 'FACTURA' | 'VOUCHER';

export interface VentaItem {
  productoId: number;
  cantidad: number;
  precioUnitario: number;
}

export interface VentaDetalle {
  productoId: number;
  cantidad: number;
  precioUnitario: number;
  subtotal: number;
}

export interface Venta {
  id: number;
  clienteId: number;
  formaPagoId: number;
  bodegaId: number;
  tipoDocumento: TipoDocumentoVenta;
  exento: boolean;
  fecha: string;
  montoNeto: number;
  montoIva: number;
  montoTotal: number;
  descuento: number;
  observacion: string | null;
  detalle: VentaDetalle[];
}

export interface CompraItem {
  productoId: number;
  cantidad: number;
  precioUnitario: number;
}

export interface CompraDetalle {
  productoId: number;
  cantidad: number;
  precioUnitario: number;
  subtotal: number;
}

export interface Compra {
  id: number;
  proveedorId: number;
  bodegaId: number;
  fecha: string;
  total: number;
  observacion: string | null;
  detalle: CompraDetalle[];
}

export type EstadoCuentaPorCobrar = 'DEUDA' | 'PARCIAL' | 'PAGADO' | 'ANULADO';
export type EstadoTransaccion = 'CONFIRMADA' | 'ANULADA';
export type MedioPago = 'EFECTIVO' | 'TRANSFERENCIA' | 'TARJETA' | 'CHEQUE';

export interface CuentaPorCobrar {
  id: number;
  ventaId: number;
  clienteId: number;
  fechaGeneracion: string;
  montoTotal: number;
  montoPagado: number;
  saldoPendiente: number;
  estado: EstadoCuentaPorCobrar;
  fechaUltimoPago: string | null;
  observaciones: string | null;
  usuarioAnuloId: number | null;
  fechaAnulacion: string | null;
  motivoAnulacion: string | null;
}

export interface TransaccionPago {
  id: number;
  cuentaPorCobrarId: number;
  ventaId: number;
  clienteId: number;
  fecha: string;
  monto: number;
  medioPago: MedioPago;
  estado: EstadoTransaccion;
  usuarioId: number | null;
  observaciones: string | null;
  transferenciaBancoOrigen: string | null;
  transferenciaBancoDestino: string | null;
  transferenciaNumeroOperacion: string | null;
  transferenciaFecha: string | null;
  tarjetaEntidad: string | null;
  tarjetaTipo: string | null;
  tarjetaNumeroOperacion: string | null;
  tarjetaFecha: string | null;
  chequeBanco: string | null;
  chequeNumero: string | null;
  chequeFechaEmision: string | null;
  chequeFechaPago: string | null;
  motivoAnulacion: string | null;
}

export interface ResumenTesoreria {
  totalPorCobrar: number;
  totalCobrado: number;
  saldoPendiente: number;
  cuentasEnDeuda: number;
  cuentasParciales: number;
  cuentasPagadas: number;
}

export interface Empresa {
  id: number;
  nombre: string;
  rut: string;
  plan: string;
  activo: boolean;
  fechaAlta: string;
}

export interface CrearEmpresaRequest {
  nombre: string;
  rut: string;
  plan?: string;
  adminNombre: string;
  adminRut: string;
  adminEmail: string;
  adminPassword: string;
}

export interface KpiResumen {
  ventasPeriodo: number;
  ventasPeriodoAnterior: number;
  variacionPct: number | null;
  comprasPeriodo: number;
  documentosEmitidos: number;
  totalClientes: number;
  totalProductos: number;
  stockDisponible: number;
  productosStockBajo: number;
  productosSinStock: number;
  cuentasPorCobrarSaldo: number;
  cuentasPorCobrarPendientes: number;
}

export type TipoAlertaDashboard = 'SIN_STOCK' | 'STOCK_BAJO' | 'CUENTAS_PENDIENTES';
export type SeveridadAlerta = 'ALTA' | 'MEDIA';

export interface AlertaDashboard {
  tipo: TipoAlertaDashboard;
  severidad: SeveridadAlerta;
  mensaje: string;
  ruta: string;
}

export interface VentaResumenItem {
  id: number;
  fecha: string;
  clienteNombre: string;
  total: number;
  tipoDocumento: TipoDocumentoVenta;
}

export interface DashboardResponse {
  kpis: KpiResumen;
  alertas: AlertaDashboard[];
  ultimasVentas: VentaResumenItem[];
}

export interface PuntoVenta {
  etiqueta: string;
  monto: number;
}
