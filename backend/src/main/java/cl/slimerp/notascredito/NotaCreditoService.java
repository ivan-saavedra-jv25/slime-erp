package cl.slimerp.notascredito;

import cl.slimerp.catalogo.Cliente;
import cl.slimerp.catalogo.ClienteRepository;
import cl.slimerp.catalogo.Producto;
import cl.slimerp.catalogo.ProductoRepository;
import cl.slimerp.common.PaginaResponse;
import cl.slimerp.common.UsuarioActualService;
import cl.slimerp.config.TenantContext;
import cl.slimerp.inventario.Bodega;
import cl.slimerp.inventario.BodegaRepository;
import cl.slimerp.inventario.MovimientoInventario;
import cl.slimerp.inventario.MovimientoInventarioHeader;
import cl.slimerp.inventario.MovimientoInventarioHeaderRepository;
import cl.slimerp.inventario.MovimientoInventarioRepository;
import cl.slimerp.inventario.StockService;
import cl.slimerp.inventario.TipoMovimiento;
import cl.slimerp.tenant.Usuario;
import cl.slimerp.tenant.UsuarioRepository;
import cl.slimerp.ventas.CalculadoraMontosVenta;
import cl.slimerp.ventas.TipoDocumentoVenta;
import cl.slimerp.ventas.Venta;
import cl.slimerp.ventas.VentaDetalle;
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

// Nota de Crédito: el documento que corrige hacia atrás la cadena comercial.
// Siempre se asocia a una VENTA (Boleta/Factura/Voucher), que es el único
// documento del ERP que descuenta stock y por lo tanto el único donde la
// recuperación de inventario tiene sentido.
//
// Alcance deliberado: sin XML, DTE ni envío al SII, y sin efectos sobre cuentas
// por cobrar ni tesorería. Emitir aplica los efectos sobre inventario; anular los
// revierte. Cada cambio queda asentado en nota_credito_evento (append-only) y el
// vínculo con cada movimiento de inventario, en nota_credito_movimiento.
@Service
public class NotaCreditoService {

    private static final Set<String> CAMPOS_ORDEN = Set.of("fecha", "folio", "montoTotal");
    private static final int MAX_DOCUMENTOS_ASOCIABLES = 20;

    private final NotaCreditoRepository notaCreditoRepository;
    private final NotaCreditoDetalleRepository detalleRepository;
    private final NotaCreditoEventoRepository eventoRepository;
    private final NotaCreditoMovimientoRepository movimientoRepository;
    private final NotaCreditoFolioService folioService;
    private final VentaRepository ventaRepository;
    private final ClienteRepository clienteRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final BodegaRepository bodegaRepository;
    private final StockService stockService;
    private final MovimientoInventarioRepository movimientoInventarioRepository;
    private final MovimientoInventarioHeaderRepository movimientoHeaderRepository;
    private final UsuarioActualService usuarioActualService;

    public NotaCreditoService(NotaCreditoRepository notaCreditoRepository,
                              NotaCreditoDetalleRepository detalleRepository,
                              NotaCreditoEventoRepository eventoRepository,
                              NotaCreditoMovimientoRepository movimientoRepository,
                              NotaCreditoFolioService folioService,
                              VentaRepository ventaRepository,
                              ClienteRepository clienteRepository,
                              ProductoRepository productoRepository,
                              UsuarioRepository usuarioRepository,
                              BodegaRepository bodegaRepository,
                              StockService stockService,
                              MovimientoInventarioRepository movimientoInventarioRepository,
                              MovimientoInventarioHeaderRepository movimientoHeaderRepository,
                              UsuarioActualService usuarioActualService) {
        this.notaCreditoRepository = notaCreditoRepository;
        this.detalleRepository = detalleRepository;
        this.eventoRepository = eventoRepository;
        this.movimientoRepository = movimientoRepository;
        this.folioService = folioService;
        this.ventaRepository = ventaRepository;
        this.clienteRepository = clienteRepository;
        this.productoRepository = productoRepository;
        this.usuarioRepository = usuarioRepository;
        this.bodegaRepository = bodegaRepository;
        this.stockService = stockService;
        this.movimientoInventarioRepository = movimientoInventarioRepository;
        this.movimientoHeaderRepository = movimientoHeaderRepository;
        this.usuarioActualService = usuarioActualService;
    }

    // --- DTOs de respuesta ---------------------------------------------------

    public record LineaNotaCredito(
            Long id, Long productoId, Long ventaDetalleId, String codigo, String descripcion,
            BigDecimal cantidad, BigDecimal precioUnitario, BigDecimal descuento, BigDecimal subtotal,
            boolean recuperaInventario) {
    }

    public record EventoNotaCredito(
            LocalDateTime fecha, String usuario, AccionNotaCredito accion,
            EstadoNotaCredito estadoAnterior, EstadoNotaCredito estadoNuevo, String detalle) {
    }

    public record DocumentoAsociado(
            Long ventaId, TipoDocumentoVenta tipo, Integer folio, String numero,
            LocalDate fecha, String razon, BigDecimal montoTotal) {
    }

    public record DocumentoAsociable(
            Long ventaId, TipoDocumentoVenta tipoDocumento, Integer folio, String numero,
            LocalDate fecha, BigDecimal montoTotal, boolean exento, Long bodegaId,
            boolean tieneNotasCredito) {
    }

