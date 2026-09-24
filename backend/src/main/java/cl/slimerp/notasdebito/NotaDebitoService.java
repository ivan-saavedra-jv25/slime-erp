package cl.slimerp.notasdebito;

import cl.slimerp.catalogo.Cliente;
import cl.slimerp.catalogo.ClienteRepository;
import cl.slimerp.catalogo.Producto;
import cl.slimerp.catalogo.ProductoRepository;
import cl.slimerp.common.PaginaResponse;
import cl.slimerp.common.UsuarioActualService;
import cl.slimerp.config.TenantContext;
import cl.slimerp.cotizaciones.Cotizacion;
import cl.slimerp.cotizaciones.CotizacionRepository;
import cl.slimerp.cotizaciones.NumeroCotizacion;
import cl.slimerp.inventario.Bodega;
import cl.slimerp.inventario.BodegaRepository;
import cl.slimerp.inventario.MovimientoInventario;
import cl.slimerp.inventario.MovimientoInventarioHeader;
import cl.slimerp.inventario.MovimientoInventarioHeaderRepository;
import cl.slimerp.inventario.MovimientoInventarioRepository;
import cl.slimerp.inventario.StockService;
import cl.slimerp.inventario.TipoMovimiento;
import cl.slimerp.notascredito.NotaCredito;
import cl.slimerp.notascredito.NotaCreditoDetalle;
import cl.slimerp.notascredito.NotaCreditoRepository;
import cl.slimerp.notascredito.EstadoNotaCredito;
import cl.slimerp.notascredito.NumeroNotaCredito;
import cl.slimerp.notasventa.NotaVenta;
import cl.slimerp.notasventa.NotaVentaRepository;
import cl.slimerp.notasventa.NumeroNotaVenta;
import cl.slimerp.tenant.Usuario;
import cl.slimerp.tenant.UsuarioRepository;
import cl.slimerp.ventas.CalculadoraMontosVenta;
import cl.slimerp.ventas.TipoDocumentoVenta;
import cl.slimerp.ventas.Venta;
import cl.slimerp.ventas.VentaRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// Nota de Débito: el documento que revierte hacia atrás la recuperación de
// inventario que hizo una nota de crédito, y que cobra otra vez por su monto.
// Siempre se asocia a una NOTA DE CRÉDITO EMITIDA; no se asocia directo a la
// venta porque es el cierre de la cadena Venta -> NC -> ND.
//
// Alcance deliberado: sin XML, DTE ni envío al SII, y sin efectos sobre cuentas
// por cobrar ni tesorería. Emitir revierte la recuperación (SALIDA de lo que la
// NC entró); anular vuelve a entrar eso (reversa de la reversión). Cada cambio
// queda asentado en nota_debito_evento (append-only) y el vínculo con cada
// movimiento de inventario, en nota_debito_movimiento.
@Service
public class NotaDebitoService {

    private static final Set<String> CAMPOS_ORDEN = Set.of("fecha", "folio", "montoTotal");
    private static final int MAX_DOCUMENTOS_ASOCIABLES = 20;

    private final NotaDebitoRepository notaDebitoRepository;
    private final NotaDebitoDetalleRepository detalleRepository;
    private final NotaDebitoEventoRepository eventoRepository;
    private final NotaDebitoMovimientoRepository movimientoRepository;
    private final NotaDebitoFolioService folioService;
    private final NotaCreditoRepository notaCreditoRepository;
    private final ClienteRepository clienteRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final BodegaRepository bodegaRepository;
    private final StockService stockService;
    private final MovimientoInventarioRepository movimientoInventarioRepository;
    private final MovimientoInventarioHeaderRepository movimientoHeaderRepository;
    private final UsuarioActualService usuarioActualService;
    private final VentaRepository ventaRepository;
    private final NotaVentaRepository notaVentaRepository;
    private final CotizacionRepository cotizacionRepository;

    public NotaDebitoService(NotaDebitoRepository notaDebitoRepository,
                             NotaDebitoDetalleRepository detalleRepository,
                             NotaDebitoEventoRepository eventoRepository,
                             NotaDebitoMovimientoRepository movimientoRepository,
                             NotaDebitoFolioService folioService,
                             NotaCreditoRepository notaCreditoRepository,
                             ClienteRepository clienteRepository,
                             ProductoRepository productoRepository,
                             UsuarioRepository usuarioRepository,
                             BodegaRepository bodegaRepository,
                             StockService stockService,
                             MovimientoInventarioRepository movimientoInventarioRepository,
                             MovimientoInventarioHeaderRepository movimientoHeaderRepository,
                             UsuarioActualService usuarioActualService,
                             VentaRepository ventaRepository,
                             NotaVentaRepository notaVentaRepository,
                             CotizacionRepository cotizacionRepository) {
        this.notaDebitoRepository = notaDebitoRepository;
        this.detalleRepository = detalleRepository;
        this.eventoRepository = eventoRepository;
        this.movimientoRepository = movimientoRepository;
        this.folioService = folioService;
        this.notaCreditoRepository = notaCreditoRepository;
        this.clienteRepository = clienteRepository;
        this.productoRepository = productoRepository;
        this.usuarioRepository = usuarioRepository;
        this.bodegaRepository = bodegaRepository;
        this.stockService = stockService;
        this.movimientoInventarioRepository = movimientoInventarioRepository;
        this.movimientoHeaderRepository = movimientoHeaderRepository;
        this.usuarioActualService = usuarioActualService;
        this.ventaRepository = ventaRepository;
        this.notaVentaRepository = notaVentaRepository;
        this.cotizacionRepository = cotizacionRepository;
    }

    // --- DTOs de respuesta ---------------------------------------------------

    public record LineaNotaDebito(
            Long id, Long productoId, Long notaCreditoDetalleId, String codigo, String descripcion,
            BigDecimal cantidad, BigDecimal precioUnitario, BigDecimal descuento, BigDecimal subtotal,
            boolean revierteInventario) {
    }

    public record EventoNotaDebito(
            LocalDateTime fecha, String usuario, AccionNotaDebito accion,
            EstadoNotaDebito estadoAnterior, EstadoNotaDebito estadoNuevo, String detalle) {
    }

    // La nota de crédito asociada, con el disponible que queda para revertir.
    // El disponible se recalcula con cada emisión/anulación (nunca se cachea).
    public record DocumentoAsociado(
            Long notaCreditoId, String numero, LocalDate fecha,
            Integer ncFolio, TipoDocumentoVenta ncDocAsociadoTipo,
            BigDecimal montoTotal, BigDecimal montoDisponible, String razon) {
    }

    public record DocumentoNotaCreditoAsociable(
            Long notaCreditoId, String numero, Integer folio, LocalDate fecha,
            Long clienteId, String clienteNombre, String clienteRut,
            TipoDocumentoVenta docAsociadoTipo, Integer docAsociadoFolio,
            boolean exenta,
            BigDecimal montoTotal, BigDecimal montoDisponible,
            boolean tieneNotasDebito) {
    }

    public record LineaNotaCreditoOriginal(
            Long notaCreditoDetalleId, Long productoId, String codigo, String descripcion,
            BigDecimal cantidad, BigDecimal cantidadRevertida, BigDecimal cantidadDisponible,
            BigDecimal precioUnitario, BigDecimal subtotal) {
    }

