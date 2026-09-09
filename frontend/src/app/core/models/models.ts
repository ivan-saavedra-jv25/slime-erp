export type Rol = 'SUPER_ADMIN' | 'ADMIN' | 'VENDEDOR' | 'COMPRADOR' | 'VISUALIZADOR';

export type AdminRol = 'SUPER_ADMIN' | 'ADMIN' | 'SUPPORT' | 'FINANCE' | 'AUDITOR';

export interface AdminSesion {
  token: string;
  adminId: number;
  nombre: string;
  email: string;
  adminRol: AdminRol;
  permisos: string[];
}

export interface Paginated<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

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

export interface PermisosUsuario {
  rol: Rol;
  permisosRol: Permiso[];
  permisosExtra: Permiso[];
}

export interface UsuarioPlataforma {
  id: number;
  tenantId: number;
  tenantNombre: string;
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
  codigoBarra: string | null;
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

export type TipoBodega = 'PRINCIPAL' | 'VENTAS' | 'BODEGAJE' | 'MIXTA';

export interface Bodega {
  id: number;
  nombre: string;
  tipo: TipoBodega;
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

export interface UsuarioBasico {
  id: number;
  nombre: string;
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
  folio: number;
  codigoSii: number | null;
  fecha: string;
  montoNeto: number;
  montoIva: number;
  montoTotal: number;
  descuento: number;
  observacion: string | null;
  detalle: VentaDetalle[];
}

export interface LibroVentasFila {
  ventaId: number;
  folio: number;
  codigoSii: number | null;
  fecha: string;
  tipoDocumento: string;
  clienteRut: string | null;
  clienteNombre: string;
  montoNetoAfecto: number;
  montoNetoExento: number;
  montoIva: number;
  montoTotal: number;
}

export interface LibroVentasSubtotal {
  tipoDocumento: string;
  cantidad: number;
  montoNetoAfecto: number;
  montoNetoExento: number;
  montoIva: number;
  montoTotal: number;
}

export interface LibroVentasResponse {
  desde: string;
  hasta: string;
  tipoDocumento: string | null;
  busqueda: string | null;
  filas: LibroVentasFila[];
  subtotales: LibroVentasSubtotal[];
  totalGeneral: LibroVentasSubtotal;
  totalFilas: number;
  pagina: number;
  tamano: number;
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

export type EstadoEmpresa = 'TRIAL' | 'ACTIVE' | 'SUSPENDED' | 'EXPIRED' | 'BLOCKED' | 'CANCELLED';

export const ESTADOS_EMPRESA: EstadoEmpresa[] = ['TRIAL', 'ACTIVE', 'SUSPENDED', 'EXPIRED', 'BLOCKED', 'CANCELLED'];

export const ETIQUETAS_ESTADO_EMPRESA: Record<EstadoEmpresa, string> = {
  TRIAL: 'Prueba',
  ACTIVE: 'Activa',
  SUSPENDED: 'Suspendida',
  EXPIRED: 'Vencida',
  BLOCKED: 'Bloqueada',
  CANCELLED: 'Cancelada',
};

export interface Empresa {
  id: number;
  nombre: string;
  rut: string;
  businessName?: string | null;
  plan: string;
  status: EstadoEmpresa;
  activo: boolean;
  fechaAlta: string;
  lastAccessAt?: string | null;
  usuariosActivos: number;
  saldoPendiente: number;
}

export interface EmpresaDetalle extends Empresa {
  usuariosTotales: number;
  suscripcionEstado?: string | null;
  alertasAbiertas: number;
}

export interface EmpresaFiltros {
  page?: number;
  limit?: number;
  id?: number;
  rut?: string;
  razonSocial?: string;
  nombreComercial?: string;
  estado?: EstadoEmpresa;
  plan?: string;
}

export interface CambiarEstadoRequest {
  estado: EstadoEmpresa;
  motivo: string;
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

export interface Plan {
  id: number;
  nombre: string;
  descripcion: string | null;
  precioMensual: number;
  precioAnual: number | null;
  maxUsuarios: number;
  maxDocumentos: number;
  modulos: string[];
  caracteristicas: string[];
  estado: 'ACTIVE' | 'INACTIVE';
}

export interface PlanRequest {
  nombre: string;
  descripcion?: string;
  precioMensual: number;
  precioAnual?: number;
  maxUsuarios: number;
  maxDocumentos: number;
  modulos: string[];
  caracteristicas?: string[];
  estado?: 'ACTIVE' | 'INACTIVE';
}

export type EstadoSuscripcion = 'TRIAL' | 'ACTIVE' | 'PAST_DUE' | 'SUSPENDED' | 'CANCELLED' | 'EXPIRED';

export interface Suscripcion {
  id: number;
  companyId: number;
  empresaNombre: string | null;
  planId: number;
  planNombre: string;
  estado: EstadoSuscripcion;
  fechaInicio: string;
  fechaVencimiento: string;
  cicloFacturacion: 'MONTHLY' | 'ANNUAL';
  precio: number;
  periodoGraciaDias: number;
}

export interface SuscripcionRequest {
  companyId: number;
  planId: number;
  fechaInicio: string;
  fechaVencimiento: string;
  cicloFacturacion: 'MONTHLY' | 'ANNUAL';
  precio: number;
  estado?: EstadoSuscripcion;
  periodoGraciaDias?: number;
}

export interface ExtenderSuscripcionRequest {
  nuevoVencimiento?: string;
  dias?: number;
  motivo?: string;
}

export interface CambiarPlanRequest {
  planId: number;
  motivo?: string;
}

export interface AuditLogResponse {
  id: number;
  adminUserId: number | null;
  adminNombre: string | null;
  adminEmail: string | null;
  companyId: number | null;
  companyNombre: string | null;
  action: string;
  modulo: string;
  entityType: string | null;
  entityId: number | null;
  oldValue: string | null;
  newValue: string | null;
  ipAddress: string | null;
  userAgent: string | null;
  creadoEn: string;
}

export interface AuditLogFiltros {
  page?: number;
  limit?: number;
  adminUserId?: number;
  companyId?: number;
  modulo?: string;
  action?: string;
  desde?: string;
  hasta?: string;
}

export type SeveridadAlertaPlataforma = 'CRITICAL' | 'WARNING' | 'INFO';
export type EstadoAlertaPlataforma = 'OPEN' | 'READ' | 'RESOLVED';

export interface Alerta {
  id: number;
  companyId: number | null;
  companyNombre: string | null;
  type: string;
  severity: SeveridadAlertaPlataforma;
  title: string;
  description: string | null;
  createdAt: string;
  readAt: string | null;
  resolvedAt: string | null;
  status: EstadoAlertaPlataforma;
}

export interface AlertaFiltros {
  page?: number;
  limit?: number;
  severity?: SeveridadAlertaPlataforma;
  status?: EstadoAlertaPlataforma;
  companyId?: number;
  tipo?: string;
}

export interface AlertaResumen {
  critical: number;
  warning: number;
  info: number;
}

export type EstadoCobranza = 'DEUDA' | 'PARCIAL' | 'PAGADO' | 'ANULADO';
export type EstadoPagoCobranza = 'CONFIRMADA' | 'ANULADA';

export interface CobranzaEmpresa {
  id: number;
  tenantId: number;
  concepto: string;
  periodo: string;
  montoTotal: number;
  montoPagado: number;
  saldoPendiente: number;
  estado: EstadoCobranza;
  fechaEmision: string;
  fechaVencimiento: string | null;
  fechaUltimoPago: string | null;
  observaciones: string | null;
  fechaAnulacion: string | null;
  motivoAnulacion: string | null;
}

export interface CobranzaPago {
  id: number;
  cobranzaEmpresaId: number;
  tenantId: number;
  fecha: string;
  monto: number;
  medioPago: MedioPago;
  estado: EstadoPagoCobranza;
  numeroOperacion: string | null;
  observaciones: string | null;
  usuarioAdminId: number | null;
  fechaAnulacion: string | null;
  motivoAnulacion: string | null;
}

export interface ResumenCobranza {
  totalEmitido: number;
  totalCobrado: number;
  saldoPendiente: number;
  cargosEnDeuda: number;
  cargosParciales: number;
  cargosPagados: number;
}

export interface EmitirCobranzaRequest {
  tenantId: number;
  concepto: string;
  periodo: string;
  montoTotal: number;
  fechaVencimiento?: string | null;
  observaciones?: string | null;
}

export interface PagoCobranzaRequest {
  monto: number;
  medioPago: MedioPago;
  numeroOperacion?: string | null;
  observaciones?: string | null;
}

export type EstadoPagoPlataforma = 'PAID' | 'CANCELLED';

export interface PagoPlataforma {
  id: number;
  cobranzaEmpresaId: number;
  companyId: number;
  companyNombre: string;
  suscripcionId: number | null;
  monto: number;
  metodo: MedioPago;
  estado: EstadoPagoPlataforma;
  referencia: string | null;
  fecha: string;
  adminNombre: string | null;
}

export interface PagoFiltros {
  page?: number;
  limit?: number;
  empresaId?: number;
  estado?: EstadoPagoPlataforma;
}

export interface RegistrarPagoManualRequest {
  companyId: number;
  suscripcionId?: number;
  monto: number;
  metodo: MedioPago;
  referencia?: string;
  fecha?: string;
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