    public record LineaDocumentoOriginal(
            Long ventaDetalleId, Long productoId, String codigo, String descripcion,
            BigDecimal cantidad, BigDecimal cantidadRecuperada, BigDecimal cantidadDisponible,
            BigDecimal precioUnitario, BigDecimal descuento, BigDecimal subtotal) {
    }

    // headerId apunta a la cabecera de movimiento_inventario: es lo que permite
    // abrir el detalle y las exportaciones del módulo de Inventario desde la
    // trazabilidad de la nota de crédito.
    public record MovimientoRelacionado(
            Long movimientoInventarioId, Long headerId, TipoMovimientoNotaCredito tipo, LocalDateTime fecha,
            Long productoId, String producto, BigDecimal cantidad, Long bodegaId, String bodega) {
    }

    public record NotaCreditoResumen(
            Long id, Integer folio, String numero, EstadoNotaCredito estado,
            TipoCorreccion tipoCorreccion, LocalDate fecha,
            Long clienteId, String clienteNombre, String clienteRut,
            TipoDocumentoVenta docAsociadoTipo, Integer docAsociadoFolio,
            String motivo, BigDecimal montoTotal, RecuperacionInventario recuperacionInventario) {
    }

    public record NotaCreditoCompleta(
            Long id, Integer folio, String numero, EstadoNotaCredito estado,
            TipoCorreccion tipoCorreccion, LocalDate fecha,
            Long clienteId, String clienteNombre, String clienteRazonSocial, String clienteRut,
            String clienteDireccion, String clienteEmail, String clienteTelefono,
            Long usuarioId, String usuarioNombre,
            DocumentoAsociado documentoAsociado,
            String motivo, String observaciones, String textoCorreccion,
            Long bodegaId, String bodegaNombre, boolean exenta, String moneda,
            BigDecimal descuento, BigDecimal montoSubtotal, BigDecimal montoDescuento,
            BigDecimal montoNeto, BigDecimal montoIva, BigDecimal montoTotal,
            LocalDateTime fechaEmision, LocalDateTime fechaAnulacion,
            RecuperacionInventario recuperacionInventario,
            List<LineaNotaCredito> lineas,
            List<MovimientoRelacionado> movimientosInventario,
            List<EventoNotaCredito> historial) {
    }

    public record ConteoPorEstado(EstadoNotaCredito estado, int cantidad, BigDecimal monto) {
    }

    public record ConteoPorTipo(TipoCorreccion tipoCorreccion, int cantidad, BigDecimal monto) {
    }

    public record DashboardNotasCredito(
            LocalDate desde, LocalDate hasta,
            int cantidad, int borradores, int emitidas, int anuladas,
            BigDecimal montoTotalEmitido, int conRecuperacionInventario, int documentosCorregidos,
            List<ConteoPorEstado> porEstado, List<ConteoPorTipo> porTipoCorreccion) {
    }

    // --- Escritura -----------------------------------------------------------

    @Transactional
    public NotaCreditoCompleta crear(NotaCreditoRequest request) {
        Long tenantId = TenantContext.getTenantId();
        Long usuarioId = usuarioActualService.idUsuarioActual(tenantId);

        Venta venta = buscarVenta(request.ventaId(), tenantId);
        Cliente cliente = clienteRepository.findById(venta.getClienteId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "El documento asociado apunta a un cliente inexistente"));

        NotaCredito nc = NotaCredito.builder()
                .tenantId(tenantId)
                .folio(folioService.siguienteFolio(tenantId))
                // Cliente, bodega y base impositiva se heredan del documento
                // asociado: la nota de crédito corrige esa operación, no otra.
                .clienteId(cliente.getId())
                .usuarioId(usuarioId)
                .estado(EstadoNotaCredito.BORRADOR)
                .tipoCorreccion(request.tipoCorreccion())
                .fecha(request.fecha())
                .motivo(request.motivo())
                .observaciones(request.observaciones())
                .bodegaId(bodegaDe(venta, tenantId))
                .exenta(venta.isExento())
                .moneda("CLP")
                .ventaId(venta.getId())
                .docAsociadoTipo(venta.getTipoDocumento())
                .docAsociadoFolio(venta.getFolio())
                .docAsociadoFecha(venta.getFecha() != null ? venta.getFecha().toLocalDate() : null)
                .docAsociadoRazon(request.docAsociadoRazon())
                .build();

        aplicarCorreccion(nc, request, venta, tenantId, null);
        nc = notaCreditoRepository.save(nc);