    // headerId apunta a la cabecera de movimiento_inventario: es lo que permite
    // abrir el detalle y las exportaciones del módulo de Inventario desde la
    // trazabilidad de la nota de débito.
    public record MovimientoRelacionado(
            Long movimientoInventarioId, Long headerId, TipoMovimientoNotaDebito tipo, LocalDateTime fecha,
            Long productoId, String producto, BigDecimal cantidad, Long bodegaId, String bodega) {
    }

    // Eslabón de la cadena de trazabilidad hacia atrás (de arriba hacia abajo):
    // Cotización -> Nota de Venta -> Venta -> Nota de Crédito. La nota de
    // débito y el inventario los pinta el frontend con la información local.
    // Los eslabones Venta/Nota de Venta/Cotización pueden faltar si la venta se
    // registró sin origen (vena directa) o la nota de venta no viene de una
    // cotización.
    public record EslabonCadena(
            String tipo,        // COTIZACION | NOTA_VENTA | VENTA | NOTA_CREDITO
            Long documentoId,
            String numero,
            LocalDate fecha,
            BigDecimal montoTotal) {
    }

    public record NotaDebitoResumen(
            Long id, Integer folio, String numero, EstadoNotaDebito estado,
            TipoReversion tipoReversion, LocalDate fecha,
            Long clienteId, String clienteNombre, String clienteRut,
            String ncNumero, Integer ncFolio, TipoDocumentoVenta ncDocAsociadoTipo,
            String motivo, BigDecimal montoTotal, ImpactoInventario impactoInventario) {
    }

    public record NotaDebitoCompleta(
            Long id, Integer folio, String numero, EstadoNotaDebito estado,
            TipoReversion tipoReversion, LocalDate fecha,
            Long clienteId, String clienteNombre, String clienteRazonSocial, String clienteRut,
            String clienteDireccion, String clienteEmail, String clienteTelefono,
            Long usuarioId, String usuarioNombre,
            DocumentoAsociado documentoAsociado,
            String motivo, String observaciones, String textoCorreccion,
            Long bodegaId, String bodegaNombre, boolean exenta, String moneda,
            BigDecimal descuento, BigDecimal montoSubtotal, BigDecimal montoDescuento,
            BigDecimal montoNeto, BigDecimal montoIva, BigDecimal montoTotal,
            LocalDateTime fechaEmision, LocalDateTime fechaAnulacion,
            ImpactoInventario impactoInventario,
            List<LineaNotaDebito> lineas,
            List<MovimientoRelacionado> movimientosInventario,
            List<EventoNotaDebito> historial) {
    }

    public record ConteoPorEstado(EstadoNotaDebito estado, int cantidad, BigDecimal monto) {
    }

    public record ConteoPorTipo(TipoReversion tipoReversion, int cantidad, BigDecimal monto) {
    }

    public record DashboardNotasDebito(
            LocalDate desde, LocalDate hasta,
            int cantidad, int borradores, int emitidas, int anuladas,
            BigDecimal montoTotalEmitido, int conReversionInventario, int notasCreditoRevertidas,
            List<ConteoPorEstado> porEstado, List<ConteoPorTipo> porTipoReversion) {
    }

    // --- Escritura -----------------------------------------------------------

    @Transactional
    public NotaDebitoCompleta crear(NotaDebitoRequest request) {
        Long tenantId = TenantContext.getTenantId();
        Long usuarioId = usuarioActualService.idUsuarioActual(tenantId);

        NotaCredito nc = buscarNotaCredito(request.notaCreditoId(), tenantId);
        Cliente cliente = clienteRepository.findById(nc.getClienteId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "La nota de crédito asociada apunta a un cliente inexistente"));

        NotaDebito nd = NotaDebito.builder()
                .tenantId(tenantId)
                .folio(folioService.siguienteFolio(tenantId))
                // Cliente, bodega y base impositiva se heredan de la nota de
                // crédito: la nota de débito cobra lo que esa NC devolvió.
                .clienteId(cliente.getId())
                .usuarioId(usuarioId)
                .estado(EstadoNotaDebito.BORRADOR)
                .tipoReversion(request.tipoReversion())
                .fecha(request.fecha())
                .motivo(request.motivo())
                .observaciones(request.observaciones())
                .bodegaId(bodegaDe(nc, tenantId))
                .exenta(nc.isExenta())
                .moneda("CLP")
                .notaCreditoId(nc.getId())
                .ncDocAsociadoTipo(nc.getDocAsociadoTipo())
                .ncFolio(nc.getFolio())
                .ncFecha(nc.getFecha())
                .ncMontoTotal(nc.getMontoTotal())
                .ncRazon(request.ncRazon())
                .build();

        aplicarReversion(nd, request, nc, tenantId, null);
        nd = notaDebitoRepository.save(nd);