        registrarEvento(nc, AccionNotaCredito.CREADA, null, EstadoNotaCredito.BORRADOR,
                etiquetaTipo(nc.getTipoCorreccion()) + " sobre " + descripcionDocumento(venta), usuarioId);
        return detalleDe(nc);
    }

    @Transactional
    public NotaCreditoCompleta actualizar(Long id, NotaCreditoRequest request) {
        Long tenantId = TenantContext.getTenantId();
        Long usuarioId = usuarioActualService.idUsuarioActual(tenantId);
        NotaCredito nc = buscarEntidad(id, tenantId);
        exigirEstado(nc, "editar", EstadoNotaCredito.BORRADOR);

        // El documento asociado no se cambia: corregir otro documento es otra
        // nota de crédito, no una edición de esta.
        if (!nc.getVentaId().equals(request.ventaId())) {
            throw new IllegalArgumentException(
                    "No se puede cambiar el documento asociado de una nota de crédito ya creada");
        }
        Venta venta = buscarVenta(nc.getVentaId(), tenantId);

        TipoCorreccion tipoAnterior = nc.getTipoCorreccion();
        nc.setTipoCorreccion(request.tipoCorreccion());
        nc.setFecha(request.fecha());
        nc.setMotivo(request.motivo());
        nc.setObservaciones(request.observaciones());
        nc.setDocAsociadoRazon(request.docAsociadoRazon());
        nc.setFechaActualizacion(LocalDateTime.now());

        nc.getDetalle().clear();
        aplicarCorreccion(nc, request, venta, tenantId, nc.getId());
        nc = notaCreditoRepository.save(nc);

        String detalle = tipoAnterior != nc.getTipoCorreccion()
                ? "Tipo de corrección: " + etiquetaTipo(tipoAnterior) + " → " + etiquetaTipo(nc.getTipoCorreccion())
                : null;
        registrarEvento(nc, AccionNotaCredito.EDITADA, EstadoNotaCredito.BORRADOR, EstadoNotaCredito.BORRADOR,
                detalle, usuarioId);
        return detalleDe(nc);
    }

    @Transactional
    public void eliminar(Long id) {
        Long tenantId = TenantContext.getTenantId();
        NotaCredito nc = buscarEntidad(id, tenantId);
        exigirEstado(nc, "eliminar", EstadoNotaCredito.BORRADOR);
        // El folio ya consumido no se reutiliza: queda un hueco en la numeración,
        // igual que en cotizaciones y notas de venta.
        notaCreditoRepository.delete(nc);
    }

    // Emitir es el núcleo del módulo: bloquea las modificaciones y aplica los
    // efectos sobre inventario. Todas las líneas se validan antes de escribir la
    // primera entrada, para que un fallo a mitad de camino dé un mensaje claro y
    // no dependa solo del rollback.
    @Transactional
    public NotaCreditoCompleta emitir(Long id) {
        Long tenantId = TenantContext.getTenantId();
        Long usuarioId = usuarioActualService.idUsuarioActual(tenantId);
        NotaCredito nc = buscarEntidad(id, tenantId);
        exigirEstado(nc, "emitir", EstadoNotaCredito.BORRADOR);

        List<NotaCreditoDetalle> aRecuperar = nc.getTipoCorreccion() == TipoCorreccion.CORRIGE_TEXTO
                ? List.of()
                : nc.getDetalle().stream().filter(NotaCreditoDetalle::isRecuperaInventario).toList();

        if (!aRecuperar.isEmpty()) {
            if (nc.getBodegaId() == null) {
                throw new IllegalArgumentException(
                        "No se puede recuperar inventario: el documento asociado no tiene bodega y el tenant no "
                                + "tiene una bodega principal configurada");
            }
            validarCantidadesDisponibles(nc, aRecuperar, tenantId);
        }

        // Las entradas cuelgan de una cabecera de movimiento para que la
        // recuperación aparezca en el historial de Inventario y reutilice su
        // pantalla de detalle y sus exportaciones a PDF y Excel.
        Long headerId = aRecuperar.isEmpty() ? null : crearHeader(nc, usuarioId, TipoMovimiento.ENTRADA,
                "Recuperación de inventario por " + NumeroNotaCredito.formatear(nc.getFolio()));

        for (NotaCreditoDetalle linea : aRecuperar) {
            MovimientoInventario mov = stockService.sumar(tenantId, linea.getProductoId(), nc.getBodegaId(),
                    linea.getCantidad(), TipoMovimiento.ENTRADA_NOTA_CREDITO, headerId, nc.getId());
            movimientoRepository.save(NotaCreditoMovimiento.builder()
                    .tenantId(tenantId)
                    .notaCreditoId(nc.getId())
                    .notaCreditoDetalleId(linea.getId())
                    .movimientoInventarioId(mov.getId())
                    .tipo(TipoMovimientoNotaCredito.RECUPERACION)
                    .build());
        }

        EstadoNotaCredito anterior = nc.getEstado();
        nc.setEstado(EstadoNotaCredito.EMITIDA);
        nc.setFechaEmision(LocalDateTime.now());
        nc.setFechaActualizacion(LocalDateTime.now());
        nc = notaCreditoRepository.save(nc);

        String detalle = nc.getDetalle().size() + " línea(s), " + aRecuperar.size()
                + " con recuperación de inventario";
        registrarEvento(nc, AccionNotaCredito.EMITIDA, anterior, EstadoNotaCredito.EMITIDA, detalle, usuarioId);
        return detalleDe(nc);
    }

    // Anular revierte los efectos sobre inventario. Las filas de RECUPERACION no
    // se borran: se agrega una fila REVERSA_ANULACION apuntando al movimiento
    // inverso, porque el historial de inventario es append-only. Al quedar
    // ANULADA, sus cantidades vuelven a estar disponibles para otra nota.
    @Transactional
    public NotaCreditoCompleta anular(Long id, String motivo) {
        Long tenantId = TenantContext.getTenantId();
        Long usuarioId = usuarioActualService.idUsuarioActual(tenantId);
        NotaCredito nc = buscarEntidad(id, tenantId);
        exigirEstado(nc, "anular", EstadoNotaCredito.EMITIDA);

        List<NotaCreditoMovimiento> recuperaciones = movimientoRepository
                .findByTenantIdAndNotaCreditoIdAndTipo(tenantId, nc.getId(), TipoMovimientoNotaCredito.RECUPERACION);

        Long headerId = recuperaciones.isEmpty() ? null : crearHeader(nc, usuarioId, TipoMovimiento.SALIDA,
                "Anulación de " + NumeroNotaCredito.formatear(nc.getFolio()));

        for (NotaCreditoMovimiento recuperacion : recuperaciones) {
            MovimientoInventario original = movimientoInventarioRepository
                    .findById(recuperacion.getMovimientoInventarioId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No se encontró el movimiento de inventario a revertir: "
                                    + recuperacion.getMovimientoInventarioId()));

            MovimientoInventario reversa = stockService.sumar(tenantId, original.getProductoId(),
                    original.getBodegaId(), original.getCantidad().negate(),
                    TipoMovimiento.SALIDA_ANULA_NOTA_CREDITO, headerId, nc.getId());

            movimientoRepository.save(NotaCreditoMovimiento.builder()
                    .tenantId(tenantId)
                    .notaCreditoId(nc.getId())
                    .notaCreditoDetalleId(recuperacion.getNotaCreditoDetalleId())
                    .movimientoInventarioId(reversa.getId())
                    .tipo(TipoMovimientoNotaCredito.REVERSA_ANULACION)
                    .build());
        }

        EstadoNotaCredito anterior = nc.getEstado();
        nc.setEstado(EstadoNotaCredito.ANULADA);
        nc.setFechaAnulacion(LocalDateTime.now());
        nc.setFechaActualizacion(LocalDateTime.now());
        nc = notaCreditoRepository.save(nc);

        registrarEvento(nc, AccionNotaCredito.ANULADA, anterior, EstadoNotaCredito.ANULADA, motivo, usuarioId);
        return detalleDe(nc);
    }

    // --- Lectura -------------------------------------------------------------

    @Transactional(readOnly = true)
    public NotaCreditoCompleta obtener(Long id) {
        return detalleDe(buscarEntidad(id, TenantContext.getTenantId()));
    }

    @Transactional(readOnly = true)
    public NotaCredito obtenerEntidad(Long id) {
        NotaCredito nc = buscarEntidad(id, TenantContext.getTenantId());
        nc.getDetalle().size(); // fuerza la carga: open-in-view está deshabilitado
        return nc;
    }

    // Ventas que pueden corregirse con una nota de crédito. Se limita a las más
    // recientes: el formulario es un buscador, no un listado completo.
    @Transactional(readOnly = true)
    public List<DocumentoAsociable> documentosAsociables(Long clienteId, String q) {
        Long tenantId = TenantContext.getTenantId();
        Integer folio = folioDeTexto(q);

        Specification<Venta> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("tenantId"), tenantId),
                cb.isTrue(root.get("activo")));
        if (clienteId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("clienteId"), clienteId));
        }
        if (folio != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("folio"), folio));
        }

        var pageable = PageRequest.of(0, MAX_DOCUMENTOS_ASOCIABLES, Sort.by(Sort.Direction.DESC, "fecha"));
        return ventaRepository.findAll(spec, pageable).getContent().stream()
                .map(v -> new DocumentoAsociable(
                        v.getId(), v.getTipoDocumento(), v.getFolio(), numeroDocumento(v),
                        v.getFecha() != null ? v.getFecha().toLocalDate() : null,
                        v.getMontoTotal(), v.isExento(), v.getBodegaId(),
                        notaCreditoRepository.existsByTenantIdAndVentaId(tenantId, v.getId())))
                .toList();
    }

    // Líneas del documento original con la cantidad todavía disponible para
    // recuperar. Es la fuente del detalle del formulario.
    @Transactional(readOnly = true)
    public List<LineaDocumentoOriginal> lineasDocumento(Long ventaId, Long excluyendoNotaCreditoId) {
        Long tenantId = TenantContext.getTenantId();
        Venta venta = buscarVenta(ventaId, tenantId);
        Map<Long, BigDecimal> recuperadas = cantidadesRecuperadas(tenantId, ventaId, excluyendoNotaCreditoId);
        Map<Long, Producto> productos = productosDe(venta.getDetalle().stream()
                .map(VentaDetalle::getProductoId).toList());

        return venta.getDetalle().stream()
                .map(d -> {
                    Producto p = productos.get(d.getProductoId());
                    BigDecimal recuperada = recuperadas.getOrDefault(d.getId(), BigDecimal.ZERO);
                    BigDecimal disponible = d.getCantidad().subtract(recuperada).max(BigDecimal.ZERO);
                    return new LineaDocumentoOriginal(
                            d.getId(), d.getProductoId(),
                            p != null ? p.getSku() : null,
                            p != null ? p.getNombre() : "Producto " + d.getProductoId(),
                            d.getCantidad(), recuperada, disponible,
                            d.getPrecioUnitario(), d.getDescuento(), d.getSubtotal());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public PaginaResponse<NotaCreditoResumen> buscar(EstadoNotaCredito estado, Long clienteId,
                                                     TipoCorreccion tipoCorreccion, Long ventaId,
                                                     TipoDocumentoVenta docAsociadoTipo,
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
        Specification<NotaCredito> spec = (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);
        if (estado != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("estado"), estado));
        }
        if (clienteId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("clienteId"), clienteId));
        }
        if (tipoCorreccion != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("tipoCorreccion"), tipoCorreccion));
        }
        if (ventaId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("ventaId"), ventaId));
        }
        if (docAsociadoTipo != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("docAsociadoTipo"), docAsociadoTipo));
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
        var resultado = notaCreditoRepository.findAll(spec, pageable);

        List<NotaCredito> notas = resultado.getContent();
        Map<Long, Cliente> clientes = clientesDe(tenantId, notas);
        List<NotaCreditoResumen> contenido = notas.stream()
                .map(n -> resumenDe(n, clientes.get(n.getClienteId())))
                .toList();
        return new PaginaResponse<>(contenido, resultado.getTotalElements());
    }

    @Transactional(readOnly = true)
    public DashboardNotasCredito dashboard(LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha 'desde' no puede ser posterior a 'hasta'");
        }
        Long tenantId = TenantContext.getTenantId();
        List<NotaCredito> notas = notaCreditoRepository
                .findByTenantIdAndFechaBetweenOrderByFolioAsc(tenantId, desde, hasta);

        Map<EstadoNotaCredito, List<NotaCredito>> porEstado = notas.stream()
                .collect(Collectors.groupingBy(NotaCredito::getEstado));
        Map<TipoCorreccion, List<NotaCredito>> porTipo = notas.stream()
                .collect(Collectors.groupingBy(NotaCredito::getTipoCorreccion));

        // Solo las emitidas son documentos con efecto: un borrador todavía no lo
        // es y una anulada dejó de serlo.
        List<NotaCredito> emitidas = porEstado.getOrDefault(EstadoNotaCredito.EMITIDA, List.of());
        BigDecimal montoEmitido = emitidas.stream()
                .map(NotaCredito::getMontoTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int conRecuperacion = (int) emitidas.stream()
                .filter(n -> n.getDetalle().stream().anyMatch(NotaCreditoDetalle::isRecuperaInventario))
                .count();
        int documentosCorregidos = (int) emitidas.stream().map(NotaCredito::getVentaId).distinct().count();

        List<ConteoPorEstado> conteosEstado = Arrays.stream(EstadoNotaCredito.values())
                .map(e -> new ConteoPorEstado(e,
                        porEstado.getOrDefault(e, List.of()).size(),
                        montoDe(porEstado.getOrDefault(e, List.of()))))
                .toList();
        List<ConteoPorTipo> conteosTipo = Arrays.stream(TipoCorreccion.values())
                .map(t -> new ConteoPorTipo(t,
                        porTipo.getOrDefault(t, List.of()).size(),
                        montoDe(porTipo.getOrDefault(t, List.of()))))
                .toList();

        return new DashboardNotasCredito(
                desde, hasta, notas.size(),
                porEstado.getOrDefault(EstadoNotaCredito.BORRADOR, List.of()).size(),
                emitidas.size(),
                porEstado.getOrDefault(EstadoNotaCredito.ANULADA, List.of()).size(),
                montoEmitido, conRecuperacion, documentosCorregidos,
                conteosEstado, conteosTipo);
    }

    // --- Reglas por tipo de corrección ---------------------------------------

    // El tipo de corrección determina el comportamiento: CORRIGE_DOCUMENTO
    // precarga el documento completo, CORRIGE_MONTO trabaja con las líneas que
    // indique el usuario, y CORRIGE_TEXTO no lleva líneas ni montos.
    private void aplicarCorreccion(NotaCredito nc, NotaCreditoRequest request, Venta venta,
                                   Long tenantId, Long notaCreditoId) {
        List<NotaCreditoRequest.Item> items = request.itemsOVacio();

        if (request.tipoCorreccion() == TipoCorreccion.CORRIGE_TEXTO) {
            if (!items.isEmpty()) {
                throw new IllegalArgumentException(
                        "Una nota de crédito que corrige texto no puede tener líneas de detalle");
            }
            if (request.textoCorreccion() == null || request.textoCorreccion().isBlank()) {
                throw new IllegalArgumentException("Debe indicar el texto de la corrección");
            }
            nc.setTextoCorreccion(request.textoCorreccion());
            aplicarMontosEnCero(nc);
            return;
        }

        nc.setTextoCorreccion(null);

        // CORRIGE_DOCUMENTO anula el documento completo: si no vienen líneas se
        // precargan todas las del original, con recuperación de inventario
        // activada por defecto.
        if (items.isEmpty() && request.tipoCorreccion() == TipoCorreccion.CORRIGE_DOCUMENTO) {
            items = venta.getDetalle().stream()
                    .map(d -> new NotaCreditoRequest.Item(d.getProductoId(), d.getId(), d.getCantidad(),
                            d.getPrecioUnitario(), d.getDescuento(), true))
                    .toList();
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Debe indicar al menos una línea a corregir");
        }

        aplicarLineasYMontos(nc, items, request.descuento(), venta, tenantId);
    }

    private void aplicarLineasYMontos(NotaCredito nc, List<NotaCreditoRequest.Item> items,
                                      BigDecimal descuentoGlobalRequest, Venta venta, Long tenantId) {
        Map<Long, VentaDetalle> lineasOriginales = venta.getDetalle().stream()
                .collect(Collectors.toMap(VentaDetalle::getId, d -> d));
        Map<Long, Producto> productos = productosDe(items.stream()
                .map(NotaCreditoRequest.Item::productoId).toList());

        List<NotaCreditoDetalle> lineas = new ArrayList<>();
        BigDecimal subtotalBrutoTotal = BigDecimal.ZERO;
        BigDecimal descuentoLineas = BigDecimal.ZERO;
        BigDecimal sumaDetalle = BigDecimal.ZERO;

        for (NotaCreditoRequest.Item item : items) {
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

            VentaDetalle original = null;
            if (item.ventaDetalleId() != null) {
                original = lineasOriginales.get(item.ventaDetalleId());
                if (original == null) {
                    throw new IllegalArgumentException(
                            "La línea de " + producto.getNombre() + " no pertenece al documento asociado");
                }
            } else if (item.recuperaInventario()) {
                throw new IllegalArgumentException(
                        "La línea \"" + producto.getNombre() + "\" no pertenece al documento original y no puede "
                                + "recuperar inventario");
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

            lineas.add(NotaCreditoDetalle.builder()
                    .notaCredito(nc)
                    .productoId(producto.getId())
                    .ventaDetalleId(original != null ? original.getId() : null)
                    .codigo(producto.getSku())
                    .descripcion(producto.getNombre())
                    .cantidad(item.cantidad())
                    .precioUnitario(item.precioUnitario())
                    .descuento(descuentoLinea)
                    .subtotal(subtotal)
                    .recuperaInventario(item.recuperaInventario())
                    .build());
        }

        BigDecimal descuentoGlobal = descuentoGlobalRequest != null ? descuentoGlobalRequest : BigDecimal.ZERO;
        if (descuentoGlobal.signum() < 0) {
            throw new IllegalArgumentException("El descuento global no puede ser negativo");
        }
        if (descuentoGlobal.compareTo(sumaDetalle) > 0) {
            throw new IllegalArgumentException("El descuento global no puede superar el total de las líneas");
        }

        nc.getDetalle().addAll(lineas);

        // Se calcula con el tipo de documento y la condición de exención de la
        // VENTA original, no con valores fijos: así una nota sobre una boleta
        // (montos brutos) cuadra con la boleta, y una sobre factura (montos
        // netos) con la factura.
        CalculadoraMontosVenta.Montos montos = CalculadoraMontosVenta.calcular(
                nc.getDocAsociadoTipo(), nc.isExenta(), sumaDetalle.subtract(descuentoGlobal));
        nc.setDescuento(descuentoGlobal);
        nc.setMontoSubtotal(subtotalBrutoTotal);
        nc.setMontoDescuento(descuentoLineas.add(descuentoGlobal));
        nc.setMontoNeto(montos.neto());
        nc.setMontoIva(montos.iva());
        nc.setMontoTotal(montos.total());
    }

    private void aplicarMontosEnCero(NotaCredito nc) {
        nc.setDescuento(BigDecimal.ZERO);
        nc.setMontoSubtotal(BigDecimal.ZERO);
        nc.setMontoDescuento(BigDecimal.ZERO);
        nc.setMontoNeto(BigDecimal.ZERO);
        nc.setMontoIva(BigDecimal.ZERO);
        nc.setMontoTotal(BigDecimal.ZERO);
    }

    // --- Inventario ----------------------------------------------------------

    // Impide recuperar dos veces la misma mercadería: lo disponible de cada línea
    // es lo vendido menos lo ya recuperado por notas de crédito EMITIDAS. Se
    // valida todo antes de escribir nada.
    private void validarCantidadesDisponibles(NotaCredito nc, List<NotaCreditoDetalle> aRecuperar, Long tenantId) {
        Venta venta = buscarVenta(nc.getVentaId(), tenantId);
        Map<Long, VentaDetalle> lineasOriginales = venta.getDetalle().stream()
                .collect(Collectors.toMap(VentaDetalle::getId, d -> d));
        Map<Long, BigDecimal> recuperadas = cantidadesRecuperadas(tenantId, nc.getVentaId(), nc.getId());

        // Varias líneas de la nota pueden apuntar a la misma línea del documento:
        // se acumulan para que el control no se pueda burlar dividiéndolas.
        Map<Long, BigDecimal> solicitadas = new LinkedHashMap<>();
        for (NotaCreditoDetalle linea : aRecuperar) {
            if (linea.getVentaDetalleId() == null) {
                throw new IllegalArgumentException(
                        "La línea \"" + linea.getDescripcion() + "\" no pertenece al documento original y no puede "
                                + "recuperar inventario");
            }
            solicitadas.merge(linea.getVentaDetalleId(), linea.getCantidad(), BigDecimal::add);
        }

        for (var entrada : solicitadas.entrySet()) {
            VentaDetalle original = lineasOriginales.get(entrada.getKey());
            if (original == null) {
                throw new IllegalArgumentException("Una de las líneas no pertenece al documento asociado");
            }
            BigDecimal yaRecuperado = recuperadas.getOrDefault(entrada.getKey(), BigDecimal.ZERO);
            BigDecimal disponible = original.getCantidad().subtract(yaRecuperado).max(BigDecimal.ZERO);
            if (entrada.getValue().compareTo(disponible) > 0) {
                String nombre = productoRepository.findById(original.getProductoId())
                        .map(Producto::getNombre)
                        .orElse("el producto");
                throw new IllegalArgumentException(
                        "No se puede recuperar " + entrada.getValue().stripTrailingZeros().toPlainString()
                                + " de \"" + nombre + "\": el documento asociado solo tiene "
                                + disponible.stripTrailingZeros().toPlainString() + " disponible(s) para recuperar");
            }
        }
    }

    private Map<Long, BigDecimal> cantidadesRecuperadas(Long tenantId, Long ventaId, Long excluyendoNotaCreditoId) {
        List<CantidadRecuperada> filas = excluyendoNotaCreditoId == null
                ? detalleRepository.cantidadesRecuperadas(tenantId, ventaId)
                : detalleRepository.cantidadesRecuperadasExcluyendo(tenantId, ventaId, excluyendoNotaCreditoId);
        Map<Long, BigDecimal> mapa = new HashMap<>();
        for (CantidadRecuperada fila : filas) {
            mapa.put(fila.ventaDetalleId(), fila.cantidad() != null ? fila.cantidad() : BigDecimal.ZERO);
        }
        return mapa;
    }

    // --- Helpers -------------------------------------------------------------

    // Cabecera que agrupa los movimientos de una emisión o de su anulación. El
    // módulo de Inventario la usa como "documento" del movimiento: de ahí salen
    // su pantalla de detalle y sus exportaciones.
    private Long crearHeader(NotaCredito nc, Long usuarioId, TipoMovimiento tipo, String observacion) {
        MovimientoInventarioHeader header = movimientoHeaderRepository.save(MovimientoInventarioHeader.builder()
                .tenantId(nc.getTenantId())
                .tipo(tipo)
                .bodegaDestinoId(tipo == TipoMovimiento.ENTRADA ? nc.getBodegaId() : null)
                .bodegaOrigenId(tipo == TipoMovimiento.SALIDA ? nc.getBodegaId() : null)
                .usuarioId(usuarioId)
                .observacion(observacion)
                .build());
        return header.getId();
    }

    private Venta buscarVenta(Long ventaId, Long tenantId) {
        // findByIdAndTenantIdAndActivoTrue trae el detalle con @EntityGraph, así que
        // las líneas están disponibles aunque open-in-view esté deshabilitado.
        return ventaRepository.findByIdAndTenantIdAndActivoTrue(ventaId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Documento asociado no encontrado: " + ventaId));
    }

    // La mercadería vuelve a la bodega desde la que salió. Si la venta no la
    // registró, se cae a la bodega principal, igual que VentaService y
    // CompraService.
    private Long bodegaDe(Venta venta, Long tenantId) {
        if (venta.getBodegaId() != null) {
            return venta.getBodegaId();
        }
        try {
            return stockService.bodegaPrincipal(tenantId).getId();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private NotaCredito buscarEntidad(Long id, Long tenantId) {
        return notaCreditoRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Nota de crédito no encontrada: " + id));
    }

    private void exigirEstado(NotaCredito nc, String accion, EstadoNotaCredito... permitidos) {
        for (EstadoNotaCredito permitido : permitidos) {
            if (nc.getEstado() == permitido) {
                return;
            }
        }
        throw new IllegalArgumentException(
                "No se puede " + accion + " una nota de crédito en estado " + nc.getEstado());
    }

    private void registrarEvento(NotaCredito nc, AccionNotaCredito accion, EstadoNotaCredito anterior,
                                 EstadoNotaCredito nuevo, String detalle, Long usuarioId) {
        eventoRepository.save(NotaCreditoEvento.builder()
                .tenantId(nc.getTenantId())
                .notaCreditoId(nc.getId())
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

    private Map<Long, Cliente> clientesDe(Long tenantId, List<NotaCredito> notas) {
        List<Long> ids = notas.stream().map(NotaCredito::getClienteId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return clienteRepository.findByTenantIdAndIdIn(tenantId, ids).stream()
                .collect(Collectors.toMap(Cliente::getId, c -> c, (a, b) -> a));
    }

    private BigDecimal montoDe(List<NotaCredito> notas) {
        return notas.stream().map(NotaCredito::getMontoTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // El texto puede ser el número de la nota (NC-000012, "12") o el nombre/RUT
    // del cliente. Los clientes se resuelven a ids en una consulta aparte para
    // que el filtro siga corriendo en la base y la paginación no tenga que traer
    // todo a memoria.
    private Specification<NotaCredito> filtroBusqueda(Long tenantId, String q) {
        List<Long> clienteIds = clienteRepository.idsPorBusqueda(tenantId, "%" + q.toLowerCase() + "%");
        Integer folio = folioDeTexto(q);

        return (root, query, cb) -> {
            List<Predicate> alternativas = new ArrayList<>();
            if (!clienteIds.isEmpty()) {
                alternativas.add(root.get("clienteId").in(clienteIds));
            }
            if (folio != null) {
                alternativas.add(cb.equal(root.get("folio"), folio));
                alternativas.add(cb.equal(root.get("docAsociadoFolio"), folio));
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

    private RecuperacionInventario recuperacionDe(NotaCredito nc) {
        List<NotaCreditoDetalle> lineas = nc.getDetalle();
        if (lineas.isEmpty()) {
            return RecuperacionInventario.NO;
        }
        long conRecuperacion = lineas.stream().filter(NotaCreditoDetalle::isRecuperaInventario).count();
        if (conRecuperacion == 0) {
            return RecuperacionInventario.NO;
        }
        return conRecuperacion == lineas.size() ? RecuperacionInventario.TOTAL : RecuperacionInventario.PARCIAL;
    }

    private String etiquetaTipo(TipoCorreccion tipo) {
        return switch (tipo) {
            case CORRIGE_DOCUMENTO -> "Corrige documento";
            case CORRIGE_MONTO -> "Corrige monto";
            case CORRIGE_TEXTO -> "Corrige texto";
        };
    }

    private String numeroDocumento(Venta venta) {
        return venta.getTipoDocumento() + " N.º " + venta.getFolio();
    }

    private String descripcionDocumento(Venta venta) {
        return numeroDocumento(venta);
    }

    // --- Armado de la respuesta ----------------------------------------------

    private NotaCreditoCompleta detalleDe(NotaCredito nc) {
        Long tenantId = nc.getTenantId();
        Cliente cliente = clienteRepository.findById(nc.getClienteId()).orElse(null);
        Usuario usuario = usuarioRepository.findById(nc.getUsuarioId()).orElse(null);
        String bodegaNombre = nc.getBodegaId() == null ? null
                : bodegaRepository.findById(nc.getBodegaId()).map(Bodega::getNombre).orElse(null);
        BigDecimal totalDocumento = ventaRepository.findByIdAndTenantIdAndActivoTrue(nc.getVentaId(), tenantId)
                .map(Venta::getMontoTotal)
                .orElse(null);

        List<LineaNotaCredito> lineas = nc.getDetalle().stream()
                .map(l -> new LineaNotaCredito(l.getId(), l.getProductoId(), l.getVentaDetalleId(),
                        l.getCodigo(), l.getDescripcion(), l.getCantidad(), l.getPrecioUnitario(),
                        l.getDescuento(), l.getSubtotal(), l.isRecuperaInventario()))
                .toList();

        List<NotaCreditoEvento> eventos = eventoRepository
                .findByTenantIdAndNotaCreditoIdOrderByFechaAscIdAsc(tenantId, nc.getId());
        Map<Long, String> nombresUsuario = nombresDeUsuarios(eventos);
        List<EventoNotaCredito> historial = eventos.stream()
                .map(e -> new EventoNotaCredito(e.getFecha(),
                        e.getUsuarioId() == null ? null : nombresUsuario.get(e.getUsuarioId()),
                        e.getAccion(), e.getEstadoAnterior(), e.getEstadoNuevo(), e.getDetalle()))
                .toList();

        return new NotaCreditoCompleta(
                nc.getId(), nc.getFolio(), NumeroNotaCredito.formatear(nc.getFolio()), nc.getEstado(),
                nc.getTipoCorreccion(), nc.getFecha(),
                nc.getClienteId(),
                cliente != null ? cliente.getNombre() : null,
                cliente != null ? cliente.getRazonSocial() : null,
                cliente != null ? cliente.getRut() : null,
                cliente != null ? cliente.getDireccion() : null,
                cliente != null ? cliente.getEmail() : null,
                cliente != null ? cliente.getTelefono() : null,
                nc.getUsuarioId(), usuario != null ? usuario.getNombre() : null,
                new DocumentoAsociado(nc.getVentaId(), nc.getDocAsociadoTipo(), nc.getDocAsociadoFolio(),
                        nc.getDocAsociadoTipo() + " N.º " + nc.getDocAsociadoFolio(),
                        nc.getDocAsociadoFecha(), nc.getDocAsociadoRazon(), totalDocumento),
                nc.getMotivo(), nc.getObservaciones(), nc.getTextoCorreccion(),
                nc.getBodegaId(), bodegaNombre, nc.isExenta(), nc.getMoneda(),
                nc.getDescuento(), nc.getMontoSubtotal(), nc.getMontoDescuento(),
                nc.getMontoNeto(), nc.getMontoIva(), nc.getMontoTotal(),
                nc.getFechaEmision(), nc.getFechaAnulacion(),
                recuperacionDe(nc), lineas, movimientosDe(nc), historial);
    }

    // Movimientos de inventario generados por la nota: la respuesta a "consultar
    // movimientos de inventario relacionados". Productos y bodegas se resuelven
    // en lote para no caer en N+1.
    private List<MovimientoRelacionado> movimientosDe(NotaCredito nc) {
        List<NotaCreditoMovimiento> vinculos = movimientoRepository
                .findByTenantIdAndNotaCreditoIdOrderByFechaAscIdAsc(nc.getTenantId(), nc.getId());
        if (vinculos.isEmpty()) {
            return List.of();
        }

        Map<Long, MovimientoInventario> movimientos = movimientoInventarioRepository
                .findAllById(vinculos.stream().map(NotaCreditoMovimiento::getMovimientoInventarioId).toList())
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

    private Map<Long, String> nombresDeUsuarios(List<NotaCreditoEvento> eventos) {
        List<Long> ids = eventos.stream()
                .map(NotaCreditoEvento::getUsuarioId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return usuarioRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Usuario::getId, Usuario::getNombre, (a, b) -> a));
    }

    private NotaCreditoResumen resumenDe(NotaCredito nc, Cliente cliente) {
        return new NotaCreditoResumen(
                nc.getId(), nc.getFolio(), NumeroNotaCredito.formatear(nc.getFolio()), nc.getEstado(),
                nc.getTipoCorreccion(), nc.getFecha(),
                nc.getClienteId(),
                cliente != null ? cliente.getNombre() : null,
                cliente != null ? cliente.getRut() : null,
                nc.getDocAsociadoTipo(), nc.getDocAsociadoFolio(),
                nc.getMotivo(), nc.getMontoTotal(), recuperacionDe(nc));
    }
}