        registrarEvento(nd, AccionNotaDebito.CREADA, null, EstadoNotaDebito.BORRADOR,
                etiquetaTipo(nd.getTipoReversion()) + " sobre " + numeroNotaCredito(nc), usuarioId);
        return detalleDe(nd);
    }

    @Transactional
    public NotaDebitoCompleta actualizar(Long id, NotaDebitoRequest request) {
        Long tenantId = TenantContext.getTenantId();
        Long usuarioId = usuarioActualService.idUsuarioActual(tenantId);
        NotaDebito nd = buscarEntidad(id, tenantId);
        exigirEstado(nd, "editar", EstadoNotaDebito.BORRADOR);

        // La nota de crédito asociada no se cambia: revertir otra es otra nota
        // de débito, no una edición de esta.
        if (!nd.getNotaCreditoId().equals(request.notaCreditoId())) {
            throw new IllegalArgumentException(
                    "No se puede cambiar la nota de crédito asociada de una nota de débito ya creada");
        }
        NotaCredito nc = buscarNotaCredito(nd.getNotaCreditoId(), tenantId);

        TipoReversion tipoAnterior = nd.getTipoReversion();
        nd.setTipoReversion(request.tipoReversion());
        nd.setFecha(request.fecha());
        nd.setMotivo(request.motivo());
        nd.setObservaciones(request.observaciones());
        nd.setNcRazon(request.ncRazon());
        nd.setFechaActualizacion(LocalDateTime.now());

        nd.getDetalle().clear();
        aplicarReversion(nd, request, nc, tenantId, nd.getId());
        nd = notaDebitoRepository.save(nd);

        String detalle = tipoAnterior != nd.getTipoReversion()
                ? "Tipo de reversión: " + etiquetaTipo(tipoAnterior) + " → " + etiquetaTipo(nd.getTipoReversion())
                : null;
        registrarEvento(nd, AccionNotaDebito.EDITADA, EstadoNotaDebito.BORRADOR, EstadoNotaDebito.BORRADOR,
                detalle, usuarioId);
        return detalleDe(nd);
    }

    @Transactional
    public void eliminar(Long id) {
        Long tenantId = TenantContext.getTenantId();
        NotaDebito nd = buscarEntidad(id, tenantId);
        exigirEstado(nd, "eliminar", EstadoNotaDebito.BORRADOR);
        // El folio ya consumido no se reutiliza: queda un hueco en la numeración,
        // igual que en notas de crédito y notas de venta.
        notaDebitoRepository.delete(nd);
    }

    @Transactional
    public NotaDebitoCompleta emitir(Long id) {
        Long tenantId = TenantContext.getTenantId();
        Long usuarioId = usuarioActualService.idUsuarioActual(tenantId);
        NotaDebito nd = buscarEntidad(id, tenantId);
        exigirEstado(nd, "emitir", EstadoNotaDebito.BORRADOR);

        List<NotaDebitoDetalle> aRevertir = nd.getTipoReversion() == TipoReversion.REVIERTE_TEXTO
                ? List.of()
                : nd.getDetalle().stream().filter(NotaDebitoDetalle::isRevierteInventario).toList();

        if (!aRevertir.isEmpty()) {
            if (nd.getBodegaId() == null) {
                throw new IllegalArgumentException(
                        "No se puede revertir inventario: la bodega no está definida");
            }
            validarCantidadesDisponibles(nd, aRevertir, tenantId);
        }

        if (nd.getTipoReversion() != TipoReversion.REVIERTE_TEXTO) {
            validarMontoDisponible(nd, tenantId);
        }

        // Las salidas cuelgan de una cabecera de movimiento para que la reversión
        // aparezca en el historial de Inventario y reutilice su pantalla de
        // detalle y sus exportaciones a PDF y Excel.
        Long headerId = aRevertir.isEmpty() ? null : crearHeader(nd, usuarioId, TipoMovimiento.SALIDA,
                "Reversión de Nota de Crédito mediante " + NumeroNotaDebito.formatear(nd.getFolio()));

        for (NotaDebitoDetalle linea : aRevertir) {
            MovimientoInventario mov = stockService.sumar(tenantId, linea.getProductoId(), nd.getBodegaId(),
                    linea.getCantidad().negate(), TipoMovimiento.SALIDA_NOTA_CREDITO, headerId, nd.getId());
            movimientoRepository.save(NotaDebitoMovimiento.builder()
                    .tenantId(tenantId)
                    .notaDebitoId(nd.getId())
                    .notaDebitoDetalleId(linea.getId())
                    .movimientoInventarioId(mov.getId())
                    .tipo(TipoMovimientoNotaDebito.REVERSION)
                    .build());
        }

        EstadoNotaDebito anterior = nd.getEstado();
        nd.setEstado(EstadoNotaDebito.EMITIDA);
        nd.setFechaEmision(LocalDateTime.now());
        nd.setFechaActualizacion(LocalDateTime.now());
        nd = notaDebitoRepository.save(nd);

        String detalle = nd.getDetalle().size() + " línea(s), " + aRevertir.size()
                + " con reversión de inventario";
        registrarEvento(nd, AccionNotaDebito.EMITIDA, anterior, EstadoNotaDebito.EMITIDA, detalle, usuarioId);
        return detalleDe(nd);
    }

    // Anular revierte la reversión (la mercadería vuelve a entrar al stock). Las
    // filas de REVERSION no se borran: se agrega una fila REVERSA_ANULACION
    // apuntando al movimiento inverso, porque el historial de inventario es
    // append-only. Al quedar ANULADA, sus cantidades vuelven a estar disponibles
    // y el monto revertido de la NC vuelve a subir automáticamente.
    @Transactional
    public NotaDebitoCompleta anular(Long id, String motivo) {
        Long tenantId = TenantContext.getTenantId();
        Long usuarioId = usuarioActualService.idUsuarioActual(tenantId);
        NotaDebito nd = buscarEntidad(id, tenantId);
        exigirEstado(nd, "anular", EstadoNotaDebito.EMITIDA);

        List<NotaDebitoMovimiento> reversiones = movimientoRepository
                .findByTenantIdAndNotaDebitoIdAndTipo(tenantId, nd.getId(), TipoMovimientoNotaDebito.REVERSION);

        Long headerId = reversiones.isEmpty() ? null : crearHeader(nd, usuarioId, TipoMovimiento.ENTRADA,
                "Anulación de " + NumeroNotaDebito.formatear(nd.getFolio()));

        for (NotaDebitoMovimiento reversion : reversiones) {
            MovimientoInventario original = movimientoInventarioRepository
                    .findById(reversion.getMovimientoInventarioId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No se encontró el movimiento de inventario a revertir: "
                                    + reversion.getMovimientoInventarioId()));

            MovimientoInventario reversa = stockService.sumar(tenantId, original.getProductoId(),
                    original.getBodegaId(), original.getCantidad(),
                    TipoMovimiento.ENTRADA_NOTA_DEBITO, headerId, nd.getId());

            movimientoRepository.save(NotaDebitoMovimiento.builder()
                    .tenantId(tenantId)
                    .notaDebitoId(nd.getId())
                    .notaDebitoDetalleId(reversion.getNotaDebitoDetalleId())
                    .movimientoInventarioId(reversa.getId())
                    .tipo(TipoMovimientoNotaDebito.REVERSA_ANULACION)
                    .build());
        }

        EstadoNotaDebito anterior = nd.getEstado();
        nd.setEstado(EstadoNotaDebito.ANULADA);
        nd.setFechaAnulacion(LocalDateTime.now());
        nd.setFechaActualizacion(LocalDateTime.now());
        nd = notaDebitoRepository.save(nd);

        registrarEvento(nd, AccionNotaDebito.ANULADA, anterior, EstadoNotaDebito.ANULADA, motivo, usuarioId);
        return detalleDe(nd);
    }

    // --- Lectura -------------------------------------------------------------

    @Transactional(readOnly = true)
    public NotaDebitoCompleta obtener(Long id) {
        return detalleDe(buscarEntidad(id, TenantContext.getTenantId()));
    }

    // Toda la línea de documentos asociados a la nota de débito, de arriba
    // hacia abajo. Recorre la cadena por las FK duras (NC->venta->nota_venta->
    // cotización); los eslabones que no existen (venta directa o NV sin
    // cotización) simplemente se omiten.
    @Transactional(readOnly = true)
    public List<EslabonCadena> cadenaDe(Long id) {
        Long tenantId = TenantContext.getTenantId();
        NotaDebito nd = buscarEntidad(id, tenantId);

        List<EslabonCadena> cadena = new ArrayList<>();

        NotaCredito nc = notaCreditoRepository.findByIdAndTenantId(nd.getNotaCreditoId(), tenantId).orElse(null);
        if (nc != null) {
            cadena.add(new EslabonCadena("NOTA_CREDITO", nc.getId(),
                    NumeroNotaCredito.formatear(nc.getFolio()), nc.getFecha(), nc.getMontoTotal()));

            Venta venta = ventaRepository
                    .findByIdAndTenantIdAndActivoTrue(nc.getVentaId(), tenantId).orElse(null);
            if (venta != null) {
                cadena.add(new EslabonCadena("VENTA", venta.getId(),
                        nc.getDocAsociadoTipo() + " N.º " + venta.getFolio(),
                        venta.getFecha().toLocalDate(), venta.getMontoTotal()));

                if (venta.getNotaVentaId() != null) {
                    NotaVenta nv = notaVentaRepository
                            .findByIdAndTenantId(venta.getNotaVentaId(), tenantId).orElse(null);
                    if (nv != null) {
                        cadena.add(new EslabonCadena("NOTA_VENTA", nv.getId(),
                                NumeroNotaVenta.formatear(nv.getFolio()), nv.getFechaEmision(), nv.getMontoTotal()));

                        if (nv.getCotizacionId() != null) {
                            Cotizacion cotizacion = cotizacionRepository
                                    .findByIdAndTenantId(nv.getCotizacionId(), tenantId).orElse(null);
                            if (cotizacion != null) {
                                cadena.add(new EslabonCadena("COTIZACION", cotizacion.getId(),
                                        NumeroCotizacion.formatear(cotizacion.getFolio()),
                                        cotizacion.getFechaEmision(), cotizacion.getMontoTotal()));
                            }
                        }
                    }
                }
            }
        }

        // Se invierten para devolverlos de la cotización hacia la nota de
        // crédito (arriba -> abajo).
        java.util.Collections.reverse(cadena);
        return cadena;
    }

    @Transactional(readOnly = true)
    public NotaDebito obtenerEntidad(Long id) {
        NotaDebito nd = buscarEntidad(id, TenantContext.getTenantId());
        nd.getDetalle().size(); // fuerza la carga: open-in-view está deshabilitado
        return nd;
    }

    // Notas de crédito EMITIDAS que pueden revertirse. Se limita a las más
    // recientes: el formulario es un buscador, no un listado completo. Una NC
    // puede ser revertida por varias NDs (reversión parcial), así que se listan
    // todas con el disponible calculado dinámicamente.
    @Transactional(readOnly = true)
    public List<DocumentoNotaCreditoAsociable> notasCreditoAsociables(Long clienteId, String q) {
        Long tenantId = TenantContext.getTenantId();

        Specification<NotaCredito> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("tenantId"), tenantId),
                cb.equal(root.get("estado"), EstadoNotaCredito.EMITIDA));
        if (clienteId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("clienteId"), clienteId));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(filtroAsociables(tenantId, q.trim()));
        }

        var pageable = PageRequest.of(0, MAX_DOCUMENTOS_ASOCIABLES, Sort.by(Sort.Direction.DESC, "fecha"));
        List<NotaCredito> notas = notaCreditoRepository.findAll(spec, pageable).getContent();

        Map<Long, Cliente> clientes = clientesDeNotasCredito(tenantId, notas);
        Map<Long, BigDecimal> revertidos = montosRevertidos(tenantId, notas);

        return notas.stream()
                .map(nc -> new DocumentoNotaCreditoAsociable(
                        nc.getId(), numeroNotaCredito(nc), nc.getFolio(), nc.getFecha(),
                        nc.getClienteId(), clientes.get(nc.getId()) != null ? clientes.get(nc.getId()).getNombre() : null,
                        clientes.get(nc.getId()) != null ? clientes.get(nc.getId()).getRut() : null,
                        nc.getDocAsociadoTipo(), nc.getDocAsociadoFolio(), nc.isExenta(),
                        nc.getMontoTotal(),
                        nc.getMontoTotal().subtract(revertidos.getOrDefault(nc.getId(), BigDecimal.ZERO)).max(BigDecimal.ZERO),
                        notaDebitoRepository.existsByTenantIdAndNotaCreditoId(tenantId, nc.getId())))
                .toList();
    }

    // Líneas de la nota de crédito con la cantidad todavía disponible para
    // revertir. Es la fuente del detalle del formulario. Solo las líneas que
    // recuperaron inventario pueden revertirlo: las demás solo aportan monto.
    @Transactional(readOnly = true)
    public List<LineaNotaCreditoOriginal> lineasNotaCredito(Long notaCreditoId, Long excluyendoNotaDebitoId) {
        Long tenantId = TenantContext.getTenantId();
        NotaCredito nc = buscarNotaCredito(notaCreditoId, tenantId);
        Map<Long, BigDecimal> revertidas = cantidadesRevertidas(tenantId, notaCreditoId, excluyendoNotaDebitoId);
        Map<Long, Producto> productos = productosDe(nc.getDetalle().stream()
                .map(NotaCreditoDetalle::getProductoId).toList());

        return nc.getDetalle().stream()
                .filter(NotaCreditoDetalle::isRecuperaInventario)
                .map(d -> {
                    Producto p = productos.get(d.getProductoId());
                    BigDecimal revertida = revertidas.getOrDefault(d.getId(), BigDecimal.ZERO);
                    BigDecimal disponible = d.getCantidad().subtract(revertida).max(BigDecimal.ZERO);
                    return new LineaNotaCreditoOriginal(
                            d.getId(), d.getProductoId(),
                            p != null ? p.getSku() : null,
                            p != null ? p.getNombre() : "Producto " + d.getProductoId(),
                            d.getCantidad(), revertida, disponible,
                            d.getPrecioUnitario(), d.getSubtotal());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public PaginaResponse<NotaDebitoResumen> buscar(EstadoNotaDebito estado, Long clienteId,
                                                    TipoReversion tipoReversion, Long notaCreditoId,
                                                    LocalDate desde, LocalDate hasta,
                                                    String q, String orden, String direccion,
                                                    int pagina, int tamano) {
        if (pagina < 0) {
            throw new IllegalArgumentException("La página debe ser 0 o mayor");
        }
        if (tamano < 1) {
            throw new IllegalArgumentException("El tamaño de página debe ser mayor que 0");
        }
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha 'desde' no puede ser posterior a 'hasta'");
        }
        Long tenantId = TenantContext.getTenantId();

        // Se arma dinámicamente en vez de usar "(:param IS NULL OR ...)" en JPQL:
        // el driver de Postgres no logra inferir el tipo de un parámetro que solo
        // se compara contra IS NULL (ver NotaVentaService.buscar).
        Specification<NotaDebito> spec = (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);
        if (estado != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("estado"), estado));
        }
        if (clienteId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("clienteId"), clienteId));
        }
        if (tipoReversion != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("tipoReversion"), tipoReversion));
        }
        if (notaCreditoId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("notaCreditoId"), notaCreditoId));
        }
        if (desde != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("fecha"), desde));
        }
        if (hasta != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("fecha"), hasta));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(filtroBusqueda(tenantId, q.trim()));
        }

        var pageable = PageRequest.of(pagina, tamano, ordenamiento(orden, direccion));
        var resultado = notaDebitoRepository.findAll(spec, pageable);

        List<NotaDebito> notas = resultado.getContent();
        Map<Long, Cliente> clientes = clientesDe(tenantId, notas);
        List<NotaDebitoResumen> contenido = notas.stream()
                .map(n -> resumenDe(n, clientes.get(n.getClienteId())))
                .toList();
        return new PaginaResponse<>(contenido, resultado.getTotalElements());
    }

    @Transactional(readOnly = true)
    public DashboardNotasDebito dashboard(LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha 'desde' no puede ser posterior a 'hasta'");
        }
        Long tenantId = TenantContext.getTenantId();
        List<NotaDebito> notas = notaDebitoRepository
                .findByTenantIdAndFechaBetweenOrderByFolioAsc(tenantId, desde, hasta);

        Map<EstadoNotaDebito, List<NotaDebito>> porEstado = notas.stream()
                .collect(Collectors.groupingBy(NotaDebito::getEstado));
        Map<TipoReversion, List<NotaDebito>> porTipo = notas.stream()
                .collect(Collectors.groupingBy(NotaDebito::getTipoReversion));

        // Solo las emitidas son documentos con efecto: un borrador todavía no lo
        // es y una anulada dejó de serlo.
        List<NotaDebito> emitidas = porEstado.getOrDefault(EstadoNotaDebito.EMITIDA, List.of());
        BigDecimal montoEmitido = emitidas.stream()
                .map(NotaDebito::getMontoTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int conReversion = (int) emitidas.stream()
                .filter(n -> n.getDetalle().stream().anyMatch(NotaDebitoDetalle::isRevierteInventario))
                .count();
        int notasCreditoRevertidas = (int) emitidas.stream().map(NotaDebito::getNotaCreditoId).distinct().count();

        List<ConteoPorEstado> conteosEstado = Arrays.stream(EstadoNotaDebito.values())
                .map(e -> new ConteoPorEstado(e,
                        porEstado.getOrDefault(e, List.of()).size(),
                        montoDe(porEstado.getOrDefault(e, List.of()))))
                .toList();
        List<ConteoPorTipo> conteosTipo = Arrays.stream(TipoReversion.values())
                .map(t -> new ConteoPorTipo(t,
                        porTipo.getOrDefault(t, List.of()).size(),
                        montoDe(porTipo.getOrDefault(t, List.of()))))
                .toList();

        return new DashboardNotasDebito(
                desde, hasta, notas.size(),
                porEstado.getOrDefault(EstadoNotaDebito.BORRADOR, List.of()).size(),
                emitidas.size(),
                porEstado.getOrDefault(EstadoNotaDebito.ANULADA, List.of()).size(),
                montoEmitido, conReversion, notasCreditoRevertidas,
                conteosEstado, conteosTipo);
    }

    // --- Reglas por tipo de reversión ----------------------------------------

    // El tipo de reversión determina el comportamiento: REVIERTE_DOCUMENTO
    // precarga la NC completa, REVIERTE_MONTO trabaja con las líneas que indique
    // el usuario, y REVIERTE_TEXTO no lleva líneas ni montos.
    private void aplicarReversion(NotaDebito nd, NotaDebitoRequest request, NotaCredito nc,
                                  Long tenantId, Long notaDebitoId) {
        List<NotaDebitoRequest.Item> items = request.itemsOVacio();

        if (request.tipoReversion() == TipoReversion.REVIERTE_TEXTO) {
            if (!items.isEmpty()) {
                throw new IllegalArgumentException(
                        "Una nota de débito que revierte texto no puede tener líneas de detalle");
            }
            if (request.textoCorreccion() == null || request.textoCorreccion().isBlank()) {
                throw new IllegalArgumentException("Debe indicar el texto de la corrección");
            }
            nd.setTextoCorreccion(request.textoCorreccion());
            aplicarMontosEnCero(nd);
            return;
        }

        nd.setTextoCorreccion(null);

        // REVIERTE_DOCUMENTO revierte la NC completa: si no vienen líneas se
        // precargan todas las de la NC que recuperaron inventario, con reversión
        // de inventario activada por defecto.
        if (items.isEmpty() && request.tipoReversion() == TipoReversion.REVIERTE_DOCUMENTO) {
            items = nc.getDetalle().stream()
                    .filter(NotaCreditoDetalle::isRecuperaInventario)
                    .map(d -> new NotaDebitoRequest.Item(d.getProductoId(), d.getId(), d.getCantidad(),
                            d.getPrecioUnitario(), d.getDescuento(), true))
                    .toList();
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Debe indicar al menos una línea a revertir");
        }

        aplicarLineasYMontos(nd, items, request.descuento(), nc, tenantId);
    }

    private void aplicarLineasYMontos(NotaDebito nd, List<NotaDebitoRequest.Item> items,
                                      BigDecimal descuentoGlobalRequest, NotaCredito nc, Long tenantId) {
        Map<Long, NotaCreditoDetalle> lineasOriginales = nc.getDetalle().stream()
                .collect(Collectors.toMap(NotaCreditoDetalle::getId, d -> d));
        Map<Long, Producto> productos = productosDe(items.stream()
                .map(NotaDebitoRequest.Item::productoId).toList());

        List<NotaDebitoDetalle> lineas = new ArrayList<>();
        BigDecimal subtotalBrutoTotal = BigDecimal.ZERO;
        BigDecimal descuentoLineas = BigDecimal.ZERO;
        BigDecimal sumaDetalle = BigDecimal.ZERO;

        for (NotaDebitoRequest.Item item : items) {
            Producto producto = productos.get(item.productoId());
            if (producto == null) {
                throw new IllegalArgumentException("Producto no encontrado: " + item.productoId());
            }
            if (item.cantidad() == null || item.cantidad().signum() <= 0) {
                throw new IllegalArgumentException(
                        "La cantidad de " + producto.getNombre() + " debe ser mayor que 0");
            }
            if (item.precioUnitario() == null || item.precioUnitario().signum() < 0) {
                throw new IllegalArgumentException(
                        "El precio de " + producto.getNombre() + " no puede ser negativo");
            }

            NotaCreditoDetalle original = null;
            if (item.notaCreditoDetalleId() != null) {
                original = lineasOriginales.get(item.notaCreditoDetalleId());
                if (original == null) {
                    throw new IllegalArgumentException(
                            "La línea de " + producto.getNombre() + " no pertenece a la nota de crédito asociada");
                }
            } else if (item.revierteInventario()) {
                throw new IllegalArgumentException(
                        "La línea \"" + producto.getNombre() + "\" no pertenece a la nota de crédito y no puede "
                                + "revertir inventario");
            }

            BigDecimal subtotalBruto = item.precioUnitario().multiply(item.cantidad());
            BigDecimal descuentoLinea = item.descuento() != null ? item.descuento() : BigDecimal.ZERO;
            if (descuentoLinea.signum() < 0) {
                throw new IllegalArgumentException(
                        "El descuento de " + producto.getNombre() + " no puede ser negativo");
            }
            if (descuentoLinea.compareTo(subtotalBruto) > 0) {
                throw new IllegalArgumentException(
                        "El descuento de " + producto.getNombre() + " no puede superar el subtotal de la línea");
            }

            BigDecimal subtotal = subtotalBruto.subtract(descuentoLinea);
            subtotalBrutoTotal = subtotalBrutoTotal.add(subtotalBruto);
            descuentoLineas = descuentoLineas.add(descuentoLinea);
            sumaDetalle = sumaDetalle.add(subtotal);

            lineas.add(NotaDebitoDetalle.builder()
                    .notaDebito(nd)
                    .productoId(producto.getId())
                    .notaCreditoDetalleId(original != null ? original.getId() : null)
                    .codigo(producto.getSku())
                    .descripcion(producto.getNombre())
                    .cantidad(item.cantidad())
                    .precioUnitario(item.precioUnitario())
                    .descuento(descuentoLinea)
                    .subtotal(subtotal)
                    .revierteInventario(item.revierteInventario())
                    .build());
        }

        BigDecimal descuentoGlobal = descuentoGlobalRequest != null ? descuentoGlobalRequest : BigDecimal.ZERO;
        if (descuentoGlobal.signum() < 0) {
            throw new IllegalArgumentException("El descuento global no puede ser negativo");
        }
        if (descuentoGlobal.compareTo(sumaDetalle) > 0) {
            throw new IllegalArgumentException("El descuento global no puede superar el total de las líneas");
        }

        nd.getDetalle().addAll(lineas);

        // Se calcula con el tipo de documento y la condición de exención de la
        // VENTA original (heredados por la NC). Así una ND sobre una NC de boleta
        // (montos brutos) cuadra con la boleta, y una sobre factura (montos
        // netos) con la factura.
        CalculadoraMontosVenta.Montos montos = CalculadoraMontosVenta.calcular(
                nd.getNcDocAsociadoTipo(), nd.isExenta(), sumaDetalle.subtract(descuentoGlobal));
        nd.setDescuento(descuentoGlobal);
        nd.setMontoSubtotal(subtotalBrutoTotal);
        nd.setMontoDescuento(descuentoLineas.add(descuentoGlobal));
        nd.setMontoNeto(montos.neto());
        nd.setMontoIva(montos.iva());
        nd.setMontoTotal(montos.total());
    }

    private void aplicarMontosEnCero(NotaDebito nd) {
        nd.setDescuento(BigDecimal.ZERO);
        nd.setMontoSubtotal(BigDecimal.ZERO);
        nd.setMontoDescuento(BigDecimal.ZERO);
        nd.setMontoNeto(BigDecimal.ZERO);
        nd.setMontoIva(BigDecimal.ZERO);
        nd.setMontoTotal(BigDecimal.ZERO);
    }

    // --- Inventario ----------------------------------------------------------

    // Impide revertir dos veces la misma mercadería: lo disponible de cada línea
    // es lo que la NC recuperó menos lo ya revertido por notas de débito
    // EMITIDAS. Se valida todo antes de escribir nada.
    private void validarCantidadesDisponibles(NotaDebito nd, List<NotaDebitoDetalle> aRevertir, Long tenantId) {
        NotaCredito nc = buscarNotaCredito(nd.getNotaCreditoId(), tenantId);
        Map<Long, NotaCreditoDetalle> lineasOriginales = nc.getDetalle().stream()
                .collect(Collectors.toMap(NotaCreditoDetalle::getId, d -> d));
        Map<Long, BigDecimal> revertidas = cantidadesRevertidas(tenantId, nc.getId(), nd.getId());

        // Varias líneas de la nota pueden apuntar a la misma línea de la NC: se
        // acumulan para que el control no se pueda burlar dividiéndolas.
        Map<Long, BigDecimal> solicitadas = new LinkedHashMap<>();
        for (NotaDebitoDetalle linea : aRevertir) {
            if (linea.getNotaCreditoDetalleId() == null) {
                throw new IllegalArgumentException(
                        "La línea \"" + linea.getDescripcion() + "\" no pertenece a la nota de crédito y no puede "
                                + "revertir inventario");
            }
            solicitadas.merge(linea.getNotaCreditoDetalleId(), linea.getCantidad(), BigDecimal::add);
        }

        for (var entrada : solicitadas.entrySet()) {
            NotaCreditoDetalle original = lineasOriginales.get(entrada.getKey());
            if (original == null) {
                throw new IllegalArgumentException("Una de las líneas no pertenece a la nota de crédito asociada");
            }
            BigDecimal yaRevertido = revertidas.getOrDefault(entrada.getKey(), BigDecimal.ZERO);
            BigDecimal disponible = original.getCantidad().subtract(yaRevertido).max(BigDecimal.ZERO);
            if (entrada.getValue().compareTo(disponible) > 0) {
                String nombre = productoRepository.findById(original.getProductoId())
                        .map(Producto::getNombre)
                        .orElse("el producto");
                throw new IllegalArgumentException(
                        "No se puede revertir " + entrada.getValue().stripTrailingZeros().toPlainString()
                                + " de \"" + nombre + "\": la nota de crédito solo tiene "
                                + disponible.stripTrailingZeros().toPlainString() + " disponible(s) para revertir");
            }
        }
    }

    // El monto a cobrar no puede superar lo que la NC aún tiene disponible. Se
    // descuenta lo ya revertido por OTRAS notas de débito EMITIDAS (la propia,
    // todavía en BORRADOR, no cuenta).
    private void validarMontoDisponible(NotaDebito nd, Long tenantId) {
        NotaCredito nc = buscarNotaCredito(nd.getNotaCreditoId(), tenantId);
        BigDecimal revertido = montoRevertido(tenantId, nc.getId(), nd.getId());
        BigDecimal disponible = nc.getMontoTotal().subtract(revertido).max(BigDecimal.ZERO);
        if (nd.getMontoTotal().compareTo(disponible) > 0) {
            throw new IllegalArgumentException(
                    "El monto de la nota de débito (" + nd.getMontoTotal().stripTrailingZeros().toPlainString()
                            + ") supera el disponible de la nota de crédito ("
                            + disponible.stripTrailingZeros().toPlainString() + ")");
        }
    }

    private Map<Long, BigDecimal> cantidadesRevertidas(Long tenantId, Long notaCreditoId, Long excluyendoNotaDebitoId) {
        List<CantidadRevertida> filas = excluyendoNotaDebitoId == null
                ? detalleRepository.cantidadesRevertidas(tenantId, notaCreditoId)
                : detalleRepository.cantidadesRevertidasExcluyendo(tenantId, notaCreditoId, excluyendoNotaDebitoId);
        Map<Long, BigDecimal> mapa = new HashMap<>();
        for (CantidadRevertida fila : filas) {
            mapa.put(fila.notaCreditoDetalleId(), fila.cantidad() != null ? fila.cantidad() : BigDecimal.ZERO);
        }
        return mapa;
    }

    private BigDecimal montoRevertido(Long tenantId, Long notaCreditoId, Long excluyendoNotaDebitoId) {
        return notaDebitoRepository.findByTenantIdAndNotaCreditoIdOrderByFolioAsc(tenantId, notaCreditoId).stream()
                .filter(nd -> nd.getEstado() == EstadoNotaDebito.EMITIDA)
                .filter(nd -> !nd.getId().equals(excluyendoNotaDebitoId))
                .map(NotaDebito::getMontoTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Suma por nota de crédito de lo ya revertido por NDs EMITIDAS, para todas
    // las NCs candidatas a asociarse (una sola consulta, sin N+1).
    private Map<Long, BigDecimal> montosRevertidos(Long tenantId, List<NotaCredito> notas) {
        if (notas.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = notas.stream().map(NotaCredito::getId).toList();
        Map<Long, BigDecimal> revertidos = new HashMap<>();
        for (NotaDebito nd : notaDebitoRepository
                .findByTenantIdAndNotaCreditoIdInOrderByFolioAsc(tenantId, ids)) {
            if (nd.getEstado() == EstadoNotaDebito.EMITIDA) {
                revertidos.merge(nd.getNotaCreditoId(), nd.getMontoTotal(), BigDecimal::add);
            }
        }
        return revertidos;
    }

    // --- Helpers -------------------------------------------------------------

    // Cabecera que agrupa los movimientos de una emisión o de su anulación. El
    // módulo de Inventario la usa como "documento" del movimiento: de ahí salen
    // su pantalla de detalle y sus exportaciones.
    private Long crearHeader(NotaDebito nd, Long usuarioId, TipoMovimiento tipo, String observacion) {
        MovimientoInventarioHeader header = movimientoHeaderRepository.save(MovimientoInventarioHeader.builder()
                .tenantId(nd.getTenantId())
                .tipo(tipo)
                .bodegaDestinoId(tipo == TipoMovimiento.ENTRADA ? nd.getBodegaId() : null)
                .bodegaOrigenId(tipo == TipoMovimiento.SALIDA ? nd.getBodegaId() : null)
                .usuarioId(usuarioId)
                .observacion(observacion)
                .build());
        return header.getId();
    }

    // La mercadería se revierte contra la bodega del documento. La bodega viene
    // heredada de la NC; si ni la NC la trae, se cae a la bodega principal del
    // tenant (mismo criterio que VentaService, CompraService y NotaCreditoService).
    private Long bodegaDe(NotaCredito nc, Long tenantId) {
        if (nc.getBodegaId() != null) {
            return nc.getBodegaId();
        }
        try {
            return stockService.bodegaPrincipal(tenantId).getId();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private NotaDebito buscarEntidad(Long id, Long tenantId) {
        return notaDebitoRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Nota de débito no encontrada: " + id));
    }

    private NotaCredito buscarNotaCredito(Long notaCreditoId, Long tenantId) {
        NotaCredito nc = notaCreditoRepository.findByIdAndTenantId(notaCreditoId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Nota de crédito no encontrada: " + notaCreditoId));
        if (nc.getEstado() != EstadoNotaCredito.EMITIDA) {
            throw new IllegalArgumentException(
                    "Solo se pueden revertir notas de crédito EMITIDAS (la " + numeroNotaCredito(nc)
                            + " está en estado " + nc.getEstado() + ")");
        }
        return nc;
    }

    private void exigirEstado(NotaDebito nd, String accion, EstadoNotaDebito... permitidos) {
        for (EstadoNotaDebito permitido : permitidos) {
            if (nd.getEstado() == permitido) {
                return;
            }
        }
        throw new IllegalArgumentException(
                "No se puede " + accion + " una nota de débito en estado " + nd.getEstado());
    }

    private void registrarEvento(NotaDebito nd, AccionNotaDebito accion, EstadoNotaDebito anterior,
                                 EstadoNotaDebito nuevo, String detalle, Long usuarioId) {
        eventoRepository.save(NotaDebitoEvento.builder()
                .tenantId(nd.getTenantId())
                .notaDebitoId(nd.getId())
                .usuarioId(usuarioId)
                .accion(accion)
                .estadoAnterior(anterior)
                .estadoNuevo(nuevo)
                .detalle(detalle)
                .build());
    }

    private Map<Long, Producto> productosDe(List<Long> productoIds) {
        if (productoIds.isEmpty()) {
            return Map.of();
        }
        return productoRepository.findAllById(productoIds).stream()
                .collect(Collectors.toMap(Producto::getId, p -> p, (a, b) -> a));
    }

    private Map<Long, Cliente> clientesDe(Long tenantId, List<NotaDebito> notas) {
        List<Long> ids = notas.stream().map(NotaDebito::getClienteId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return clienteRepository.findByTenantIdAndIdIn(tenantId, ids).stream()
                .collect(Collectors.toMap(Cliente::getId, c -> c, (a, b) -> a));
    }

    private Map<Long, Cliente> clientesDeNotasCredito(Long tenantId, List<NotaCredito> notas) {
        List<Long> ids = notas.stream().map(NotaCredito::getClienteId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return clienteRepository.findByTenantIdAndIdIn(tenantId, ids).stream()
                .collect(Collectors.toMap(Cliente::getId, c -> c, (a, b) -> a));
    }

    private BigDecimal montoDe(List<NotaDebito> notas) {
        return notas.stream().map(NotaDebito::getMontoTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // El texto puede ser el número de la nota (ND-000012, "12"), el folio de la
    // NC asociada, o el nombre/RUT del cliente. Los clientes se resuelven a ids
    // en una consulta aparte para que el filtro siga corriendo en la base y la
    // paginación no tenga que traer todo a memoria.
    private Specification<NotaDebito> filtroBusqueda(Long tenantId, String q) {
        List<Long> clienteIds = clienteRepository.idsPorBusqueda(tenantId, "%" + q.toLowerCase() + "%");
        Integer folio = folioDeTexto(q);

        return (root, query, cb) -> {
            List<Predicate> alternativas = new ArrayList<>();
            if (!clienteIds.isEmpty()) {
                alternativas.add(root.get("clienteId").in(clienteIds));
            }
            if (folio != null) {
                alternativas.add(cb.equal(root.get("folio"), folio));
                alternativas.add(cb.equal(root.get("ncFolio"), folio));
            }
            return alternativas.isEmpty()
                    ? cb.disjunction()
                    : cb.or(alternativas.toArray(new Predicate[0]));
        };
    }

    private Specification<NotaCredito> filtroAsociables(Long tenantId, String q) {
        List<Long> clienteIds = clienteRepository.idsPorBusqueda(tenantId, "%" + q.toLowerCase() + "%");
        Integer folio = folioDeTexto(q);

        return (root, query, cb) -> {
            List<Predicate> alternativas = new ArrayList<>();
            if (!clienteIds.isEmpty()) {
                alternativas.add(root.get("clienteId").in(clienteIds));
            }
            if (folio != null) {
                alternativas.add(cb.equal(root.get("folio"), folio));
            }
            return alternativas.isEmpty()
                    ? cb.disjunction()
                    : cb.or(alternativas.toArray(new Predicate[0]));
        };
    }

    private Integer folioDeTexto(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String digitos = q.replaceAll("[^0-9]", "");
        if (digitos.isEmpty() || digitos.length() > 9) {
            return null;
        }
        return Integer.valueOf(digitos);
    }

    private Sort ordenamiento(String orden, String direccion) {
        // Set.of(...).contains(null) lanza NullPointerException, y "sort" es un
        // parámetro opcional del listado: hay que descartar el nulo antes.
        String campo = orden != null && CAMPOS_ORDEN.contains(orden) ? orden : "fecha";
        Sort.Direction dir = "asc".equalsIgnoreCase(direccion) ? Sort.Direction.ASC : Sort.Direction.DESC;
        // Desempate por folio: dos notas del mismo día deben salir siempre en el
        // mismo orden entre páginas.
        return Sort.by(dir, campo).and(Sort.by(dir, "folio"));
    }

    private ImpactoInventario impactoDe(NotaDebito nd) {
        List<NotaDebitoDetalle> lineas = nd.getDetalle();
        if (lineas.isEmpty()) {
            return ImpactoInventario.NO;
        }
        long conReversion = lineas.stream().filter(NotaDebitoDetalle::isRevierteInventario).count();
        if (conReversion == 0) {
            return ImpactoInventario.NO;
        }
        return conReversion == lineas.size() ? ImpactoInventario.TOTAL : ImpactoInventario.PARCIAL;
    }

    private String etiquetaTipo(TipoReversion tipo) {
        return switch (tipo) {
            case REVIERTE_DOCUMENTO -> "Revierte documento";
            case REVIERTE_MONTO -> "Revierte monto";
            case REVIERTE_TEXTO -> "Revierte texto";
        };
    }

    private String numeroNotaCredito(NotaCredito nc) {
        return NumeroNotaCredito.formatear(nc.getFolio());
    }

    // --- Armado de la respuesta ----------------------------------------------

    private NotaDebitoCompleta detalleDe(NotaDebito nd) {
        Long tenantId = nd.getTenantId();
        Cliente cliente = clienteRepository.findById(nd.getClienteId()).orElse(null);
        Usuario usuario = usuarioRepository.findById(nd.getUsuarioId()).orElse(null);
        String bodegaNombre = nd.getBodegaId() == null ? null
                : bodegaRepository.findById(nd.getBodegaId()).map(Bodega::getNombre).orElse(null);
        BigDecimal montoDisponible = nd.getMontoTotal() == null ? null
                : montoDisponibleDe(tenantId, nd);

        List<LineaNotaDebito> lineas = nd.getDetalle().stream()
                .map(l -> new LineaNotaDebito(l.getId(), l.getProductoId(), l.getNotaCreditoDetalleId(),
                        l.getCodigo(), l.getDescripcion(), l.getCantidad(), l.getPrecioUnitario(),
                        l.getDescuento(), l.getSubtotal(), l.isRevierteInventario()))
                .toList();

        List<NotaDebitoEvento> eventos = eventoRepository
                .findByTenantIdAndNotaDebitoIdOrderByFechaAscIdAsc(tenantId, nd.getId());
        Map<Long, String> nombresUsuario = nombresDeUsuarios(eventos);
        List<EventoNotaDebito> historial = eventos.stream()
                .map(e -> new EventoNotaDebito(e.getFecha(),
                        e.getUsuarioId() == null ? null : nombresUsuario.get(e.getUsuarioId()),
                        e.getAccion(), e.getEstadoAnterior(), e.getEstadoNuevo(), e.getDetalle()))
                .toList();

        return new NotaDebitoCompleta(
                nd.getId(), nd.getFolio(), NumeroNotaDebito.formatear(nd.getFolio()), nd.getEstado(),
                nd.getTipoReversion(), nd.getFecha(),
                nd.getClienteId(),
                cliente != null ? cliente.getNombre() : null,
                cliente != null ? cliente.getRazonSocial() : null,
                cliente != null ? cliente.getRut() : null,
                cliente != null ? cliente.getDireccion() : null,
                cliente != null ? cliente.getEmail() : null,
                cliente != null ? cliente.getTelefono() : null,
                nd.getUsuarioId(), usuario != null ? usuario.getNombre() : null,
                new DocumentoAsociado(nd.getNotaCreditoId(), NumeroNotaCredito.formatear(nd.getNcFolio()),
                        nd.getNcFecha(), nd.getNcFolio(), nd.getNcDocAsociadoTipo(),
                        nd.getNcMontoTotal(), montoDisponible, nd.getNcRazon()),
                nd.getMotivo(), nd.getObservaciones(), nd.getTextoCorreccion(),
                nd.getBodegaId(), bodegaNombre, nd.isExenta(), nd.getMoneda(),
                nd.getDescuento(), nd.getMontoSubtotal(), nd.getMontoDescuento(),
                nd.getMontoNeto(), nd.getMontoIva(), nd.getMontoTotal(),
                nd.getFechaEmision(), nd.getFechaAnulacion(),
                impactoDe(nd), lineas, movimientosDe(nd), historial);
    }

    // Disponible de la NC asociada vista desde esta ND: si la ND está EMITIDA su
    // propio monto ya cuenta como revertido; se excluye para que el número que
    // ve el usuario sea "lo que resta de la NC" en todo caso.
    private BigDecimal montoDisponibleDe(Long tenantId, NotaDebito nd) {
        BigDecimal revertido = montoRevertido(tenantId, nd.getNotaCreditoId(), nd.getId());
        BigDecimal base = nd.getNcMontoTotal() != null ? nd.getNcMontoTotal() : BigDecimal.ZERO;
        return base.subtract(revertido).max(BigDecimal.ZERO);
    }

    // Movimientos de inventario generados por la nota: la respuesta a "consultar
    // movimientos de inventario relacionados". Productos y bodegas se resuelven
    // en lote para no caer en N+1.
    private List<MovimientoRelacionado> movimientosDe(NotaDebito nd) {
        List<NotaDebitoMovimiento> vinculos = movimientoRepository
                .findByTenantIdAndNotaDebitoIdOrderByFechaAscIdAsc(nd.getTenantId(), nd.getId());
        if (vinculos.isEmpty()) {
            return List.of();
        }

        Map<Long, MovimientoInventario> movimientos = movimientoInventarioRepository
                .findAllById(vinculos.stream().map(NotaDebitoMovimiento::getMovimientoInventarioId).toList())
                .stream()
                .collect(Collectors.toMap(MovimientoInventario::getId, m -> m, (a, b) -> a));

        Map<Long, Producto> productos = productosDe(movimientos.values().stream()
                .map(MovimientoInventario::getProductoId).distinct().toList());
        Map<Long, String> bodegas = bodegaRepository
                .findAllById(movimientos.values().stream()
                        .map(MovimientoInventario::getBodegaId)
                        .filter(java.util.Objects::nonNull)
                        .distinct().toList())
                .stream()
                .collect(Collectors.toMap(Bodega::getId, Bodega::getNombre, (a, b) -> a));

        return vinculos.stream()
                .map(v -> {
                    MovimientoInventario mov = movimientos.get(v.getMovimientoInventarioId());
                    if (mov == null) {
                        return null;
                    }
                    Producto p = productos.get(mov.getProductoId());
                    return new MovimientoRelacionado(
                            mov.getId(), mov.getHeaderId(), v.getTipo(), mov.getFecha(), mov.getProductoId(),
                            p != null ? p.getNombre() : "Producto " + mov.getProductoId(),
                            mov.getCantidad(), mov.getBodegaId(),
                            mov.getBodegaId() == null ? null : bodegas.get(mov.getBodegaId()));
                })
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(MovimientoRelacionado::fecha))
                .toList();
    }

    private Map<Long, String> nombresDeUsuarios(List<NotaDebitoEvento> eventos) {
        List<Long> ids = eventos.stream()
                .map(NotaDebitoEvento::getUsuarioId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return usuarioRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Usuario::getId, Usuario::getNombre, (a, b) -> a));
    }

    private NotaDebitoResumen resumenDe(NotaDebito nd, Cliente cliente) {
        return new NotaDebitoResumen(
                nd.getId(), nd.getFolio(), NumeroNotaDebito.formatear(nd.getFolio()), nd.getEstado(),
                nd.getTipoReversion(), nd.getFecha(),
                nd.getClienteId(),
                cliente != null ? cliente.getNombre() : null,
                cliente != null ? cliente.getRut() : null,
                NumeroNotaCredito.formatear(nd.getNcFolio()), nd.getNcFolio(), nd.getNcDocAsociadoTipo(),
                nd.getMotivo(), nd.getMontoTotal(), impactoDe(nd));
    }
}